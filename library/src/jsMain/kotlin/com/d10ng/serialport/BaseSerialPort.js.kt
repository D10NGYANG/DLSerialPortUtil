package com.d10ng.serialport

actual fun buildPlatformSerialPort(
    info: SerialPortInfo,
    config: SerialPortConfig
): BaseSerialPort {
    logger.i { "buildPlatformSerialPort -> WebSerialPort(JS) for [${info.id}]" }
    return WebSerialPort(info, config)
}