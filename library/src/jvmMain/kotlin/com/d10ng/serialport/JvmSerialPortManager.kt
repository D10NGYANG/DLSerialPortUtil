package com.d10ng.serialport

import com.fazecast.jSerialComm.SerialPort

/**
 * JVM 串口管理器（基于 jSerialComm）
 * @Author d10ng
 * @Date 2025/9/19
 */
object JvmSerialPortManager : ISerialPortManager {

    override fun isSupported(): Boolean {
        return true
    }

    override suspend fun listPorts(): List<SerialPortInfo> {
        return SerialPort.getCommPorts()
            .map { port ->
                SerialPortInfo(
                    id = port.systemPortName,
                    description = port.descriptivePortName,
                    obj = port
                )
            }
            .sortedBy { it.id }
    }

    override suspend fun open(portInfo: SerialPortInfo, config: SerialPortConfig): BaseSerialPort {
        return JvmSerialPort(portInfo, config).apply { open() }
    }
}