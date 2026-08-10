package com.d10ng.serialport

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 串口基类
 * @Author d10ng
 * @Date 2025/9/17 11:10
 */
abstract class BaseSerialPort(
    val info: SerialPortInfo,
    val config: SerialPortConfig
) {
    companion object {
        internal const val RX_REPLAY_CAPACITY = 64
    }

    /**
     * 输出数据流
     */
    val outputDataFlow = MutableSharedFlow<ByteArray>(replay = RX_REPLAY_CAPACITY)

    /**
     * 串口是否处于开启状态
     */
    val openStateFlow = MutableStateFlow(false)

    private val writeOperationIdMutex = Mutex()
    private val writeOperationExecutionMutex = Mutex()
    private var writeOperationSequence = 0L

    protected suspend fun <T> withWriteOperation(
        dataSize: Int,
        block: suspend (Long) -> T
    ): T {
        val operationId = writeOperationIdMutex.withLock { ++writeOperationSequence }
        logger.d { "[serial.write.queue] ${info.id} ${dataSize}B op=$operationId" }
        var operationStarted = false
        return try {
            writeOperationExecutionMutex.withLock {
                operationStarted = true
                block(operationId)
            }
        } catch (exception: CancellationException) {
            if (!operationStarted) {
                logger.w { "[serial.write.cancel] ${info.id} stage=queue op=$operationId" }
            }
            throw exception
        }
    }

    protected suspend fun emitReceived(data: ByteArray) {
        if (
            outputDataFlow.subscriptionCount.value == 0 &&
            outputDataFlow.replayCache.size >= RX_REPLAY_CAPACITY
        ) {
            logger.w {
                "[serial.rx.drop] ${info.id} reason=buffer-full capacity=$RX_REPLAY_CAPACITY"
            }
        }
        logger.d { serialPayloadLog("rx", info.id, data) }
        outputDataFlow.emit(data)
    }

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
