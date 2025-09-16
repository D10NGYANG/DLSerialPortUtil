package com.d10ng.serialport

enum class StopBits(val intValue: Int) {
    V1(1),
    V2(2);

    companion object {
        fun parse(value: Int): StopBits = entries.firstOrNull { it.intValue == value }?: V2
    }
}