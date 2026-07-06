package com.d10ng.serialport

/**
 * 系统串口设备变化事件。
 */
sealed interface SerialPortEvent {

    val port: SerialPortInfo

    data class Connected(
        override val port: SerialPortInfo
    ) : SerialPortEvent

    data class Disconnected(
        override val port: SerialPortInfo
    ) : SerialPortEvent
}
