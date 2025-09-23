package com.d10ng.serialport

actual fun getPlatformSerialPortManager(): ISerialPortManager {
    return PosixSerialPortManager
}