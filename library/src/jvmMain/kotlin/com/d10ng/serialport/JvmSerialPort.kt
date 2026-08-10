package com.d10ng.serialport

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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

    companion object {
        private const val WRITE_TIMEOUT_MILLIS = 2_000
        private const val READ_RETRY_MILLIS = 50L
    }

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
                    logger.i { "[serial.open] ${info.id} ${serialConfigFields(config)}" }
                    val candidate = info.obj as? SerialPort
                        ?: throw IllegalArgumentException(
                            "Serial port [${info.id}] does not contain a jSerialComm handle"
                        )
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
                    )) { "Serial port [${info.id}] rejected communication parameters" }
                    check(candidate.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)) {
                        "Serial port [${info.id}] rejected flow=none"
                    }
                    check(candidate.setComPortTimeouts(
                        SerialPort.TIMEOUT_READ_SEMI_BLOCKING or SerialPort.TIMEOUT_WRITE_BLOCKING,
                        0,
                        WRITE_TIMEOUT_MILLIS
                    )) { "Serial port [${info.id}] rejected read/write timeouts" }
                    check(candidate.openPort()) {
                        "Serial port [${info.id}] open failed code=${candidate.lastErrorCode} location=${candidate.lastErrorLocation}"
                    }
                    val installed = synchronized(stateLock) {
                        if (generation == token && sp == null) {
                            sp = candidate
                            true
                        } else {
                            false
                        }
                    }
                    check(installed) { "Serial port [${info.id}] was closed while opening" }
                    val port = candidate
                    startRead(port, token)
                    logger.i { "[serial.open.ok] ${info.id}" }
                }.onFailure { exception ->
                    logger.w { "[serial.open.fail] ${info.id} error=${exception.serialContext()}" }
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
            var disconnectReason: String? = null
            logger.i { "[serial.read.start] ${info.id}" }
            try {
                loop@ while (isActive && isCurrent(port, token)) {
                    val size = try {
                        input.read(buffer)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Throwable) {
                        if (!port.isOpen || exception.hasDefinitiveDisconnectEvidence()) {
                            disconnectReason = if (!port.isOpen) "jserialcomm-isOpen=false" else exception.serialContext()
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
                    } else if (size == -1) {
                        if (!port.isOpen) {
                            disconnectReason = "jserialcomm-isOpen=false"
                            break@loop
                        }
                        logger.w { "[serial.read.fail] ${info.id} action=retry reason=stream-eof-with-open-port" }
                        delay(READ_RETRY_MILLIS)
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
                logger.d { "[serial.write.start] ${info.id} timeout=${WRITE_TIMEOUT_MILLIS}ms op=$operationId" }
                try {
                    logger.d { serialPayloadLog("tx", info.id, data, operationId) }
                    val written = if (data.isEmpty()) 0 else port.writeBytes(data, data.size, 0)
                    if (written == data.size) {
                        logger.d { "[serial.write.ok] ${info.id} ${data.size}B op=$operationId" }
                        true
                    } else {
                        val type = if (written == 0) "timeout" else "fail"
                        logger.w {
                            "[serial.write.$type] ${info.id} written=$written expected=${data.size} " +
                                "code=${port.lastErrorCode} location=${port.lastErrorLocation} op=$operationId"
                        }
                        false
                    }
                } catch (exception: CancellationException) {
                    logger.w { "[serial.write.cancel] ${info.id} op=$operationId" }
                    throw exception
                } catch (exception: Throwable) {
                    logger.w {
                        "[serial.write.fail] ${info.id} op=$operationId error=${exception.serialContext()}"
                    }
                    false
                }
            }
        }
    }

    override suspend fun setDtr(enabled: Boolean): Boolean = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val port = synchronized(stateLock) { sp } ?: return@withContext false
            runCatching {
                if (enabled) port.setDTR() else port.clearDTR()
            }.onFailure { exception ->
                logger.w { "set DTR to $enabled fail: ${exception.message}" }
            }.getOrDefault(false)
        }
    }

    override suspend fun setRts(enabled: Boolean): Boolean = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val port = synchronized(stateLock) { sp } ?: return@withContext false
            runCatching {
                if (enabled) port.setRTS() else port.clearRTS()
            }.onFailure { exception ->
                logger.w { "set RTS to $enabled fail: ${exception.message}" }
            }.getOrDefault(false)
        }
    }

    override fun close() {
        logger.i { "[serial.close] ${info.id} reason=caller" }
        synchronized(stateLock) {
            generation += 1
            val job = readJob
            val port = sp
            readJob = null
            sp = null
            openStateFlow.value = false
            job?.cancel()
            runCatching { port?.closePort() }
            logger.i { "[serial.cleanup] ${info.id} state=closed" }
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

    private fun closeIfCurrent(port: SerialPort, token: Long, reason: String) {
        synchronized(stateLock) {
            if (sp !== port || generation != token) return
            logger.w { "[serial.disconnect] ${info.id} evidence=$reason" }
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
