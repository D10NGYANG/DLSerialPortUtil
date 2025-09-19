package com.d10ng.serialport

import java.io.File

/**
 * Android 串口管理
 * @Author d10ng
 * @Date 2025/9/18 17:23
 */
object AndroidSerialPortManager: ISerialPortManager<AndroidSerialPort> {
    override suspend fun listPorts(): List<SerialPortInfo> {
        val devDir = File("/dev/")
        if (!devDir.exists() || !devDir.isDirectory) {
            return emptyList()
        }

        return devDir.listFiles()
            ?.filter { file ->
                file.canRead() && file.canWrite() && file.name.startsWith("tty")
            }
            ?.map { it.absolutePath }
            ?.sorted()
            ?.map { SerialPortInfo(it) }
            ?: emptyList()
    }

    override suspend fun open(
        portInfo: SerialPortInfo,
        config: SerialPortConfig
    ): AndroidSerialPort {
        return AndroidSerialPort(portInfo, config).apply { open() }
    }
}