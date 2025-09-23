package com.d10ng.serialport

actual fun buildPlatformSerialPort(
    info: SerialPortInfo,
    config: SerialPortConfig
): BaseSerialPort {
    return PosixSerialPort(info, config)
}