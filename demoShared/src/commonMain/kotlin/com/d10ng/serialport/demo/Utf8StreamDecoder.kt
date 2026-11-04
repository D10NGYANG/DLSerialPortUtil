package com.d10ng.serialport.demo

internal const val MAX_MESSAGES = 200

/** Retain only an incomplete trailing character, not arbitrary serial packets. */
internal class Utf8StreamDecoder {
    private var pending = byteArrayOf()

    fun decode(bytes: ByteArray): String {
        val data = pending + bytes
        var start = data.lastIndex
        while (start >= 0 && (data[start].toInt() and 0xC0) == 0x80) start--
        val lead = data.getOrNull(start)?.toInt()?.and(0xFF) ?: 0
        val length = when (lead) {
            in 0xC2..0xDF -> 2
            in 0xE0..0xEF -> 3
            in 0xF0..0xF4 -> 4
            else -> 1
        }
        val end = if (start >= 0 && data.size - start < length) start else data.size
        pending = data.copyOfRange(end, data.size)
        return data.decodeToString(0, end)
    }

    fun finish(): String = pending.decodeToString().also { pending = byteArrayOf() }
}
