package com.d10ng.serialport

import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber

/**
 * android Usb串口管理
 * @Author d10ng
 * @Date 2025/9/19 09:20
 */
object AndroidUsbSerialPortManager: ISerialPortManager {

    override fun isSupported(): Boolean {
        val supported = true
        logger.i { "[serial.capability] adapter=android-usb supported=$supported" }
        return supported
    }

    override suspend fun listPorts(): List<SerialPortInfo> {
        val list = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            .flatMap { driver ->
                val mName = driver.device.manufacturerName ?: "Unknown"
                val pName = driver.device.productName ?: "Unknown"
                val vId = driver.device.vendorId.toString(16).uppercase()
                val pId = driver.device.productId.toString(16).uppercase()
                driver.ports.indices.map { portIndex ->
                    val id = if (driver.ports.size == 1) {
                        driver.device.deviceName
                    } else {
                        "${driver.device.deviceName}#$portIndex"
                    }
                    val portDescription = if (driver.ports.size == 1) "" else " Port ${portIndex + 1}"
                    SerialPortInfo(
                        id,
                        "$mName $pName$portDescription (VID:$vId, PID:$pId)",
                        AndroidUsbSerialPortHandle(driver, portIndex)
                    )
                }
            }
        list.forEach { portInfo ->
            val handle = portInfo.obj as AndroidUsbSerialPortHandle
            val device = handle.driver.device
            val serialNumber = runCatching { device.serialNumber }.getOrNull() ?: "Unknown"
            logger.d {
                "[serial.list.port] ${portInfo.id} vid=${device.vendorId.toString(16).uppercase()} " +
                    "pid=${device.productId.toString(16).uppercase()} serial=$serialNumber"
            }
        }
        logger.i { "[serial.list] source=usb-default-prober count=${list.size}" }
        return list
    }

    override suspend fun open(
        portInfo: SerialPortInfo,
        config: SerialPortConfig
    ): BaseSerialPort {
        logger.i { "[serial.open.request] ${portInfo.id} ${serialConfigFields(config)}" }
        return AndroidUsbSerialPort(portInfo, config).apply { open() }
    }
}

internal data class AndroidUsbSerialPortHandle(
    val driver: UsbSerialDriver,
    val portIndex: Int
)
