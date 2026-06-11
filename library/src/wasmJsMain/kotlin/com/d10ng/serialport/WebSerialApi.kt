@file:OptIn(ExperimentalWasmJsInterop::class)

package com.d10ng.serialport

import kotlin.js.Promise

/**
 * web serial api 声明
 * @Author d10ng
 * @Date 2025/9/26 16:07
 */

external val navigator: Navigator

external interface Navigator : JsAny {
    val serial: Serial?
}

external interface Serial : JsAny {
    fun requestPort(): Promise<SerialPort>
}

external interface SerialPort : JsAny {
    val connected: Boolean
    val readable: ReadableStream?
    val writable: WritableStream?
    fun close(): Promise<JsAny>
    fun getInfo(): WebSerialPortInfo
    fun open(options: JsAny): Promise<JsAny>
    fun setSignals(signals: JsAny): Promise<JsAny>
}

external interface WebSerialPortInfo : JsAny {
    val usbProductId: Int?
    val usbVendorId: Int?
}

fun createJsSerialOptions(baudRate: Int, dataBits: Int, parity: String, stopBits: Int): JsAny =
    js("({ baudRate: baudRate, dataBits: dataBits, parity: parity, stopBits: stopBits })")

fun createJsSerialOutputSignals(dataTerminalReady: Boolean): JsAny =
    js("({ dataTerminalReady: dataTerminalReady })")

fun createJsSerialRtsOutputSignals(requestToSend: Boolean): JsAny =
    js("({ requestToSend: requestToSend })")

external interface ReadableStream: JsAny {
    fun getReader(): ReadableStreamDefaultReader
}

external interface ReadableStreamDefaultReader: JsAny {
    fun read(): Promise<ReadableStreamReadResult>
    fun releaseLock()
    fun cancel(): Promise<JsAny>
}

external interface ReadableStreamReadResult: JsAny {
    val done: Boolean
    val value: Uint8Array?
}

external interface WritableStream: JsAny {
    fun getWriter(): WritableStreamDefaultWriter
}

external interface WritableStreamDefaultWriter: JsAny {
    fun write(data: Uint8Array): Promise<JsAny>
    fun releaseLock()
    fun close(): Promise<JsAny>
}

external class Uint8Array: JsAny {
    constructor(length: Int)
    val length: Int
    operator fun get(index: Int): Byte
    operator fun set(index: Int, value: Byte)
}
