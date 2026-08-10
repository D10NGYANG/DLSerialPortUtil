package com.d10ng.serialport

import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.js.Promise

/**
 * Web串口管理器
 * @Author d10ng
 * @Date 2025/9/19 13:43
 */
object WebSerialPortManager : ISerialPortManager {

    private val identityRegistry = SerialPortIdentityRegistry("web-serial")
    private val connectionStateRegistry = SerialPortConnectionStateRegistry()
    private val _portEventFlow = MutableSharedFlow<SerialPortEvent>(extraBufferCapacity = 8)
    override val portEventFlow = _portEventFlow.asSharedFlow()

    private val connectHandler: (dynamic) -> Unit = { event ->
        val port = event.target
        runCatching {
            val portInfo = toPortInfo(port)
            if (connectionStateRegistry.markConnected(port as Any, portInfo.id)) {
                if (!_portEventFlow.tryEmit(SerialPortEvent.Connected(portInfo))) {
                    logger.w { "drop serial connect event for [${portInfo.id}]: event buffer is full" }
                }
            }
        }.onFailure { logger.w { "handle serial connect event fail: ${it.message}" } }
    }
    private val disconnectHandler: (dynamic) -> Unit = { event ->
        val port = event.target
        runCatching {
            val knownInfo = toPortInfo(port)
            val shouldEmit = connectionStateRegistry.markDisconnected(port as Any, knownInfo.id)
            val info = identityRegistry.remove(port as Any) ?: knownInfo
            if (shouldEmit) {
                if (!_portEventFlow.tryEmit(SerialPortEvent.Disconnected(info))) {
                    logger.w { "drop serial disconnect event for [${info.id}]: event buffer is full" }
                }
            }
        }.onFailure { logger.w { "handle serial disconnect event fail: ${it.message}" } }
    }

    init {
        if (isSupported()) {
            val serial = js("navigator.serial")
            serial.addEventListener("connect", connectHandler)
            serial.addEventListener("disconnect", disconnectHandler)
        }
    }

    /**
     * 检查浏览器是否支持Web Serial API
     */
    override fun isSupported(): Boolean {
        val supported = js("typeof navigator !== 'undefined' && 'serial' in navigator") as Boolean
        logger.i { "[serial.capability] adapter=web-serial supported=$supported" }
        return supported
    }
    
    /**
     * 获取当前站点已经授权的串口列表，不触发浏览器设备选择器。
     */
    override suspend fun listPorts(): List<SerialPortInfo> {
        if (!isSupported()) {
            return emptyList()
        }

        val ports = (js("navigator.serial.getPorts()") as Promise<Array<dynamic>>).await()
        val result = ports.map(::toPortInfo)
        result.forEach { logger.d { "[serial.list.port] ${it.id} description=${it.description}" } }
        logger.i { "[serial.list] source=authorized-web-ports count=${result.size}" }
        return result
    }

    /**
     * 请求用户选择并授权一个串口。必须直接由点击等用户手势触发。
     */
    override suspend fun requestPort(): SerialPortInfo? {
        if (!isSupported()) return null
        return try {
            val port = (js("navigator.serial.requestPort()") as Promise<dynamic>).await()
            toPortInfo(port)
        } catch (error: Throwable) {
            if (error.asDynamic().name == "NotFoundError") null else throw error
        }
    }

    override suspend fun open(
        portInfo: SerialPortInfo,
        config: SerialPortConfig
    ): BaseSerialPort {
        logger.i { "[serial.open.request] ${portInfo.id} ${serialConfigFields(config)}" }
        return WebSerialPort(portInfo, config).apply { open() }
    }

    private fun toPortInfo(port: dynamic): SerialPortInfo {
        val info = port.getInfo()
        val vendorId = (info.usbVendorId as? Int)?.toString(16)?.uppercase() ?: "Unknown"
        val productId = (info.usbProductId as? Int)?.toString(16)?.uppercase() ?: "Unknown"
        val description = "Serial Device (VID:$vendorId, PID:$productId)"
        return identityRegistry.getOrCreate(port as Any, description)
    }

}
