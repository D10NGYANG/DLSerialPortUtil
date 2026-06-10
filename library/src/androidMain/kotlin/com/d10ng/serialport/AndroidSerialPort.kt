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

    override val isDtrSupported: Boolean = false

    // 缓存数据
    private val buffer = ByteArray(2048)
    // 循环读取数据任务
    private var readJob: Job? = null

    override suspend fun open() {
        if (sp != null) {
            logger.w { "Serial port [${info.id}] already opened" }
            return
        }
        val file = File(info.id)
        // 提权
        if (!file.canRead() || !file.canWrite()) chmod777(file)
        runCatching {
            logger.d { "open serial port [${info.id}], config: $config" }
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
            logger.w { "open fail: ${exception.message}" }
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
        if (!device.exists()) {
            logger.w { "device [${device.absolutePath}] not exists" }
            return false
        }
        if (device.canRead() && device.canWrite() && device.canExecute()) return true
        return runCatching {
            val su = Runtime.getRuntime().exec("/system/bin/su")
            val cmd = "chmod 777 ${device.absolutePath}\nexit\n"
            su.outputStream.write(cmd.toByteArray())
            0 == su.waitFor() && device.canRead() && device.canWrite() && device.canExecute()
        }.onFailure { exception ->
            logger.w { "chmod 777 ${device.absolutePath} fail: ${exception.message}" }
        }.getOrDefault(false)
    }

    private fun startRead() {
        // 启动读取线程
        readJob = scope.launch {
            loop@ while (isActive && sp != null) {
                runCatching {
                    val size = sp!!.inputStream.read(buffer)
                    if (size > 0) {
                        val data = buffer.copyOfRange(0, size)
                        logger.d { "RX HEX: ${data.toHexString(HexFormat.UpperCase)}" }
                        logger.d { "RX STR: ${data.decodeToString()}" }
                        outputDataFlow.tryEmit(data)
                    } else if (size == -1) {
                        logger.w { "read fail: stream closed" }
                        break@loop
                    }
                }.onFailure { exception ->
                    logger.w { "read fail: ${exception.message}" }
                    break@loop
                }
            }
            close()
        }
    }

    override suspend fun write(data: ByteArray): Boolean {
        return runCatching {
            logger.d { "TX HEX: ${data.toHexString(HexFormat.UpperCase)}" }
            logger.d { "TX STR: ${data.decodeToString()}" }
            sp!!.outputStream.let { os ->
                os.write(data)
                os.flush()
            }
            true
        }.onFailure { exception ->
            logger.w { "write fail: ${exception.message}"}
        }.getOrDefault(false)
    }

    override suspend fun setDtr(enabled: Boolean): Boolean {
        logger.w { "DTR is not supported by Android built-in serial port [${info.id}]" }
        return false
    }

    override fun close() {
        logger.d { "close serial port [${info.id}]" }
        runCatching { readJob?.cancel() }
        runCatching { sp?.tryClose() }
        readJob = null
        sp = null
        openStateFlow.value = false
    }
}
