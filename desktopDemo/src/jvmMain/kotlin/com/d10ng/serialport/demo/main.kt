package com.d10ng.serialport.demo

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "串口通讯测试",
    ) {
        App()
    }
}