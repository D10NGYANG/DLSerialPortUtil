package com.d10ng.serialport.demo.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.d10ng.serialport.BaudRate
import com.d10ng.serialport.DataBits
import com.d10ng.serialport.Parity
import com.d10ng.serialport.SerialPortInfo
import com.d10ng.serialport.StopBits
import com.d10ng.serialport.demo.viewmodel.SerialPortViewModel

/**
 * 机内串口配置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SerialConfigScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCommunication: () -> Unit,
    viewModel: SerialPortViewModel = viewModel()
) {
    val config by viewModel.config.collectAsState()
    val serialPorts by viewModel.serialPorts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    var selectedPort by remember { mutableStateOf<SerialPortInfo?>(null) }
    var showPortDialog by remember { mutableStateOf(false) }
    
    // 监听连接状态，成功连接后跳转到通讯页面
    val isConnected by viewModel.isConnected.collectAsState()
    LaunchedEffect(isConnected) {
        if (isConnected) {
            onNavigateToCommunication()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("机内串口配置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadSerialPorts() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 串口地址选择
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "串口地址",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        OutlinedButton(
                            onClick = { showPortDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = selectedPort?.id ?: "选择串口地址",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            // 波特率配置
            item {
                ConfigCard(
                    title = "波特率",
                    options = BaudRate.entries.map { "${it.intValue}" },
                    selectedIndex = BaudRate.entries.indexOf(config.baudRate),
                    onSelectionChanged = { index ->
                        viewModel.updateConfig(config.copy(baudRate = BaudRate.entries[index]))
                    }
                )
            }
            
            // 数据位配置
            item {
                ConfigCard(
                    title = "数据位",
                    options = DataBits.entries.map { "${it.intValue}" },
                    selectedIndex = DataBits.entries.indexOf(config.dataBits),
                    onSelectionChanged = { index ->
                        viewModel.updateConfig(config.copy(dataBits = DataBits.entries[index]))
                    }
                )
            }
            
            // 校验位配置
            item {
                ConfigCard(
                    title = "校验位",
                    options = Parity.entries.map { it.text },
                    selectedIndex = Parity.entries.indexOf(config.parity),
                    onSelectionChanged = { index ->
                        viewModel.updateConfig(config.copy(parity = Parity.entries[index]))
                    }
                )
            }
            
            // 停止位配置
            item {
                ConfigCard(
                    title = "停止位",
                    options = StopBits.entries.map { "${it.intValue}" },
                    selectedIndex = StopBits.entries.indexOf(config.stopBits),
                    onSelectionChanged = { index ->
                        viewModel.updateConfig(config.copy(stopBits = StopBits.entries[index]))
                    }
                )
            }
            
            // 打开串口按钮
            item {
                Button(
                    onClick = {
                        selectedPort?.let { port ->
                            viewModel.openSerialPort(port)
                        }
                    },
                    enabled = selectedPort != null && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isLoading) "连接中..." else "打开串口",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
    
    // 串口选择对话框
    if (showPortDialog) {
        AlertDialog(
            onDismissRequest = { showPortDialog = false },
            title = { Text("选择串口") },
            text = {
                LazyColumn {
                    items(serialPorts) { port ->
                        TextButton(
                            onClick = {
                                selectedPort = port
                                showPortDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = port.id,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    
                    if (serialPorts.isEmpty()) {
                        item {
                            Text(
                                text = "未找到可用串口",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPortDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 错误提示
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            // 这里可以显示 SnackBar 或其他错误提示
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigCard(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelectionChanged: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = options.getOrNull(selectedIndex) ?: "",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onSelectionChanged(index)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
