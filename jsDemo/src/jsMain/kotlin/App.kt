/**
 * Js Web demo App
 * @Author d10ng
 * @Date 2025/9/19 14:32
 */

import com.d10ng.log.LogLevel
import com.d10ng.serialport.BaseSerialPort
import com.d10ng.serialport.BaudRate
import com.d10ng.serialport.DataBits
import com.d10ng.serialport.Parity
import com.d10ng.serialport.SerialPortConfig
import com.d10ng.serialport.SerialPortEvent
import com.d10ng.serialport.SerialPortInfo
import com.d10ng.serialport.SerialPortManagerLog
import com.d10ng.serialport.StopBits
import com.d10ng.serialport.WebSerialPortManager
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.dom.addClass
import kotlinx.dom.removeClass
import org.w3c.dom.Element
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLSpanElement
import org.w3c.dom.events.KeyboardEvent
import kotlin.js.Date

// 全局变量
private var currentPort: BaseSerialPort? = null
private var selectedPortInfo: SerialPortInfo? = null
private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

fun main() {
    window.addEventListener("load", {
        SerialPortManagerLog.miniLevel = LogLevel.VERBOSE
        initializeApp()
    })
}

private fun initializeApp() {
    // 检查浏览器支持
    if (!WebSerialPortManager.isSupported()) {
        showError("您的浏览器不支持Web Serial API，请使用Chrome 89+或Edge 89+")
        return
    }
    
    // 初始化UI事件
    setupEventListeners()
    observePortEvents()
    
    // 显示欢迎消息
    addSystemMessage("浏览器支持Web Serial API，可以开始使用串口通讯功能")
}

private fun observePortEvents() {
    scope.launch {
        WebSerialPortManager.portEventFlow.collect { event ->
            when (event) {
                is SerialPortEvent.Connected -> {
                    addSystemMessage("检测到串口设备接入: ${event.port.description ?: event.port.id}")
                }
                is SerialPortEvent.Disconnected -> {
                    if (selectedPortInfo?.id == event.port.id) {
                        selectedPortInfo = null
                        getElementById<HTMLDivElement>("selectedPort")?.textContent = "尚未选择串口设备"
                        updateConnectionStatus(false)
                    }
                    addSystemMessage("检测到串口设备拔出: ${event.port.description ?: event.port.id}")
                }
            }
        }
    }
}

private fun setupEventListeners() {
    // 串口选择按钮
    getElementById<HTMLButtonElement>("selectPortBtn")?.addEventListener("click", {
        selectSerialPort()
    })
    
    // 连接按钮
    getElementById<HTMLButtonElement>("connectBtn")?.addEventListener("click", {
        toggleConnection()
    })
    
    // 发送按钮
    getElementById<HTMLButtonElement>("sendBtn")?.addEventListener("click", {
        sendMessage()
    })
    
    // 输入框回车发送
    getElementById<HTMLInputElement>("messageInput")?.addEventListener("keypress", { event ->
        if ((event as KeyboardEvent).key == "Enter") {
            sendMessage()
        }
    })
}

private fun selectSerialPort() {
    scope.launch {
        try {
            // 请求用户选择串口设备
            val port = WebSerialPortManager.requestPort()
            if (port != null) {
                selectedPortInfo = port
                updateSelectedPortDisplay(port)
                enableConnectButton()
                addSystemMessage("已选择串口设备: ${port.description}")
            }
        } catch (e: Exception) {
            showError("选择串口设备失败: ${e.message}")
        }
    }
}

private fun updateSelectedPortDisplay(portInfo: SerialPortInfo) {
    getElementById<HTMLDivElement>("selectedPort")?.textContent = portInfo.description
}

private fun enableConnectButton() {
    getElementById<HTMLButtonElement>("connectBtn")?.disabled = false
}

private fun toggleConnection() {
    if (currentPort?.openStateFlow?.value == true) {
        disconnectPort()
    } else {
        connectPort()
    }
}

private fun connectPort() {
    val portInfo = selectedPortInfo
    if (portInfo == null) {
        showError("请先选择串口设备")
        return
    }
    
    scope.launch {
        try {
            setConnecting(true)
            
            // 获取配置参数
            val config = getSerialPortConfig()
            
            // 打开串口
            val port = WebSerialPortManager.open(portInfo, config)
            currentPort = port
            
            // 监听串口状态
            launch {
                port.openStateFlow.collect { isOpen ->
                    updateConnectionStatus(isOpen)
                    if (!isOpen && currentPort != null) {
                        currentPort = null
                        addSystemMessage("串口连接已断开")
                    }
                }
            }
            
            // 监听接收数据
            launch {
                port.outputDataFlow.collect { data ->
                    val message = data.decodeToString()
                    addReceivedMessage(message)
                }
            }
            
            addSystemMessage("串口连接成功")
            
        } catch (e: Exception) {
            showError("连接串口失败: ${e.message}")
            currentPort = null
            updateConnectionStatus(false)
        } finally {
            setConnecting(false)
        }
    }
}

private fun disconnectPort() {
    val port = currentPort ?: return
    currentPort = null
    scope.launch {
        try {
            setConnecting(true)
            port.closeAndAwait()
            updateConnectionStatus(false)
            addSystemMessage("已断开串口连接")
        } catch (e: Exception) {
            showError("关闭串口失败: ${e.message}")
            updateConnectionStatus(false)
        } finally {
            setConnecting(false)
        }
    }
}

private fun getSerialPortConfig(): SerialPortConfig {
    val baudRate = when (getElementById<HTMLSelectElement>("baudRate")?.value?.toIntOrNull()) {
        9600 -> BaudRate.V9600
        19200 -> BaudRate.V19200
        38400 -> BaudRate.V38400
        57600 -> BaudRate.V57600
        115200 -> BaudRate.V115200
        else -> BaudRate.V115200
    }
    
    val dataBits = when (getElementById<HTMLSelectElement>("dataBits")?.value?.toIntOrNull()) {
        7 -> DataBits.V7
        8 -> DataBits.V8
        else -> DataBits.V8
    }
    
    val parity = when (getElementById<HTMLSelectElement>("parity")?.value) {
        "none" -> Parity.NONE
        "even" -> Parity.EVEN
        "odd" -> Parity.ODD
        else -> Parity.NONE
    }
    
    val stopBits = when (getElementById<HTMLSelectElement>("stopBits")?.value?.toIntOrNull()) {
        1 -> StopBits.V1
        2 -> StopBits.V2
        else -> StopBits.V1
    }
    
    return SerialPortConfig(baudRate, dataBits, parity, stopBits)
}

private fun setConnecting(connecting: Boolean) {
    val connectBtn = getElementById<HTMLButtonElement>("connectBtn")
    val connectBtnText = getElementById<HTMLSpanElement>("connectBtnText")
    val connectBtnLoading = getElementById<HTMLDivElement>("connectBtnLoading")
    
    if (connecting) {
        connectBtn?.disabled = true
        connectBtnText?.textContent = "连接中..."
        connectBtnLoading?.style?.display = "inline-block"
    } else {
        connectBtn?.disabled = false
        connectBtnLoading?.style?.display = "none"
    }
}

private fun updateConnectionStatus(connected: Boolean) {
    val statusElement = getElementById<HTMLDivElement>("connectionStatus")
    val statusDot = statusElement?.querySelector(".status-dot")
    val statusText = statusElement?.querySelector("span")
    val connectBtn = getElementById<HTMLButtonElement>("connectBtn")
    val connectBtnText = getElementById<HTMLSpanElement>("connectBtnText")
    val messageInput = getElementById<HTMLInputElement>("messageInput")
    val sendBtn = getElementById<HTMLButtonElement>("sendBtn")
    
    if (connected) {
        statusElement?.removeClass("status-disconnected")
        statusElement?.addClass("status-connected")
        statusDot?.removeClass("disconnected")
        statusDot?.addClass("connected")
        statusText?.textContent = "已连接"
        connectBtnText?.textContent = "断开串口"
        connectBtn?.removeClass("btn-primary")
        connectBtn?.addClass("btn-danger")
        messageInput?.disabled = false
        sendBtn?.disabled = false
    } else {
        statusElement?.removeClass("status-connected")
        statusElement?.addClass("status-disconnected")
        statusDot?.removeClass("connected")
        statusDot?.addClass("disconnected")
        statusText?.textContent = "未连接"
        connectBtnText?.textContent = "打开串口"
        connectBtn?.removeClass("btn-danger")
        connectBtn?.addClass("btn-primary")
        messageInput?.disabled = true
        sendBtn?.disabled = true
        connectBtn?.disabled = selectedPortInfo == null
    }
}

private fun sendMessage() {
    val input = getElementById<HTMLInputElement>("messageInput")
    val message = input?.value?.trim()
    
    if (message.isNullOrEmpty()) {
        return
    }
    
    val port = currentPort
    if (port == null || !port.openStateFlow.value) {
        showError("串口未连接")
        return
    }
    
    scope.launch {
        try {
            // 自动在消息后添加\r\n换行符
            val dataToSend = "$message\r\n"
            val success = port.write(dataToSend.encodeToByteArray())
            
            if (success) {
                addSentMessage(message)
                input.value = ""
            } else {
                showError("发送数据失败")
            }
        } catch (e: Exception) {
            showError("发送数据失败: ${e.message}")
        }
    }
}

private fun addSystemMessage(message: String) {
    addMessage(message, "message-system")
}

private fun addSentMessage(message: String) {
    addMessage("发送: $message", "message-sent")
}

private fun addReceivedMessage(message: String) {
    addMessage("接收: $message", "message-received")
}

private fun addMessage(content: String, className: String) {
    val container = getElementById<HTMLDivElement>("messagesContainer")
    val messageDiv = document.createElement("div") as HTMLDivElement
    
    messageDiv.addClass("message")
    messageDiv.addClass(className)
    
    val headerDiv = document.createElement("div") as HTMLDivElement
    headerDiv.addClass("message-header")
    headerDiv.textContent = getCurrentTime()
    
    val contentDiv = document.createElement("div") as HTMLDivElement
    contentDiv.addClass("message-content")
    contentDiv.textContent = content
    
    messageDiv.appendChild(headerDiv)
    messageDiv.appendChild(contentDiv)
    container?.appendChild(messageDiv)
    
    // 自动滚动到底部
    container?.scrollTop = container.scrollHeight.toDouble()
}

private fun getCurrentTime(): String {
    val now = Date()
    val hours = now.getHours().toString().padStart(2, '0')
    val minutes = now.getMinutes().toString().padStart(2, '0')
    val seconds = now.getSeconds().toString().padStart(2, '0')
    return "$hours:$minutes:$seconds"
}

private fun showError(message: String) {
    val errorElement = getElementById<HTMLDivElement>("errorMessage")
    errorElement?.textContent = message
    errorElement?.style?.display = "block"
    
    // 3秒后自动隐藏错误信息
    window.setTimeout({
        errorElement?.style?.display = "none"
    }, 3000)
}

private inline fun <reified T : Element> getElementById(id: String): T? {
    return document.getElementById(id) as? T
}
