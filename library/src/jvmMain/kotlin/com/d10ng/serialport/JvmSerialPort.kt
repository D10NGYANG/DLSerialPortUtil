package com.d10ng.serialport

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    private var sp: SerialPort? = null

    // 缓存数据
    private val buffer = ByteArray(2048)
    // 循环读取数据任务
    private var readJob: Job? = null

    override suspend fun open() {
        if (sp != null) return
        runCatching {
            // 选择串口
            val port = info.obj as SerialPort

            // 配置串口参数
            val stopBits = when (config.stopBits) {
                StopBits.V1 -> SerialPort.ONE_STOP_BIT
                StopBits.V2 -> SerialPort.TWO_STOP_BITS
            }
            port.setComPortParameters(
                config.baudRate.intValue,
                config.dataBits.intValue,
                stopBits,
                config.parity.intValue
            )
            port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0)

            if (!port.openPort()) throw Exception("connect fail: open port fail")

            sp = port
            startRead()
            openStateFlow.value = true
        }.onFailure { exception ->
            log.w { "open fail: ${exception.message}" }
            sp = null
            throw exception
        }
    }

    private fun startRead() {
        // 启动读取线程
        readJob = scope.launch {
            loop@ while (isActive && sp != null) {
                runCatching {
                    val size = sp!!.inputStream.read(buffer)
                    if (size > 0) {
                        outputDataFlow.tryEmit(buffer.copyOfRange(0, size))
                    } else if (size == -1) {
                        break@loop
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
            sp!!.outputStream.use { os ->
                os.write(data)
                os.flush()
            }
            true
        }.onFailure { exception ->
            log.w { "write fail: ${exception.message}"}
        }.getOrDefault(false)
    }

    override fun close() {
        runCatching { readJob?.cancel() }
        runCatching { sp?.closePort() }
        readJob = null
        sp = null
        openStateFlow.value = false
    }
}