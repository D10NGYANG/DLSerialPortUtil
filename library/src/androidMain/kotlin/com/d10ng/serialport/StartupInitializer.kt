package com.d10ng.serialport

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.startup.Initializer
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * 启动初始化
 * @Author d10ng
 * @Date 2024/9/10 15:37
 */

internal val ctx by lazy { StartupInitializer.application }
internal val ACTION_USB_PERMISSION by lazy { "com.d10ng.serialport.USB_PERMISSION" }
internal val usbManager by lazy { ctx.getSystemService(Context.USB_SERVICE) as UsbManager }

internal class StartupInitializer : Initializer<Unit> {

    companion object {
        lateinit var application: Application

        internal val usbPermissionResultFlow = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 128)
        internal val usbDeviceDetachedFlow = MutableSharedFlow<String>(extraBufferCapacity = 128)

        // USB权限请求广播接收器
        private val usbPermissionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?: return
                when (intent.action) {
                    ACTION_USB_PERMISSION -> {
                        // 权限申请结果
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        device?: return
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        logger.i { "${device.deviceName} usb permission request result: $granted" }
                        usbPermissionResultFlow.tryEmit(device.deviceName to granted)
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        // 设备拔出
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        device?: return
                        logger.i { "${device.deviceName} usb device detached" }
                        usbDeviceDetachedFlow.tryEmit(device.deviceName)
                    }
                }
            }
        }
    }

    override fun create(context: Context) {
        application = context as Application

        // 注册广播接收器
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                context,
                usbPermissionReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> {
        return mutableListOf()
    }
}