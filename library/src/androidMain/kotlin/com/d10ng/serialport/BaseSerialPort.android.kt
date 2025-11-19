package com.d10ng.serialport

actual fun buildPlatformSerialPort(
    info: SerialPortInfo,
    config: SerialPortConfig
): BaseSerialPort {
    logger.i { "buildPlatformSerialPort -> AndroidSerialPort for [${info.id}]" }
    return AndroidSerialPort(info, config)
}