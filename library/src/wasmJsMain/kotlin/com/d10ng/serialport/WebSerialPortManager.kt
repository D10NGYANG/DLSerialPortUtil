package com.d10ng.serialport

import kotlinx.coroutines.await

/**
 * Web串口管理器 (WASM-JS)
 * @Author d10ng
 * @Date 2025/1/27 15:00
 */
@ExperimentalWasmJsInterop
object WebSerialPortManager : ISerialPortManager {
    
    /**
     * 检查浏览器是否支持Web Serial API
     */
    override fun isSupported(): Boolean {
        val supported = navigator.serial != null
        logger.i { "isSupported: $supported" }
        return supported
    }
    
    /**
     * 获取已授权的串口列表
     * 注意：Web Serial API需要用户明确授权才能访问设备
     */
    override suspend fun listPorts(): List<SerialPortInfo> {
        if (!isSupported()) {
            return emptyList()
        }

        val result = runCatching {
            val port = navigator.serial!!.requestPort().await<SerialPort>()
            val info = port.getInfo()
            val vendorId = info.usbVendorId?.toString(16)?.uppercase() ?: "Unknown"
            val productId = info.usbProductId?.toString(16)?.uppercase() ?: "Unknown"
            val description = "Serial Device (VID:$vendorId, PID:$productId)"

            listOf(
                SerialPortInfo(
                    id = port.toString(),
                    description = description,
                    obj = port
                )
            )
        }.getOrDefault(emptyList())
        logger.i { "listPorts found: ${result.size}" }
        return result
    }
    
    override suspend fun open(
        portInfo: SerialPortInfo,
        config: SerialPortConfig
    ): BaseSerialPort {
        logger.i { "open request: ${portInfo.id}" }
        return WebSerialPort(portInfo, config).apply { open() }
    }
}