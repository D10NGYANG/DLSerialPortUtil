package com.d10ng.serialport

import android.serialport.SerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var sp: SerialPort? = null

    // 缓存数据
    private val buffer = ByteArray(2048)
    // 循环读取数据任务
    private var readJob: Job? = null

    override suspend fun open() {
        if (sp != null) return
        val file = File(info.id)
        // 提权
        if (!file.canRead() || !file.canWrite()) chmod777(file)
        runCatching {
            // 打开串口
            sp = SerialPort(
                file,
                config.baudRate.intValue,
                config.dataBits.intValue,
                config.parity.intValue,
                config.stopBits.intValue
            )
            startRead()
            openStateFlow.value = true
        }.onFailure { exception ->
            sp = null
            throw exception
        }
    }

    /**
     * 修改权限
     * @param device File
     * @return Boolean
     */
    private fun chmod777(device: File): Boolean {
        if (!device.exists()) return false
        if (device.canRead() && device.canWrite() && device.canExecute()) return true
        return runCatching {
            val su = Runtime.getRuntime().exec("/system/bin/su")
            val cmd = "chmod 777 ${device.absolutePath}\nexit\n"
            su.outputStream.write(cmd.toByteArray())
            0 == su.waitFor() && device.canRead() && device.canWrite() && device.canExecute()
        }.getOrDefault(false)
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
                }.onFailure { break@loop }
            }
            close()
        }
    }

    override fun write(data: ByteArray): Boolean {
        return runCatching {
            sp!!.outputStream.use { os ->
                os.write(data)
                os.flush()
            }
            true
        }.getOrDefault(false)
    }

    override fun close() {
        runCatching { readJob?.cancel() }
        runCatching { sp?.tryClose() }
        readJob = null
        sp = null
        openStateFlow.value = false
    }
}