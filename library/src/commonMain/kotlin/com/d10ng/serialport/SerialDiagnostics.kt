package com.d10ng.serialport

import kotlin.text.HexFormat

internal fun serialPayloadLog(
    type: String,
    portId: String,
    data: ByteArray,
    operationId: Long? = null
): String = buildString {
    append("[serial.")
    append(type)
    append("] ")
    append(portId)
    append(' ')
    append(data.size)
    append("B ")
    append(data.toHexString(HexFormat.UpperCase))
    if (operationId != null) {
        append(" op=")
        append(operationId)
    }
}

internal fun serialConfigFields(config: SerialPortConfig): String =
    "baud=${config.baudRate.intValue} data=${config.dataBits.intValue} " +
        "stop=${config.stopBits.intValue} parity=${config.parity.charValue} flow=none"

internal fun Throwable.serialContext(): String =
    buildString {
        append(this@serialContext::class.simpleName ?: "Throwable")
        this@serialContext.message?.takeIf { it.isNotBlank() }?.let {
            append(": ")
            append(it)
        }
    }

internal fun Throwable.hasDefinitiveDisconnectEvidence(): Boolean {
    val details = generateSequence(this as Throwable?) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
        .lowercase()
    return listOf(
        "bad file descriptor",
        "broken pipe",
        "device disconnected",
        "device has been disconnected",
        "device not configured",
        "no such device",
        "enodev",
        "ebadf"
    ).any(details::contains)
}
