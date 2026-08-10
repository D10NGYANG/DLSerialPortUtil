package com.d10ng.serialport

import android.serialport.SerialPort
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
import java.io.File
import java.util.concurrent.TimeUnit

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
        private const val ROOT_COMMAND_TIMEOUT_SECONDS = 5L
        private val SAFE_DEVICE_PATH = Regex("^/dev/[A-Za-z0-9._/-]+$")
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
                if ((!file.canRead() || !file.canWrite()) && !chmodDevice(file)) {
                    throw SecurityException("Serial port [${file.absolutePath}] is not readable and writable")
                }

                var openedPort: SerialPort? = null
                runCatching {
                    logger.d { "open serial port [${info.id}], config: $config" }
                    val port = synchronized(stateLock) {
                        check(generation == token && sp == null) {
                            "Serial port was closed while opening"
                        }
                        SerialPort(
                            file,
                            config.baudRate.intValue,
                            config.dataBits.intValue,
                            config.parity.intValue,
                            config.stopBits.intValue
                        ).also {
                            openedPort = it
                            sp = it
                        }
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
                    runCatching { openedPort?.tryClose() }
                    openStateFlow.value = false
                    throw exception
                }
            }
        }
    }

    /**
     * 修改设备权限。设备路径必须解析到 /dev 下，避免把外部输入注入 root shell。
     */
    private fun resolveDeviceFile(path: String): File {
        val file = File(path).canonicalFile
        require(SAFE_DEVICE_PATH.matches(file.absolutePath)) {
            "Serial port path must resolve to a device under /dev"
        }
        require(file.exists()) { "Serial port [${file.absolutePath}] does not exist" }
        return file
    }

    private fun chmodDevice(device: File): Boolean {
        if (device.canRead() && device.canWrite()) return true

        return runCatching {
            val safePath = device.absolutePath

            val process = ProcessBuilder(
                "/system/bin/su",
                "-c",
                "chmod 666 $safePath"
            ).start()
            try {
                val completed = process.waitFor(ROOT_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (!completed) process.destroy()
                completed && process.exitValue() == 0 && device.canRead() && device.canWrite()
            } finally {
                runCatching { process.inputStream.close() }
                runCatching { process.errorStream.close() }
                runCatching { process.outputStream.close() }
            }
        }.onFailure { exception ->
            logger.w { "chmod 666 ${device.absolutePath} fail: ${exception.message}" }
        }.getOrDefault(false)
    }

    private fun startRead(port: SerialPort, token: Long) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val buffer = ByteArray(2048)
            try {
                loop@ while (isActive && isCurrent(port, token)) {
                    val size = runCatching { port.inputStream.read(buffer) }
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
                        logger.w { "read fail: stream closed" }
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

    override suspend fun setDtr(enabled: Boolean): Boolean {
        logger.w { "DTR is not supported by Android built-in serial port [${info.id}]" }
        return false
    }

    override suspend fun setRts(enabled: Boolean): Boolean {
        logger.w { "RTS is not supported by Android built-in serial port [${info.id}]" }
        return false
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
            runCatching { port?.tryClose() }
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
            runCatching { port.tryClose() }
        }
    }
}
