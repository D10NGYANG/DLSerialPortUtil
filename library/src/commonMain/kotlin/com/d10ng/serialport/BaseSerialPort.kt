package com.d10ng.serialport

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 串口基类
 * @Author d10ng
 * @Date 2025/9/17 11:10
 */
abstract class BaseSerialPort(
    val info: SerialPortInfo,
    val config: SerialPortConfig
) {
    /**
     * 输出数据流
     */
    val outputDataFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)

    /**
     * 串口是否处于开启状态
     */
    val openStateFlow = MutableStateFlow(false)

    /**
     * 当前串口实现是否支持 DTR 控制
     */
    abstract val isDtrSupported: Boolean

    /**
     * 当前串口实现是否支持 RTS 控制
     */
    abstract val isRtsSupported: Boolean

    /**
     * 打开串口
     */
    abstract suspend fun open()

    /**
     * 设置 DTR 信号
     * @param enabled Boolean 是否启用
     * @return Boolean 是否设置成功
     */
    abstract suspend fun setDtr(enabled: Boolean): Boolean

    /**
     * 设置 RTS 信号
     * @param enabled Boolean 是否启用
     * @return Boolean 是否设置成功
     */
    abstract suspend fun setRts(enabled: Boolean): Boolean

    /**
     * 写数据
     * @param data ByteArray 数据
     * @return Boolean 是否成功
     */
    abstract suspend fun write(data: ByteArray): Boolean

    /**
     * 关闭串口
     */
    abstract fun close()

    /**
     * 关闭串口并等待底层资源释放完成。
     *
     * 默认实现兼容同步关闭的平台；Web 等异步关闭平台应覆盖此方法。
     */
    open suspend fun closeAndAwait() {
        close()
    }
}

/**
 * 创建平台串口
 * @param info SerialPortInfo 串口信息
 * @param config SerialPortConfig 串口配置
 * @return BaseSerialPort
 */
expect fun buildPlatformSerialPort(info: SerialPortInfo, config: SerialPortConfig): BaseSerialPort
