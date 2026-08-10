package com.d10ng.serialport

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * JVM平台下的串口管理
 * @Author d10ng
 * @Date 2025/9/19 17:44
 */
class JvmSerialPort(
    info: SerialPortInfo,
    config: SerialPortConfig
): BaseSerialPort(info, config) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lifecycleMutex = Mutex()
    private val writeMutex = Mutex()
    private val stateLock = Any()

    private var sp: SerialPort? = null
    private var readJob: Job? = null
    private var generation = 0L

    override val isDtrSupported: Boolean = true
    override val isRtsSupported: Boolean = true

    override suspend fun open() {
        lifecycleMutex.withLock {
            withContext(Dispatchers.IO) {
                val token = synchronized(stateLock) {
                    if (sp != null) return@synchronized null
                    generation += 1
                    generation
                }
                if (token == null) {
                    logger.w { "Serial port [${info.id}] already opened" }
                    return@withContext
                }

                var openedPort: SerialPort? = null
                runCatching {
                    logger.d { "open serial port [${info.id}], config: $config" }
                    val port = synchronized(stateLock) {
                        check(generation == token && sp == null) {
                            "Serial port was closed while opening"
                        }
                        val candidate = info.obj as SerialPort
                        openedPort = candidate
                        val stopBits = when (config.stopBits) {
                            StopBits.V1 -> SerialPort.ONE_STOP_BIT
                            StopBits.V2 -> SerialPort.TWO_STOP_BITS
                        }
                        check(candidate.setComPortParameters(
                            config.baudRate.intValue,
                            config.dataBits.intValue,
                            stopBits,
                            config.parity.intValue
                        )) { "Failed to set serial port parameters" }
                        check(candidate.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)) {
                            "Failed to disable serial port flow control"
                        }
                        check(candidate.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0)) {
                            "Failed to set serial port timeouts"
                        }
                        check(candidate.openPort()) { "connect fail: open port fail" }
                        candidate.also { sp = it }
                    }
                    startRead(port, token)
                }.onFailure { exception ->
                    logger.w { "open fail: ${exception.message}" }
                    synchronized(stateLock) {
                        if (generation == token) generation += 1
                        if (sp === openedPort) {
                            sp = null
                            readJob = null
                        }
                    }
                    runCatching { openedPort?.closePort() }
                    openStateFlow.value = false
                    throw exception
                }
            }
        }
    }

    private fun startRead(port: SerialPort, token: Long) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val buffer = ByteArray(2048)
            val input = port.inputStream
            try {
                loop@ while (isActive && isCurrent(port, token)) {
                    val size = runCatching { input.read(buffer) }
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
                    port.outputStream.let { output ->
                        output.write(data)
                        output.flush()
                    }
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
            runCatching {
                if (enabled) port.setDTR() else port.clearDTR()
            }.onFailure { exception ->
                logger.w { "set DTR to $enabled fail: ${exception.message}" }
            }.getOrDefault(false)
        }
    }

    override suspend fun setRts(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        synchronized(stateLock) {
            val port = sp ?: return@synchronized false
            runCatching {
                if (enabled) port.setRTS() else port.clearRTS()
            }.onFailure { exception ->
                logger.w { "set RTS to $enabled fail: ${exception.message}" }
            }.getOrDefault(false)
        }
    }

    override fun close() {
        logger.d { "close serial port [${info.id}]" }
        synchronized(stateLock) {
            generation += 1
            val job = readJob
            val port = sp
            readJob = null
            sp = null
            openStateFlow.value = false
            job?.cancel()
            runCatching { port?.closePort() }
        }
    }

    override suspend fun closeAndAwait() {
        lifecycleMutex.withLock {
            val job = synchronized(stateLock) { readJob }
            close()
            job?.join()
        }
    }

    private fun isCurrent(port: SerialPort, token: Long): Boolean =
        synchronized(stateLock) { sp === port && generation == token }

    private fun closeIfCurrent(port: SerialPort, token: Long) {
        synchronized(stateLock) {
            if (sp !== port || generation != token) return
            generation += 1
            val job = readJob
            readJob = null
            sp = null
            openStateFlow.value = false
            job?.cancel()
            runCatching { port.closePort() }
        }
    }
}
