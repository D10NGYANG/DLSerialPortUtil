package com.d10ng.serialport.demo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.d10ng.serialport.demo.viewmodel.ChatMessage
import com.d10ng.serialport.demo.viewmodel.MessageType
import com.d10ng.serialport.demo.viewmodel.SerialPortViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 通讯测试页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationScreen(
    onNavigateHome: () -> Unit,
    viewModel: SerialPortViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // 监听连接状态，断开时自动返回首页
    LaunchedEffect(isConnected) {
        if (!isConnected) {
            onNavigateHome()
        }
    }
    
    // 自动滚动到最新消息
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("串口通讯")
                        Text(
                            text = if (isConnected) "已连接" else "未连接",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isConnected) Color.Green else Color.Red
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.closePort()
                            onNavigateHome()
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "断开串口")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入要发送的数据...") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank() && isConnected) {
                                    viewModel.sendData(inputText)
                                    inputText = ""
                                    keyboardController?.hide()
                                }
                            }
                        ),
                        maxLines = 3
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    FloatingActionButton(
                        onClick = {
                            if (inputText.isNotBlank() && isConnected) {
                                viewModel.sendData(inputText)
                                inputText = ""
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier.size(56.dp),
                        containerColor = if (inputText.isNotBlank() && isConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "发送",
                            tint = if (inputText.isNotBlank() && isConnected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        if (messages.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔌",
                        style = MaterialTheme.typography.displayLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "开始通讯",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "在下方输入框中输入数据并发送",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // 消息列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageItem(message = message)
                }
            }
        }
    }
}

@Composable
private fun MessageItem(message: ChatMessage) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.type == MessageType.SENT) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        if (message.type == MessageType.SENT) {
            Spacer(modifier = Modifier.width(64.dp))
        }
        
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.type == MessageType.SENT) 16.dp else 4.dp,
                bottomEnd = if (message.type == MessageType.SENT) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = when (message.type) {
                    MessageType.SENT -> MaterialTheme.colorScheme.primary
                    MessageType.RECEIVED -> MaterialTheme.colorScheme.surfaceVariant
                    MessageType.ERROR -> MaterialTheme.colorScheme.errorContainer
                    MessageType.SYSTEM -> MaterialTheme.colorScheme.secondaryContainer
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // 消息类型标签
                if (message.type != MessageType.SENT && message.type != MessageType.RECEIVED) {
                    Text(
                        text = when (message.type) {
                            MessageType.ERROR -> "错误"
                            MessageType.SYSTEM -> "系统"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (message.type) {
                            MessageType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                            MessageType.SYSTEM -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                
                // 消息内容
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = when (message.type) {
                        MessageType.SENT -> MaterialTheme.colorScheme.onPrimary
                        MessageType.RECEIVED -> MaterialTheme.colorScheme.onSurfaceVariant
                        MessageType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                        MessageType.SYSTEM -> MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
                
                // 时间戳
                Text(
                    text = dateFormat.format(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (message.type) {
                        MessageType.SENT -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        MessageType.RECEIVED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        MessageType.ERROR -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        MessageType.SYSTEM -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        
        if (message.type != MessageType.SENT) {
            Spacer(modifier = Modifier.width(64.dp))
        }
    }
}