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
        logger.i { "isSupported: $supported" }
        return supported
    }

    override suspend fun listPorts(): List<SerialPortInfo> {
        val devDir = File("/dev/")
        if (!devDir.exists() || !devDir.isDirectory) {
            return emptyList()
        }

        val list = devDir.listFiles()
            ?.filter { file ->
                file.canRead() && file.canWrite() && file.name.startsWith("tty")
            }
            ?.map { it.absolutePath }
            ?.sorted()
            ?.map { SerialPortInfo(it) }
            ?: emptyList()
        logger.i { "listPorts found: ${list.size}" }
        return list
    }

    override suspend fun open(
        portInfo: SerialPortInfo,
        config: SerialPortConfig
    ): AndroidSerialPort {
        logger.i { "open request: ${portInfo.id}" }
        return AndroidSerialPort(portInfo, config).apply { open() }
    }
}