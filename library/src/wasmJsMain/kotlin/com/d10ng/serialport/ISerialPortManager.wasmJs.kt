package com.d10ng.serialport

@OptIn(ExperimentalWasmJsInterop::class)
actual fun getPlatformSerialPortManager(): ISerialPortManager {
    return WebSerialPortManager
}