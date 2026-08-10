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
internal val usbManager by lazy { ctx.getSystemService(Context.USB_SERVICE) as UsbManager }

internal class StartupInitializer : Initializer<Unit> {

    companion object {
        lateinit var application: Application

        internal val usbDeviceDetachedFlow = MutableSharedFlow<String>(extraBufferCapacity = 128)

        private val usbDetachReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?: return
                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        device?: return
                        logger.w { "[serial.usb.detach] ${device.deviceName} vid=${device.vendorId.toString(16).uppercase()} pid=${device.productId.toString(16).uppercase()}" }
                        if (!usbDeviceDetachedFlow.tryEmit(device.deviceName)) {
                            logger.w { "[serial.usb.detach.drop] ${device.deviceName} reason=buffer-full" }
                        }
                    }
                }
            }
        }
    }

    override fun create(context: Context) {
        application = context as Application
        logger.i { "[serial.init] android-usb detach-listener=enabled permission-ui=caller" }

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbDetachReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                context,
                usbDetachReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> {
        return mutableListOf()
    }
}
