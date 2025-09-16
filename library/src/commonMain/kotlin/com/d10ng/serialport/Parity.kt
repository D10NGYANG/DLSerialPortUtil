package com.d10ng.serialport

enum class Parity(val charValue: Char, val text: String, val intValue: Int) {

    NONE('N', "none",0),
    EVEN('E', "even", 2),
    ODD('O', "odd", 1);

    companion object {
        fun parse(value: Char): Parity = entries.firstOrNull { it.charValue == value }?: NONE

        fun parse(value: Int): Parity = entries.firstOrNull { it.intValue == value }?: NONE
    }
}