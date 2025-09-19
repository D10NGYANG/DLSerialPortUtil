package com.d10ng.serialport

import com.hoho.android.usbserial.driver.UsbSerialProber

/**
 * android Usb串口管理
 * @Author d10ng
 * @Date 2025/9/19 09:20
 */
object AndroidUsbSerialPortManager: ISerialPortManager {
    override suspend fun listPorts(): List<SerialPortInfo> {
        return UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            .map { driver ->
                val mName = driver.device.manufacturerName ?: "Unknown"
                val pName = driver.device.productName ?: "Unknown"
                val vId = driver.device.vendorId.toString(16).uppercase()
                val pId = driver.device.productId.toString(16).uppercase()
                SerialPortInfo(driver.device.deviceName, "$mName $pName (VID:$vId, PID:$pId)", driver)
            }
    }

    override suspend fun open(
        portInfo: SerialPortInfo,
        config: SerialPortConfig
    ): BaseSerialPort {
        return AndroidUsbSerialPort(portInfo, config).apply { open() }
    }
}