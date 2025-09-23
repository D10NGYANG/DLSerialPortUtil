package com.d10ng.serialport

/**
 * 串口管理
 * @Author d10ng
 * @Date 2025/9/17 11:23
 */
interface ISerialPortManager{

    /**
     * 判断当前环境是否支持串口通讯
     * @return Boolean
     */
    fun isSupported(): Boolean

    /**
     * 获取串口列表
     * @return List<SerialPortInfo>
     */
    suspend fun listPorts(): List<SerialPortInfo>

    /**
     * 打开串口
     * @param portInfo 串口信息
     * @param config 串口配置
     * @return BaseSerialPort
     */
    suspend fun open(portInfo: SerialPortInfo, config: SerialPortConfig): BaseSerialPort
}

/**
 * 获取平台串口管理器
 */
expect fun getPlatformSerialPortManager(): ISerialPortManager