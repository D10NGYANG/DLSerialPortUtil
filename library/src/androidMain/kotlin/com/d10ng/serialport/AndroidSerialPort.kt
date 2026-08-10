package com.d10ng.serialport

import android.serialport.SerialPort
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
import java.io.File

/**
 * Android串口
 * @Author d10ng
 * @Date 2025/9/17 13:53
 */
class AndroidSerialPort(
    info: SerialPortInfo,
    config: SerialPortConfig
): BaseSerialPort(info, config) {

    companion object {
        private val SAFE_DEVICE_PATH = Regex("^/dev/[A-Za-z0-9._/-]+$")
        private const val READ_RETRY_MILLIS = 50L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lifecycleMutex = Mutex()
    private val writeMutex = Mutex()
    private val stateLock = Any()

    private var sp: SerialPort? = null
    private var readJob: Job? = null
    private var generation = 0L

    override val isDtrSupported: Boolean = false
    override val isRtsSupported: Boolean = false

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

                val file = resolveDeviceFile(info.id)
                if (!file.canRead() || !file.canWrite()) {
                    logger.w { "[serial.open.fail] ${info.id} reason=permission-denied" }
                    throw SecurityException(
                        "Serial port [${file.absolutePath}] is not readable and writable; " +
                            "grant device-file access in the application or system configuration"
                    )
                }

                var openedPort: SerialPort? = null
                runCatching {
                    logger.i { "[serial.open] ${info.id} ${serialConfigFields(config)}" }
                    val port = SerialPort(
                        file,
                        config.baudRate.intValue,
                        config.dataBits.intValue,
                        config.parity.intValue,
                        config.stopBits.intValue
                    ).also { openedPort = it }
                    val installed = synchronized(stateLock) {
                        if (generation == token && sp == null) {
                            sp = port
                            true
                        } else {
                            false
                        }
                    }
                    check(installed) { "Serial port [${info.id}] was closed while opening" }
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
                    runCatching { openedPort?.tryClose() }
                    openStateFlow.value = false
                    throw exception
                }
            }
        }
    }

    private fun resolveDeviceFile(path: String): File {
        val file = File(path).canonicalFile
        require(SAFE_DEVICE_PATH.matches(file.absolutePath)) {
            "Serial port path must resolve to a device under /dev"
        }
        require(file.exists()) { "Serial port [${file.absolutePath}] does not exist" }
        return file
    }

    private fun startRead(port: SerialPort, token: Long) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val buffer = ByteArray(2048)
            var disconnectReason: String? = null
            logger.i { "[serial.read.start] ${info.id}" }
            try {
                loop@ while (isActive && isCurrent(port, token)) {
                    val size = try {
                        port.inputStream.read(buffer)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Throwable) {
                        if (exception.hasDefinitiveDisconnectEvidence()) {
                            disconnectReason = exception.serialContext()
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
                        disconnectReason = "stream-eof"
                        break@loop
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
                logger.d { "[serial.write.start] ${info.id} op=$operationId" }
                try {
                    logger.d { serialPayloadLog("tx", info.id, data, operationId) }
                    if (data.isNotEmpty()) {
                        port.outputStream.let { output ->
                            output.write(data)
                            output.flush()
                        }
                    }
                    logger.d { "[serial.write.ok] ${info.id} ${data.size}B op=$operationId" }
                    true
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

    override suspend fun setDtr(enabled: Boolean): Boolean {
        logger.w { "DTR is not supported by Android built-in serial port [${info.id}]" }
        return false
    }

    override suspend fun setRts(enabled: Boolean): Boolean {
        logger.w { "RTS is not supported by Android built-in serial port [${info.id}]" }
        return false
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
            runCatching { port?.tryClose() }
                .onFailure { logger.w { "[serial.cleanup.fail] ${info.id} error=${it.serialContext()}" } }
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
            runCatching { port.tryClose() }
                .onFailure { logger.w { "[serial.cleanup.fail] ${info.id} error=${it.serialContext()}" } }
        }
    }
}
