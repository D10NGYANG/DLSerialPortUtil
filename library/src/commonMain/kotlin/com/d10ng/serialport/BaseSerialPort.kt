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
    val outputDataFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = Int.MAX_VALUE)

    /**
     * 串口是否处于开启状态
     */
    val openStateFlow = MutableStateFlow(false)

    /**
     * 打开串口
     */
    abstract fun open()

    /**
     * 写数据
     * @param data ByteArray 数据
     * @return Boolean 是否成功
     */
    abstract fun write(data: ByteArray): Boolean

    /**
     * 关闭串口
     */
    abstract fun close()
}