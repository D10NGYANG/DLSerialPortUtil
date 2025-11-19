package com.d10ng.serialport

@OptIn(ExperimentalWasmJsInterop::class)
actual fun getPlatformSerialPortManager(): ISerialPortManager {
    logger.i { "getPlatformSerialPortManager -> WebSerialPortManager(WASM-JS)" }
    return WebSerialPortManager
}