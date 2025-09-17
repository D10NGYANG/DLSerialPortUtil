package com.d10ng.serialport

/**
 * 串口管理
 * @Author d10ng
 * @Date 2025/9/17 11:23
 */
interface ISerialPortManager {

    /**
     * 获取串口列表
     */
    suspend fun listPorts(): List<SerialPortInfo>

    /**
     * 打开串口
     * @param portInfo 串口信息
     * @param config 串口配置
     * @return BaseSerialPort
     */
    suspend fun <T : BaseSerialPort> open(portInfo: SerialPortInfo, config: SerialPortConfig): T
}