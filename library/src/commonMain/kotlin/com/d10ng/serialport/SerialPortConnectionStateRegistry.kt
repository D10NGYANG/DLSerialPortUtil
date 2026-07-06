package com.d10ng.serialport

import kotlin.time.TimeSource

/**
 * 过滤平台层重复派发的串口连接状态事件。
 *
 * 第一层按平台对象和状态过滤；第二层处理浏览器为同一物理端口创建多个临时包装对象的情况，
 * 将相同逻辑端口在短时间内连续派发的同类事件合并。
 */
internal class SerialPortConnectionStateRegistry(
    private val duplicateWindowMillis: Long = 100L,
    private val nowMillisProvider: (() -> Long)? = null
) {

    private data class ObjectState(
        val obj: Any,
        var connected: Boolean
    )

    private data class LogicalPortState(
        val key: String,
        var connected: Boolean,
        var observedAtMillis: Long
    )

    private val objectStates = mutableListOf<ObjectState>()
    private val logicalPortStates = mutableListOf<LogicalPortState>()
    private val clockOrigin = TimeSource.Monotonic.markNow()

    fun markConnected(obj: Any, logicalPortKey: String): Boolean =
        update(obj, logicalPortKey, connected = true)

    fun markDisconnected(obj: Any, logicalPortKey: String): Boolean =
        update(obj, logicalPortKey, connected = false)

    private fun update(obj: Any, logicalPortKey: String, connected: Boolean): Boolean {
        val objectState = objectStates.firstOrNull { it.obj === obj }
        if (objectState?.connected == connected) return false
        if (objectState == null) {
            objectStates += ObjectState(obj, connected)
        } else {
            objectState.connected = connected
        }

        val now = nowMillisProvider?.invoke() ?: clockOrigin.elapsedNow().inWholeMilliseconds
        val logicalState = logicalPortStates.firstOrNull { it.key == logicalPortKey }
        val isDuplicateBurst = logicalState != null &&
            logicalState.connected == connected &&
            now - logicalState.observedAtMillis <= duplicateWindowMillis

        if (logicalState == null) {
            logicalPortStates += LogicalPortState(logicalPortKey, connected, now)
        } else {
            logicalState.connected = connected
            logicalState.observedAtMillis = now
        }
        return !isDuplicateBurst
    }
}
