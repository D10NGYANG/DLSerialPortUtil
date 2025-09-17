package com.d10ng.serialport

data class SerialPortConfig(
    // 波特率
    var baudRate: BaudRate = BaudRate.V115200,
    // 数据位
    var dataBits: DataBits = DataBits.V8,
    // 校验位
    var parity: Parity = Parity.NONE,
    // 停止位
    var stopBits: StopBits = StopBits.V1,
)
