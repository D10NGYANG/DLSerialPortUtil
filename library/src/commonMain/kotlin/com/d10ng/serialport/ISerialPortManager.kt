package com.d10ng.serialport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 串口管理
 * @Author d10ng
 * @Date 2025/9/17 11:23
 */
interface ISerialPortManager{

    /**
     * 串口设备接入、拔出事件。
     *
     * 不支持设备事件的平台保持为空 Flow。
     */
    val portEventFlow: Flow<SerialPortEvent>
        get() = emptyFlow()

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
     * 请求用户选择并授权一个串口。
     *
     * 只有需要显式授权的平台（例如 Web）才需要覆盖此方法。该方法必须直接由用户手势触发。
     * 用户取消选择时返回 null；权限、策略或其他平台异常会原样抛出。
     *
     * @return 已授权的串口；当前平台无需或不支持显式授权时返回 null
     */
    suspend fun requestPort(): SerialPortInfo? = null

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
