package com.d10ng.serialport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

/**
 * Web版串口
 * @Author d10ng
 * @Date 2025/9/19 13:43
 */
class WebSerialPort(
    info: SerialPortInfo,
    config: SerialPortConfig
): BaseSerialPort(info, config) {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val writeMutex = Mutex()
    private var sp: dynamic = null
    private var reader: dynamic = null
    private var writer: dynamic = null
    private var readJob: Job? = null
    private var closeJob: Deferred<Unit>? = null

    override val isDtrSupported: Boolean
        get() {
            val port = sp ?: info.obj
            return port != null && jsTypeOf(port.setSignals) == "function"
        }

    override val isRtsSupported: Boolean
        get() {
            val port = sp ?: info.obj
            return port != null && jsTypeOf(port.setSignals) == "function"
        }
    
    override suspend fun open() {
        closeJob?.let { pendingClose ->
            try {
                pendingClose.await()
            } finally {
                if (closeJob === pendingClose) closeJob = null
            }
        }
        if (sp != null) {
            logger.w { "Serial port [${info.id}] already opened" }
            return
        }
        
        runCatching {
            logger.i { "[serial.open] ${info.id} ${serialConfigFields(config)}" }
            // 获取串口对象
            sp = info.obj ?: throw Exception("Serial port object is null")
            
            // 配置串口参数
            val options = js("{}")
            options.baudRate = config.baudRate.intValue
            options.dataBits = config.dataBits.intValue
            options.stopBits = config.stopBits.intValue
            options.parity = config.parity.text
            
            // 打开串口
            val openPromise = sp.open(options) as Promise<Unit>
            openPromise.await()

            reader = sp.readable.getReader()
            writer = sp.writable.getWriter()
            
            // 开始读取数据
            startRead()
            
            openStateFlow.value = true
            logger.i { "[serial.open.ok] ${info.id}" }
        }.onFailure { exception ->
            logger.w { "[serial.open.fail] ${info.id} error=${exception.serialContext()}" }
            runCatching { closeAndAwait() }
                .onFailure { closeError ->
                    logger.w { "cleanup serial port [${info.id}] after open failure: ${closeError.message}" }
                }
            closeJob = null
            throw exception
        }
    }

    private fun startRead() {
        readJob = scope.launch {
            var disconnectReason: String? = null
            logger.i { "[serial.read.start] ${info.id}" }
            loop@ while (isActive && sp != null) {
                try {
                    val result = (reader!!.read() as Promise<dynamic>).await()
                    if (result.done) {
                        disconnectReason = "readable-stream-done"
                        break@loop
                    }
                    if (result.value != null) {
                        // 直接将Uint8Array转换为ByteArray并处理
                        val uint8Array = result.value!!
                        val byteArray = ByteArray(uint8Array.length)
                        for (i in 0 until uint8Array.length) {
                            byteArray[i] = uint8Array[i]
                        }
                        emitReceived(byteArray)
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Throwable) {
                    if (sp?.connected == false || exception.hasDefinitiveDisconnectEvidence()) {
                        disconnectReason = if (sp?.connected == false) "web-serial-connected=false" else exception.serialContext()
                        break@loop
                    }
                    logger.w { "[serial.read.fail] ${info.id} action=retry error=${exception.serialContext()}" }
                    delay(50)
                }
            }
            logger.i { "[serial.read.stop] ${info.id} reason=${disconnectReason ?: "cancelled-or-replaced"}" }
            disconnectReason?.let {
                logger.w { "[serial.disconnect] ${info.id} evidence=$it" }
                beginClose(it)
            }
        }
    }

    override suspend fun write(data: ByteArray): Boolean = withWriteOperation(data.size) { operationId ->
        writeMutex.withLock {
            val currentWriter = writer
            if (currentWriter == null) {
                logger.w { "[serial.write.fail] ${info.id} op=$operationId reason=not-open" }
                return@withLock false
            }
            return@withLock try {
                logger.d { "[serial.write.start] ${info.id} op=$operationId" }
                logger.d { serialPayloadLog("tx", info.id, data, operationId) }
                val uint8Array = Uint8Array(data.size)
                uint8Array.set(data.toTypedArray())
                (currentWriter.write(uint8Array) as Promise<dynamic>).await()
                logger.d { "[serial.write.ok] ${info.id} ${data.size}B op=$operationId" }
                true
            } catch (exception: CancellationException) {
                logger.w { "[serial.write.cancel] ${info.id} op=$operationId late-browser-completion=possible" }
                throw exception
            } catch (exception: Throwable) {
                logger.w { "[serial.write.fail] ${info.id} op=$operationId error=${exception.serialContext()}" }
                false
            }
        }
    }

    override suspend fun setDtr(enabled: Boolean): Boolean {
        if (sp == null || !isDtrSupported) return false
        return runCatching {
            val signals = js("{}")
            signals.dataTerminalReady = enabled
            (sp.setSignals(signals) as Promise<Unit>).await()
            true
        }.onFailure { exception ->
            logger.w { "set DTR to $enabled fail: ${exception.message}" }
        }.getOrDefault(false)
    }

    override suspend fun setRts(enabled: Boolean): Boolean {
        if (sp == null || !isRtsSupported) return false
        return runCatching {
            val signals = js("{}")
            signals.requestToSend = enabled
            (sp.setSignals(signals) as Promise<Unit>).await()
            true
        }.onFailure { exception ->
            logger.w { "set RTS to $enabled fail: ${exception.message}" }
        }.getOrDefault(false)
    }

    override fun close() {
        beginClose("caller")
    }

    override suspend fun closeAndAwait() {
        beginClose("caller").await()
    }

    private fun beginClose(reason: String): Deferred<Unit> {
        closeJob?.let { return it }
        openStateFlow.value = false
        return scope.async(start = CoroutineStart.LAZY) {
            closeInternal(reason)
        }.also { job ->
            closeJob = job
            job.invokeOnCompletion { error ->
                if (error != null) {
                    logger.w { "close serial port [${info.id}] fail: ${error.message}" }
                }
            }
            job.start()
        }
    }

    private suspend fun closeInternal(reason: String) {
        logger.i { "[serial.close] ${info.id} reason=$reason" }
        val port = sp
        val currentReader = reader
        val currentWriter = writer
        val currentReadJob = readJob

        try {
            runCatching {
                if (currentReader != null) {
                    (currentReader.cancel() as Promise<dynamic>).await()
                }
            }.onFailure { error ->
                logger.w { "cancel serial port reader [${info.id}] fail: ${error.message}" }
            }
            runCatching { currentReadJob?.cancelAndJoin() }
                .onFailure { error ->
                    logger.w { "stop serial port reader [${info.id}] fail: ${error.message}" }
                }
            runCatching { currentWriter?.releaseLock() }
                .onFailure { error ->
                    logger.w { "release serial port writer [${info.id}] fail: ${error.message}" }
                }
            runCatching { currentReader?.releaseLock() }
                .onFailure { error ->
                    logger.w { "release serial port reader [${info.id}] fail: ${error.message}" }
                }
            if (port != null) {
                (port.close() as Promise<dynamic>).await()
            }
        } finally {
            if (sp === port) sp = null
            if (reader === currentReader) reader = null
            if (writer === currentWriter) writer = null
            if (readJob === currentReadJob) readJob = null
            openStateFlow.value = false
            logger.i { "[serial.cleanup] ${info.id} state=closed" }
        }
    }
}
