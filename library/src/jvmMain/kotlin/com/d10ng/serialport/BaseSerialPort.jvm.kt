package com.d10ng.serialport

actual fun buildPlatformSerialPort(
    info: SerialPortInfo,
    config: SerialPortConfig
): BaseSerialPort {
    logger.i { "buildPlatformSerialPort -> JvmSerialPort for [${info.id}]" }
    return JvmSerialPort(info, config)
}