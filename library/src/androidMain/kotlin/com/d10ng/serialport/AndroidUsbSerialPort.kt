package com.d10ng.serialport

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.hardware.usb.UsbDeviceConnection
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
        private const val PERMISSION_WAIT_MILLIS = 30_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lifecycleMutex = Mutex()
    private val writeMutex = Mutex()
    private val stateLock = Any()

    private var sp: UsbSerialPort? = null
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

    @SuppressLint("ObsoleteSdkInt")
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
            logger.d { "open serial port [${info.id}], config: $config" }
            ensurePermission(driver)

            withContext(Dispatchers.IO) {
                var connection: UsbDeviceConnection? = null
                var openedPort: UsbSerialPort? = null
                runCatching {
                    val port = synchronized(stateLock) {
                        check(generation == token && sp == null) {
                            "Serial port was closed while opening"
                        }
                        val deviceConnection = usbManager.openDevice(driver.device)
                            ?: throw Exception("connect fail: open device fail")
                        connection = deviceConnection
                        val candidate = handle.selectedPort()
                        openedPort = candidate
                        candidate.open(deviceConnection)
                        candidate.setParameters(
                            config.baudRate.intValue,
                            config.dataBits.intValue,
                            config.stopBits.intValue,
                            config.parity.intValue
                        )
                        candidate.also { sp = it }
                    }
                    startDetachListener(deviceName, port, token)
                    startRead(port, token)
                }.onFailure { exception ->
                    logger.w { "open fail: ${exception.message}" }
                    synchronized(stateLock) {
                        if (generation == token) generation += 1
                        if (sp === openedPort) {
                            sp = null
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

    @SuppressLint("ObsoleteSdkInt")
    private suspend fun ensurePermission(driver: UsbSerialDriver) {
        val device = driver.device
        if (usbManager.hasPermission(device)) return

        coroutineScope {
            val result = async(start = CoroutineStart.UNDISPATCHED) {
                StartupInitializer.usbPermissionResultFlow
                    .filter { it.first == device.deviceName }
                    .first()
                    .second
            }
            try {
                withContext(Dispatchers.Main) {
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_MUTABLE else 0
                    val intent = Intent(ACTION_USB_PERMISSION).setPackage(ctx.packageName)
                    usbManager.requestPermission(
                        device,
                        PendingIntent.getBroadcast(ctx, device.deviceId, intent, flags)
                    )
                }
                val granted = try {
                    withTimeout(PERMISSION_WAIT_MILLIS) { result.await() }
                } catch (_: TimeoutCancellationException) {
                    throw Exception("connect fail: permission request timed out")
                }
                if (!granted) throw Exception("connect fail: permission denied")
            } finally {
                result.cancel()
            }
        }
    }

    private fun startDetachListener(deviceName: String, port: UsbSerialPort, token: Long) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            StartupInitializer.usbDeviceDetachedFlow.first { it == deviceName }
            closeIfCurrent(port, token)
        }
        synchronized(stateLock) {
            if (sp === port && generation == token) detachJob = job else job.cancel()
        }
        job.start()
    }

    private fun startRead(port: UsbSerialPort, token: Long) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val buffer = ByteArray(2048)
            try {
                loop@ while (isActive && isCurrent(port, token)) {
                    val size = runCatching { port.read(buffer, buffer.size, READ_WAIT_MILLIS) }
                        .onFailure { exception ->
                            logger.w { "read fail: ${exception.message}" }
                        }
                        .getOrElse { break@loop }
                    if (size > 0) {
                        val data = buffer.copyOfRange(0, size)
                        logger.d { "RX HEX: ${data.toHexString(HexFormat.UpperCase)}" }
                        logger.d { "RX STR: ${data.decodeToString()}" }
                        outputDataFlow.emit(data)
                    } else if (size == -1) {
                        break@loop
                    }
                }
            } finally {
                closeIfCurrent(port, token)
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

    override suspend fun write(data: ByteArray): Boolean = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            synchronized(stateLock) {
                val port = sp ?: return@synchronized false
                runCatching {
                    logger.d { "TX HEX: ${data.toHexString(HexFormat.UpperCase)}" }
                    logger.d { "TX STR: ${data.decodeToString()}" }
                    port.write(data, WRITE_WAIT_MILLIS)
                    true
                }.onFailure { exception ->
                    logger.w { "write fail: ${exception.message}"}
                }.getOrDefault(false)
            }
        }
    }

    override suspend fun setDtr(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        synchronized(stateLock) {
            val port = sp ?: return@synchronized false
            if (UsbSerialPort.ControlLine.DTR !in port.supportedControlLines) return@synchronized false
            runCatching {
                port.setDTR(enabled)
                true
            }.onFailure { exception ->
                logger.w { "set DTR to $enabled fail: ${exception.message}" }
            }.getOrDefault(false)
        }
    }

    override suspend fun setRts(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        synchronized(stateLock) {
            val port = sp ?: return@synchronized false
            if (UsbSerialPort.ControlLine.RTS !in port.supportedControlLines) return@synchronized false
            runCatching {
                port.setRTS(enabled)
                true
            }.onFailure { exception ->
                logger.w { "set RTS to $enabled fail: ${exception.message}" }
            }.getOrDefault(false)
        }
    }

    override fun close() {
        logger.d { "close serial port [${info.id}]" }
        synchronized(stateLock) {
            generation += 1
            val read = readJob
            val detach = detachJob
            val port = sp
            readJob = null
            detachJob = null
            sp = null
            openStateFlow.value = false
            read?.cancel()
            detach?.cancel()
            runCatching { port?.close() }
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

    private fun closeIfCurrent(port: UsbSerialPort, token: Long) {
        synchronized(stateLock) {
            if (sp !== port || generation != token) return
            generation += 1
            val read = readJob
            val detach = detachJob
            readJob = null
            detachJob = null
            sp = null
            openStateFlow.value = false
            read?.cancel()
            detach?.cancel()
            runCatching { port.close() }
        }
    }
}
