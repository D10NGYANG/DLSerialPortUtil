package com.d10ng.serialport

enum class DataBits(val intValue: Int) {

    V7(7),
    V8(8);

    companion object {
        fun parse(value: Int): DataBits = entries.firstOrNull { it.intValue == value }?: V8
    }
}