package com.d10ng.serialport

actual fun getPlatformSerialPortManager(): ISerialPortManager {
    logger.i { "getPlatformSerialPortManager -> WebSerialPortManager(JS)" }
    return WebSerialPortManager
}