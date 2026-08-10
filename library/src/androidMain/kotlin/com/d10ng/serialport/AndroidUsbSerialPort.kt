package com.d10ng.serialport

import android.hardware.usb.UsbDeviceConnection
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Android USB串口
 * @Author d10ng
 * @Date 2025/9/18 17:19
 */
class AndroidUsbSerialPort(
    info: SerialPortInfo,
    config: SerialPortConfig
): BaseSerialPort(info, config) {

    companion object {
        private const val WRITE_WAIT_MILLIS = 2000
        private const val READ_WAIT_MILLIS = 2000
        private const val READ_RETRY_MILLIS = 50L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lifecycleMutex = Mutex()
    private val writeMutex = Mutex()
    private val stateLock = Any()

    private var sp: UsbSerialPort? = null
    private var deviceConnection: UsbDeviceConnection? = null
    private var readJob: Job? = null
    private var detachJob: Job? = null
    private var generation = 0L

    override val isDtrSupported: Boolean
        get() = runCatching {
            val port = synchronized(stateLock) { sp } ?: resolveHandleOrNull()?.selectedPort()
            port != null && UsbSerialPort.ControlLine.DTR in port.supportedControlLines
        }.getOrDefault(false)

    override val isRtsSupported: Boolean
        get() = runCatching {
            val port = synchronized(stateLock) { sp } ?: resolveHandleOrNull()?.selectedPort()
            port != null && UsbSerialPort.ControlLine.RTS in port.supportedControlLines
        }.getOrDefault(false)

    override suspend fun open() {
        lifecycleMutex.withLock {
            val token = synchronized(stateLock) {
                if (sp != null) return@synchronized null
                generation += 1
                generation
            }
            if (token == null) {
                logger.w { "Serial port [${info.id}] already opened" }
                return@withLock
            }

            val handle = resolveHandleOrNull()
                ?: throw IllegalArgumentException("Serial port [${info.id}] does not contain a USB driver")
            val driver = handle.driver
            val deviceName = driver.device.deviceName
            val vendorId = driver.device.vendorId.toString(16).uppercase()
            val productId = driver.device.productId.toString(16).uppercase()
            logger.i {
                "[serial.open] ${info.id} ${serialConfigFields(config)} vid=$vendorId pid=$productId"
            }
            ensurePermission(driver)

            withContext(Dispatchers.IO) {
                var connection: UsbDeviceConnection? = null
                var openedPort: UsbSerialPort? = null
                runCatching {
                    val openedConnection = usbManager.openDevice(driver.device)
                        ?: throw IllegalStateException(
                            "Serial port [${info.id}] could not open USB device [$deviceName]"
                        )
                    connection = openedConnection
                    val port = handle.selectedPort().also { openedPort = it }
                    port.open(openedConnection)
                    port.setParameters(
                        config.baudRate.intValue,
                        config.dataBits.intValue,
                        config.stopBits.intValue,
                        config.parity.intValue
                    )
                    val installed = synchronized(stateLock) {
                        if (generation == token && sp == null) {
                            sp = port
                            deviceConnection = openedConnection
                            true
                        } else {
                            false
                        }
                    }
                    check(installed) { "Serial port [${info.id}] was closed while opening" }
                    startDetachListener(deviceName, port, token)
                    startRead(deviceName, port, token)
                    logger.i { "[serial.open.ok] ${info.id}" }
                }.onFailure { exception ->
                    logger.w { "[serial.open.fail] ${info.id} error=${exception.serialContext()}" }
                    synchronized(stateLock) {
                        if (generation == token) generation += 1
                        if (sp === openedPort) {
                            sp = null
                            deviceConnection = null
                            readJob = null
                            detachJob = null
                        }
                    }
                    runCatching { openedPort?.close() }
                    runCatching { connection?.close() }
                    openStateFlow.value = false
                    throw exception
                }
            }
        }
    }

    private suspend fun ensurePermission(driver: UsbSerialDriver) {
        val device = driver.device
        if (usbManager.hasPermission(device)) return
        val vendorId = device.vendorId.toString(16).uppercase()
        val productId = device.productId.toString(16).uppercase()
        logger.w {
            "[serial.permission.missing] ${info.id} device=${device.deviceName} vid=$vendorId pid=$productId"
        }
        throw SecurityException(
            "USB permission is missing for serial port [${info.id}] device [${device.deviceName}] " +
                "VID:$vendorId PID:$productId; the application must request UsbManager permission before open()"
        )
    }

    private fun startDetachListener(deviceName: String, port: UsbSerialPort, token: Long) {
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (usbManager.deviceList.values.none { it.deviceName == deviceName }) {
                closeIfCurrent(port, token, "usb-device-missing:$deviceName")
                return@launch
            }
            StartupInitializer.usbDeviceDetachedFlow.first { it == deviceName }
            closeIfCurrent(port, token, "usb-detached:$deviceName")
        }
        synchronized(stateLock) {
            if (sp === port && generation == token) detachJob = job else job.cancel()
        }
    }

    private fun startRead(deviceName: String, port: UsbSerialPort, token: Long) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val buffer = ByteArray(2048)
            var disconnectReason: String? = null
            logger.i { "[serial.read.start] ${info.id}" }
            try {
                loop@ while (isActive && isCurrent(port, token)) {
                    val size = try {
                        port.read(buffer, buffer.size, READ_WAIT_MILLIS)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Throwable) {
                        val detached = usbManager.deviceList.values.none { it.deviceName == deviceName }
                        if (detached || exception.hasDefinitiveDisconnectEvidence()) {
                            disconnectReason = if (detached) "usb-device-missing:$deviceName" else exception.serialContext()
                            break@loop
                        }
                        logger.w {
                            "[serial.read.fail] ${info.id} action=retry error=${exception.serialContext()}"
                        }
                        delay(READ_RETRY_MILLIS)
                        continue@loop
                    }
                    if (size > 0) {
                        val data = buffer.copyOfRange(0, size)
                        emitReceived(data)
                    }
                }
            } finally {
                logger.i {
                    "[serial.read.stop] ${info.id} reason=${disconnectReason ?: "cancelled-or-replaced"}"
                }
                disconnectReason?.let { closeIfCurrent(port, token, it) }
            }
        }
        var shouldStart = false
        synchronized(stateLock) {
            if (sp === port && generation == token) {
                readJob = job
                openStateFlow.value = true
                shouldStart = true
            } else {
                job.cancel()
            }
        }
        if (shouldStart) job.start()
    }

    override suspend fun write(data: ByteArray): Boolean = withWriteOperation(data.size) { operationId ->
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val port = synchronized(stateLock) { sp }
                if (port == null) {
                    logger.w { "[serial.write.fail] ${info.id} op=$operationId reason=not-open" }
                    return@withContext false
                }
                logger.d { "[serial.write.start] ${info.id} timeout=${WRITE_WAIT_MILLIS}ms op=$operationId" }
                try {
                    logger.d { serialPayloadLog("tx", info.id, data, operationId) }
                    if (data.isNotEmpty()) {
                        port.write(data, WRITE_WAIT_MILLIS)
                    }
                    logger.d { "[serial.write.ok] ${info.id} ${data.size}B op=$operationId" }
                    true
                } catch (exception: CancellationException) {
                    logger.w { "[serial.write.cancel] ${info.id} op=$operationId" }
                    throw exception
                } catch (exception: Throwable) {
                    val type = if (exception::class.simpleName?.contains("Timeout", ignoreCase = true) == true) {
                        "timeout"
                    } else {
                        "fail"
                    }
                    logger.w {
                        "[serial.write.$type] ${info.id} op=$operationId error=${exception.serialContext()}"
                    }
                    false
                }
            }
        }
    }

    override suspend fun setDtr(enabled: Boolean): Boolean = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val port = synchronized(stateLock) { sp } ?: return@withContext false
            if (UsbSerialPort.ControlLine.DTR !in port.supportedControlLines) return@withContext false
            runCatching {
                port.setDTR(enabled)
                true
            }.onFailure { exception ->
                logger.w { "set DTR to $enabled fail: ${exception.message}" }
            }.getOrDefault(false)
        }
    }

    override suspend fun setRts(enabled: Boolean): Boolean = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val port = synchronized(stateLock) { sp } ?: return@withContext false
            if (UsbSerialPort.ControlLine.RTS !in port.supportedControlLines) return@withContext false
            runCatching {
                port.setRTS(enabled)
                true
            }.onFailure { exception ->
                logger.w { "set RTS to $enabled fail: ${exception.message}" }
            }.getOrDefault(false)
        }
    }

    override fun close() {
        logger.i { "[serial.close] ${info.id} reason=caller" }
        synchronized(stateLock) {
            generation += 1
            val read = readJob
            val detach = detachJob
            val port = sp
            val connection = deviceConnection
            readJob = null
            detachJob = null
            sp = null
            deviceConnection = null
            openStateFlow.value = false
            read?.cancel()
            detach?.cancel()
            runCatching { port?.close() }
            runCatching { connection?.close() }
            logger.i { "[serial.cleanup] ${info.id} state=closed" }
        }
    }

    override suspend fun closeAndAwait() {
        lifecycleMutex.withLock {
            val jobs = synchronized(stateLock) { readJob to detachJob }
            close()
            jobs.first?.join()
            jobs.second?.join()
        }
    }

    private fun resolveHandleOrNull(): AndroidUsbSerialPortHandle? = when (val obj = info.obj) {
        is AndroidUsbSerialPortHandle -> obj
        is UsbSerialDriver -> AndroidUsbSerialPortHandle(obj, 0)
        else -> null
    }

    private fun AndroidUsbSerialPortHandle.selectedPort(): UsbSerialPort =
        driver.ports.getOrNull(portIndex)
            ?: throw IllegalArgumentException("USB serial port index $portIndex is unavailable")

    private fun isCurrent(port: UsbSerialPort, token: Long): Boolean =
        synchronized(stateLock) { sp === port && generation == token }

    private fun closeIfCurrent(port: UsbSerialPort, token: Long, reason: String) {
        synchronized(stateLock) {
            if (sp !== port || generation != token) return
            logger.w { "[serial.disconnect] ${info.id} evidence=$reason" }
            generation += 1
            val read = readJob
            val detach = detachJob
            val connection = deviceConnection
            readJob = null
            detachJob = null
            sp = null
            deviceConnection = null
            openStateFlow.value = false
            read?.cancel()
            detach?.cancel()
            runCatching { port.close() }
            runCatching { connection?.close() }
        }
    }
}
