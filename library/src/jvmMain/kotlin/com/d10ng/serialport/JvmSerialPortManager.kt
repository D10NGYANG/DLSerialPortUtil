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
        logger.i { "[serial.capability] adapter=jserialcomm supported=$supported" }
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
        list.forEach { portInfo ->
            val port = portInfo.obj as SerialPort
            logger.d {
                "[serial.list.port] ${portInfo.id} description=${portInfo.description} " +
                    "serial=${port.serialNumber}"
            }
        }
        logger.i { "[serial.list] source=jserialcomm count=${list.size}" }
        return list
    }

    override suspend fun open(portInfo: SerialPortInfo, config: SerialPortConfig): BaseSerialPort {
        logger.i { "[serial.open.request] ${portInfo.id} ${serialConfigFields(config)}" }
        return JvmSerialPort(portInfo, config).apply { open() }
    }
}
