package com.d10ng.serialport

actual fun buildPlatformSerialPort(
    info: SerialPortInfo,
    config: SerialPortConfig
): BaseSerialPort {
    logger.i { "buildPlatformSerialPort -> PosixSerialPort(MacOS) for [${info.id}]" }
    return PosixSerialPort(info, config)
}