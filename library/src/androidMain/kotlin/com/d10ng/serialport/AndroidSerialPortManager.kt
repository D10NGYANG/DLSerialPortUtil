package com.d10ng.serialport

import java.io.File

/**
 * Android 串口管理
 * @Author d10ng
 * @Date 2025/9/18 17:23
 */
object AndroidSerialPortManager: ISerialPortManager {

    override fun isSupported(): Boolean {
        val supported = true
        logger.i { "[serial.capability] adapter=android-device-node supported=$supported" }
        return supported
    }

    override suspend fun listPorts(): List<SerialPortInfo> {
        val devDir = File("/dev/")
        if (!devDir.exists() || !devDir.isDirectory) {
            logger.w { "[serial.list.fail] root=/dev exists=${devDir.exists()} directory=${devDir.isDirectory}" }
            return emptyList()
        }

        val list = devDir.listFiles()
            ?.filter { file ->
                val matches = file.name.startsWith("tty")
                if (matches) {
                    logger.d { "[serial.list.candidate] ${file.absolutePath} read=${file.canRead()} write=${file.canWrite()}" }
                }
                matches && file.canRead() && file.canWrite()
            }
            ?.map { it.absolutePath }
            ?.sorted()
            ?.map { SerialPortInfo(it) }
            ?: emptyList()
        logger.i { "[serial.list] root=/dev prefix=tty accessible=true count=${list.size}" }
        return list
    }

    override suspend fun open(
        portInfo: SerialPortInfo,
        config: SerialPortConfig
    ): AndroidSerialPort {
        logger.i { "[serial.open.request] ${portInfo.id} ${serialConfigFields(config)}" }
        return AndroidSerialPort(portInfo, config).apply { open() }
    }
}
