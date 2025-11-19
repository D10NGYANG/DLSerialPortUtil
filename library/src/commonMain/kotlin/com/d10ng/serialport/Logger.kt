package com.d10ng.serialport

import com.d10ng.log.LoggerFactory

/**
 * 日志
 * @Author d10ng
 * @Date 2025/9/25 17:29
 */
internal val logger by lazy { LoggerFactory.create("SerialPort") }

// 提供给外部使用的日志名，避免冲突
val SerialPortManagerLog by lazy { logger }