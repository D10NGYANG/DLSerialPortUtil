package com.d10ng.serialport

enum class BaudRate(val intValue: Int) {

    V9600(9600),
    V19200(19200),
    V38400(38400),
    V57600(57600),
    V115200(115200);

    companion object {
        fun parse(value: Int): BaudRate = entries.firstOrNull { it.intValue == value }?: V115200
    }
}