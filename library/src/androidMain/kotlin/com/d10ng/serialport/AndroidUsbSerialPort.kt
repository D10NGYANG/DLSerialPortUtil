package com.d10ng.serialport

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val buffer = ByteArray(2048)
    private var sp: UsbSerialPort? = null
    private var readJob: Job? = null

    override val isDtrSupported: Boolean
        get() {
            val port = sp ?: (info.obj as? UsbSerialDriver)?.ports?.firstOrNull()
            return runCatching {
                UsbSerialPort.ControlLine.DTR in port!!.supportedControlLines
            }.getOrDefault(false)
        }

    init {
        scope.launch {
            StartupInitializer.usbDeviceDetachedFlow.filter { it == info.id }.collect {
                close()
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    override suspend fun open() {
        if (sp != null) {
            logger.w { "Serial port [${info.id}] already opened" }
            return
        }
        val driver = info.obj as UsbSerialDriver
        logger.d { "open serial port [${info.id}], config: $config" }
        // 检查权限
        if (!usbManager.hasPermission(driver.device)) {
            withContext(Dispatchers.Main) {
                usbManager.requestPermission(driver.device,
                    PendingIntent.getBroadcast(
                        ctx,
                        0,
                        Intent(ACTION_USB_PERMISSION),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_MUTABLE else 0
                    )
                )
            }
            if (!StartupInitializer.usbPermissionResultFlow.filter { it.first == info.id }.first().second) {
                throw Exception("connect fail: permission denied")
            }
        }
        runCatching {
            // 打开设备
            val connection = usbManager.openDevice(driver.device)
            if (connection == null) throw Exception("connect fail: open device fail")
            // 打开串口
            val port = driver.ports[0] // 大多数设备只有一个端口
            port.open(connection)
            // 设置参数
            port.setParameters(
                config.baudRate.intValue, // 波特率
                config.dataBits.intValue, // 数据位
                config.stopBits.intValue, // 停止位
                config.parity.intValue    // 校验位
            )
            sp = port
            startRead()
            openStateFlow.value = true
        }.onFailure { exception ->
            logger.w { "open fail: ${exception.message}" }
            sp = null
            throw exception
        }
    }

    private fun startRead() {
        // 启动读取线程
        readJob = scope.launch {
            loop@ while (isActive && sp != null) {
                runCatching {
                    val size = sp!!.read(buffer, buffer.size, READ_WAIT_MILLIS)
                    if (size > 0) {
                        val data = buffer.copyOfRange(0, size)
                        logger.d { "RX HEX: ${data.toHexString(HexFormat.UpperCase)}" }
                        logger.d { "RX STR: ${data.decodeToString()}" }
                        outputDataFlow.tryEmit(data)
                    } else if (size == -1) {
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
            sp!!.write(data, WRITE_WAIT_MILLIS)
            true
        }.onFailure { exception ->
            logger.w { "write fail: ${exception.message}"}
        }.getOrDefault(false)
    }

    override suspend fun setDtr(enabled: Boolean): Boolean {
        val port = sp ?: return false
        if (!isDtrSupported) return false
        return runCatching {
            port.setDTR(enabled)
            true
        }.onFailure { exception ->
            logger.w { "set DTR to $enabled fail: ${exception.message}" }
        }.getOrDefault(false)
    }

    override fun close() {
        logger.d { "close serial port [${info.id}]" }
        runCatching { readJob?.cancel() }
        runCatching { sp?.close() }
        readJob = null
        sp = null
        openStateFlow.value = false
    }
}
