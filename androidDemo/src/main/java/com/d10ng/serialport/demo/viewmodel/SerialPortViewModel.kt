package com.d10ng.serialport.demo.viewmodel

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.d10ng.serialport.AndroidSerialPortManager
import com.d10ng.serialport.AndroidUsbSerialPortManager
import com.d10ng.serialport.BaseSerialPort
import com.d10ng.serialport.SerialPortConfig
import com.d10ng.serialport.SerialPortInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 串口通讯ViewModel
 */
class SerialPortViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.d10ng.serialport.demo.USB_PERMISSION"
        private const val USB_PERMISSION_TIMEOUT_MILLIS = 30_000L
    }
    
    // 串口配置
    private val _config = MutableStateFlow(SerialPortConfig())
    val config: StateFlow<SerialPortConfig> = _config.asStateFlow()
    
    // 机内串口列表
    private val _serialPorts = MutableStateFlow<List<SerialPortInfo>>(emptyList())
    val serialPorts: StateFlow<List<SerialPortInfo>> = _serialPorts.asStateFlow()
    
    // USB串口列表
    private val _usbPorts = MutableStateFlow<List<SerialPortInfo>>(emptyList())
    val usbPorts: StateFlow<List<SerialPortInfo>> = _usbPorts.asStateFlow()
    
    // 当前连接的串口
    private val _currentPort = MutableStateFlow<BaseSerialPort?>(null)
    val currentPort: StateFlow<BaseSerialPort?> = _currentPort.asStateFlow()
    
    // 串口连接状态
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // 通讯消息列表
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var portObservationJob: Job? = null
    
    init {
        // 初始化时加载串口列表
        loadSerialPorts()
        loadUsbPorts()
    }
    
    /**
     * 更新串口配置
     */
    fun updateConfig(newConfig: SerialPortConfig) {
        _config.value = newConfig
    }
    
    /**
     * 加载机内串口列表
     */
    fun loadSerialPorts() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val ports = AndroidSerialPortManager.listPorts()
                _serialPorts.value = ports
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = "加载串口列表失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 加载USB串口列表
     */
    fun loadUsbPorts() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val ports = AndroidUsbSerialPortManager.listPorts()
                _usbPorts.value = ports
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = "加载USB串口列表失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 打开机内串口
     */
    fun openSerialPort(portInfo: SerialPortInfo) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                val port = AndroidSerialPortManager.open(portInfo, _config.value)
                _currentPort.value = port
                observePort(port, "串口连接已断开")
                
                addMessage("系统", "串口连接成功: ${portInfo.id}", MessageType.SYSTEM)
                
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = "打开串口失败: ${e.message}"
                _currentPort.value = null
                _isConnected.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 打开USB串口
     */
    fun openUsbPort(portInfo: SerialPortInfo) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                requestUsbPermission(portInfo)
                val port = AndroidUsbSerialPortManager.open(portInfo, _config.value)
                _currentPort.value = port
                observePort(port, "USB串口连接已断开")
                
                addMessage("系统", "USB串口连接成功: ${portInfo.description ?: portInfo.id}", MessageType.SYSTEM)
                
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = "打开USB串口失败: ${e.message}"
                _currentPort.value = null
                _isConnected.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun observePort(port: BaseSerialPort, disconnectedMessage: String) {
        portObservationJob?.cancel()
        val observationJob = viewModelScope.launch observation@{
            launch {
                port.outputDataFlow.collect { data ->
                    addMessage("接收", String(data, Charsets.UTF_8), MessageType.RECEIVED)
                }
            }
            port.openStateFlow.collect { isOpen ->
                _isConnected.value = isOpen
                if (!isOpen && _currentPort.value === port) {
                    _currentPort.value = null
                    addMessage("系统", disconnectedMessage, MessageType.SYSTEM)
                    this@observation.cancel()
                }
            }
        }
        portObservationJob = observationJob
        observationJob.invokeOnCompletion {
            if (portObservationJob === observationJob) portObservationJob = null
        }
    }

    private suspend fun requestUsbPermission(portInfo: SerialPortInfo) {
        val context = getApplication<Application>()
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceName = portInfo.id.substringBefore('#')
        val device = usbManager.deviceList.values.firstOrNull { it.deviceName == deviceName }
            ?: throw IllegalStateException("USB设备已断开: $deviceName")
        if (usbManager.hasPermission(device)) return

        val completedInTime = withTimeoutOrNull(USB_PERMISSION_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val completed = AtomicBoolean(false)
                lateinit var receiver: BroadcastReceiver

                fun finish(result: Result<Unit>) {
                    if (!completed.compareAndSet(false, true)) return
                    runCatching { context.unregisterReceiver(receiver) }
                    result.fold(continuation::resume, continuation::resumeWithException)
                }

                receiver = object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context?, intent: Intent?) {
                        if (intent?.action != ACTION_USB_PERMISSION) return
                        val resultDevice = intent.usbDeviceExtra()
                        if (resultDevice?.deviceName != deviceName) return
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            finish(Result.success(Unit))
                        } else {
                            finish(Result.failure(SecurityException("用户拒绝USB设备授权: $deviceName")))
                        }
                    }
                }

                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(ACTION_USB_PERMISSION),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                continuation.invokeOnCancellation {
                    if (completed.compareAndSet(false, true)) {
                        runCatching { context.unregisterReceiver(receiver) }
                    }
                }

                val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    device.deviceId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                runCatching { usbManager.requestPermission(device, pendingIntent) }
                    .onFailure { finish(Result.failure(it)) }
            }
            true
        } ?: false
        if (!completedInTime) throw IllegalStateException("USB设备授权超时: $deviceName")
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDeviceExtra(): UsbDevice? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    
    /**
     * 发送数据
     */
    fun sendMessage(message: String) {
        val port = _currentPort.value
        if (port == null || !_isConnected.value) {
            _errorMessage.value = "串口未连接"
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = "${message}\r\n".toByteArray(Charsets.UTF_8)
                val success = port.write(data)
                if (success) {
                    addMessage("发送", message, MessageType.SENT)
                } else {
                    _errorMessage.value = "发送数据失败"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = "发送数据异常: ${e.message}"
            }
        }
    }
    
    /**
     * 发送数据（sendMessage方法的别名）
     */
    fun sendData(data: String) {
        sendMessage(data)
    }
    
    /**
     * 关闭串口
     */
    fun closePort() {
        portObservationJob?.cancel()
        portObservationJob = null
        _currentPort.value?.close()
        _currentPort.value = null
        _isConnected.value = false
        _messages.value = emptyList()
    }
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * 添加聊天消息
     */
    private fun addMessage(sender: String, content: String, type: MessageType) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val message = ChatMessage(
            id = System.currentTimeMillis(),
            sender = sender,
            content = content,
            timestamp = timestamp,
            type = type
        )
        _messages.value = _messages.value + message
    }
    
    override fun onCleared() {
        super.onCleared()
        closePort()
    }
}

/**
 * 聊天消息数据类
 */
data class ChatMessage(
    val id: Long,
    val sender: String,
    val content: String,
    val timestamp: String,
    val type: MessageType
)

/**
 * 消息类型
 */
enum class MessageType {
    SENT,       // 发送的消息
    RECEIVED,   // 接收的消息
    SYSTEM,     // 系统消息
    ERROR       // 错误消息
}
