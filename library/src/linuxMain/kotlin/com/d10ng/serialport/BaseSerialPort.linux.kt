package com.d10ng.serialport

actual fun buildPlatformSerialPort(
    info: SerialPortInfo,
    config: SerialPortConfig
): BaseSerialPort {
    logger.i { "buildPlatformSerialPort -> PosixSerialPort(Linux) for [${info.id}]" }
    return PosixSerialPort(info, config)
}