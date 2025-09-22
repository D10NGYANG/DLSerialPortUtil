package com.d10ng.serialport

import kotlinx.cinterop.*
import platform.posix.*

/**
 * POSIX系统下的串口管理器
 * @Author d10ng
 * @Date 2025/9/19 15:30
 */
@OptIn(ExperimentalForeignApi::class)
object PosixSerialPortManager: ISerialPortManager {

    override suspend fun listPorts(): List<SerialPortInfo> {
        val ports = mutableListOf<SerialPortInfo>()

        val dir = opendir("/dev") ?: return emptyList()
        runCatching {
            while (true) {
                val entry = readdir(dir) ?: break
                val name = entry.pointed.d_name.toKString()

                // 只关心 tty. 和 cu. 开头的设备
                if (name.startsWith("tty.") || name.startsWith("cu.")) {
                    val devicePath = "/dev/$name"
                    if (isSerialPortAvailable(devicePath)) {
                        ports.add(SerialPortInfo(devicePath))
                    }
                }
            }
        }
        closedir(dir)

        return ports.sortedBy { it.id }
    }

    override suspend fun open(
        portInfo: SerialPortInfo,
        config: SerialPortConfig
    ): BaseSerialPort {
        return PosixSerialPort(portInfo, config).apply { open() }
    }

    /**
     * 检查串口设备是否可用
     */
    private fun isSerialPortAvailable(devicePath: String): Boolean {
        memScoped {
            val st = alloc<stat>()
            if (stat(devicePath, st.ptr) != 0) return false
            // S_ISCHR 宏在 Kotlin/Native 中不可直接用，改为通过位掩码判断： (mode & S_IFMT) == S_IFCHR
            if ((st.st_mode.toInt() and S_IFMT) != S_IFCHR) return false

            // 尝试打开验证是否可访问
            val fd = open(devicePath, O_RDWR or O_NOCTTY or O_NONBLOCK)
            return if (fd >= 0) {
                close(fd)
                true
            } else {
                false
            }
        }
    }
}