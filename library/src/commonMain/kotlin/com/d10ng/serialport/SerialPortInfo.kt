package com.d10ng.serialport

/**
 * 串口信息
 * @Author d10ng
 * @Date 2025/9/17 11:22
 */
data class SerialPortInfo(
    // 平台唯一标识（例如 Linux 下是 /dev/ttyS0，Android 下是 USB Device ID）
    val id: String,
    val description: String? = null
)
