package com.d10ng.serialport

import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Web串口管理器 (WASM-JS)
 * @Author d10ng
 * @Date 2025/1/27 15:00
 */
@ExperimentalWasmJsInterop
object WebSerialPortManager : ISerialPortManager {

    private val identityRegistry = SerialPortIdentityRegistry("web-serial")
    private val connectionStateRegistry = SerialPortConnectionStateRegistry()
    private val _portEventFlow = MutableSharedFlow<SerialPortEvent>(extraBufferCapacity = 8)
    override val portEventFlow = _portEventFlow.asSharedFlow()

    private val connectHandler: (SerialConnectionEvent) -> Unit = { event ->
        runCatching {
            val port = event.target
            if (connectionStateRegistry.markConnected(port, port.logicalKey())) {
                _portEventFlow.tryEmit(SerialPortEvent.Connected(port.toPortInfo()))
            }
        }.onFailure { logger.w { "handle serial connect event fail: ${it.message}" } }
    }
    private val disconnectHandler: (SerialConnectionEvent) -> Unit = { event ->
        runCatching {
            val port = event.target
            if (connectionStateRegistry.markDisconnected(port, port.logicalKey())) {
                val info = identityRegistry.remove(port) ?: port.toPortInfo()
                _portEventFlow.tryEmit(SerialPortEvent.Disconnected(info))
            }
        }.onFailure { logger.w { "handle serial disconnect event fail: ${it.message}" } }
    }

    init {
        if (isSupported()) {
            navigator.serial!!.addEventListener("connect", connectHandler)
            navigator.serial!!.addEventListener("disconnect", disconnectHandler)
        }
    }

    /**
     * 检查浏览器是否支持Web Serial API
     */
    override fun isSupported(): Boolean {
        val supported = navigator.serial != null
        logger.i { "isSupported: $supported" }
        return supported
    }
    
    /**
     * 获取当前站点已经授权的串口列表，不触发浏览器设备选择器。
     */
    override suspend fun listPorts(): List<SerialPortInfo> {
        if (!isSupported()) {
            return emptyList()
        }

        val ports = navigator.serial!!.getPorts().await<JsArray<SerialPort>>()
        val result = ports.toArray().map { it.toPortInfo() }
        logger.i { "listPorts found: ${result.size}" }
        return result
    }

    /**
     * 请求用户选择并授权一个串口。必须直接由点击等用户手势触发。
     */
    override suspend fun requestPort(): SerialPortInfo? {
        if (!isSupported()) return null
        return requestSerialPortOrNull(navigator.serial!!).await<SerialPort?>()?.toPortInfo()
    }

    override suspend fun open(
        portInfo: SerialPortInfo,
        config: SerialPortConfig
    ): BaseSerialPort {
        logger.i { "open request: ${portInfo.id}" }
        return WebSerialPort(portInfo, config).apply { open() }
    }

    private fun SerialPort.toPortInfo(): SerialPortInfo {
        val info = getInfo()
        val vendorId = info.usbVendorId?.toString(16)?.uppercase() ?: "Unknown"
        val productId = info.usbProductId?.toString(16)?.uppercase() ?: "Unknown"
        val description = "Serial Device (VID:$vendorId, PID:$productId)"
        return identityRegistry.getOrCreate(this, description)
    }

    private fun SerialPort.logicalKey(): String {
        val info = getInfo()
        return "usb:${info.usbVendorId ?: "unknown"}:${info.usbProductId ?: "unknown"}"
    }
}
