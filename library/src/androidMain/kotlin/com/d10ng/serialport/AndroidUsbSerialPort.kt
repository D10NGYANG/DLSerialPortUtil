package com.d10ng.serialport

import android.app.PendingIntent
import android.content.Intent
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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

    override suspend fun open() {
        if (sp != null) return
        val driver = info.obj as UsbSerialPort
        // 检查权限
        if (!usbManager.hasPermission(driver.device)) {
            withContext(Dispatchers.Main) {
                usbManager.requestPermission(driver.device,
                    PendingIntent.getBroadcast(
                        ctx,
                        0,
                        Intent(ACTION_USB_PERMISSION),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
            if (!StartupInitializer.usbPermissionResultFlow.filter { it.first == info.id }.first().second) {
                throw Exception("connect fail: permission denied")
            }
        }
    }

    override fun write(data: ByteArray): Boolean {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}