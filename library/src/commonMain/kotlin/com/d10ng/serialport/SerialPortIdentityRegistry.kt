package com.d10ng.serialport

/**
 * 为没有公开稳定设备 ID 的平台对象分配进程内唯一标识。
 *
 * ID 仅在当前页面/进程生命周期内有效。设备拔出后会移除旧对象映射，再次插入时获得新 ID，
 * 从而避免调用方误用已经失效的平台对象。
 */
internal class SerialPortIdentityRegistry(
    private val idPrefix: String
) {

    private val ports = mutableListOf<SerialPortInfo>()
    private var nextId = 1L

    fun getOrCreate(obj: Any, description: String?): SerialPortInfo {
        return ports.firstOrNull { it.obj === obj }
            ?: SerialPortInfo(
                id = "$idPrefix-${nextId++}",
                description = description,
                obj = obj
            ).also(ports::add)
    }

    fun remove(obj: Any): SerialPortInfo? {
        val index = ports.indexOfFirst { it.obj === obj }
        return if (index >= 0) ports.removeAt(index) else null
    }
}
