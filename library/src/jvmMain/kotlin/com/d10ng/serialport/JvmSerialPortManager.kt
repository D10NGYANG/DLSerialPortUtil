package com.d10ng.serialport

import com.fazecast.jSerialComm.SerialPort

/**
 * JVM 串口管理器（基于 jSerialComm）
 * @Author d10ng
 * @Date 2025/9/19
 */
object JvmSerialPortManager : ISerialPortManager {

    override fun isSupported(): Boolean {
        val supported = true
        logger.i { "isSupported: $supported" }
        return supported
    }

    override suspend fun listPorts(): List<SerialPortInfo> {
        val list = SerialPort.getCommPorts()
            .map { port ->
                SerialPortInfo(
                    id = port.systemPortName,
                    description = port.descriptivePortName,
                    obj = port
                )
            }
            .sortedBy { it.id }
        logger.i { "listPorts found: ${list.size}" }
        return list
    }

    override suspend fun open(portInfo: SerialPortInfo, config: SerialPortConfig): BaseSerialPort {
        logger.i { "open request: ${portInfo.id}" }
        return JvmSerialPort(portInfo, config).apply { open() }
    }
}