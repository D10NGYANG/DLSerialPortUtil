package com.d10ng.serialport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


/**
 * Web版串口 (WASM-JS)
 * @Author d10ng
 * @Date 2025/1/27 15:00
 */
@OptIn(ExperimentalWasmJsInterop::class)
class WebSerialPort(
    info: SerialPortInfo,
    config: SerialPortConfig
): BaseSerialPort(info, config) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var sp: SerialPort? = null
    private var reader: ReadableStreamDefaultReader? = null
    private var writer: WritableStreamDefaultWriter? = null
    private var readJob: Job? = null

    override suspend fun open() {
        if (sp != null) return

        runCatching {
            // 获取串口对象
            sp = (info.obj as? SerialPort) ?: throw Exception("Serial port object is null")

            // 配置串口参数
            val options = createJsSerialOptions(
                config.baudRate.intValue,
                config.dataBits.intValue,
                config.parity.text,
                config.stopBits.intValue
            )

            // 打开串口
            sp!!.open(options).await<JsAny>()

            reader = sp!!.readable!!.getReader()
            writer = sp!!.writable!!.getWriter()

            // 开始读取数据
            startRead()

            openStateFlow.value = true
        }.onFailure { exception ->
            log.w { "open fail: ${exception.message}" }
            sp = null
            reader = null
            writer = null
            throw exception
        }
    }

    private fun startRead() {
        readJob = scope.launch {
            loop@ while (isActive && sp != null) {
                runCatching {
                    val result = reader!!.read().await<ReadableStreamReadResult>()
                    if (result.done) break@loop
                    if (result.value != null) {
                        // 直接将Uint8Array转换为ByteArray并处理
                        val uint8Array = result.value!!
                        val byteArray = ByteArray(uint8Array.length)
                        for (i in 0 until uint8Array.length) {
                            byteArray[i] = uint8Array[i]
                        }
                        outputDataFlow.tryEmit(byteArray)
                    }
                }.onFailure { exception ->
                    log.w { "read fail: ${exception.message}" }
                    break@loop
                }
            }
            close()
        }
    }

    override suspend fun write(data: ByteArray): Boolean {
        return runCatching {
            // 直接将ByteArray转换为Uint8Array
            val uint8Array = Uint8Array(data.size)
            data.forEachIndexed { index, byte ->
                uint8Array[index] = byte
            }
            writer!!.write(uint8Array).await<JsAny>()
            writer!!.releaseLock()
            true
        }.onFailure { exception ->
            log.w { "write fail: ${exception.message}"}
        }.getOrDefault(false)
    }

    override fun close() {
        runCatching { readJob?.cancel() }
        runCatching { writer?.releaseLock() }
        runCatching { writer?.close() }
        runCatching { reader?.releaseLock() }
        runCatching { reader?.cancel() }
        runCatching { sp?.close() }
        readJob = null
        sp = null
        reader = null
        writer = null
        openStateFlow.value = false
    }
}