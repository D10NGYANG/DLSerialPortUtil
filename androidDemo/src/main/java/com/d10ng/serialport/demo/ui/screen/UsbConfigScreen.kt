package com.d10ng.serialport.demo.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
 * USB串口配置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbConfigScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCommunication: () -> Unit,
    viewModel: SerialPortViewModel = viewModel()
) {
    val config by viewModel.config.collectAsState()
    val usbPorts by viewModel.usbPorts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    var selectedPort by remember { mutableStateOf<SerialPortInfo?>(null) }
    
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
                title = { Text("USB串口配置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadUsbPorts() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新列表")
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
            // USB设备列表
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "USB设备列表",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            TextButton(
                                onClick = { viewModel.loadUsbPorts() },
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text("刷新")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (usbPorts.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "未找到USB串口设备",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "请连接USB串口设备后点击刷新",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        } else {
                            usbPorts.forEach { port ->
                                UsbDeviceItem(
                                    port = port,
                                    isSelected = selectedPort == port,
                                    onSelect = { selectedPort = port }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
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
                            viewModel.openUsbPort(port)
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
    
    // 错误提示
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            // 这里可以显示 SnackBar 或其他错误提示
        }
    }
}

@Composable
private fun UsbDeviceItem(
    port: SerialPortInfo,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            CardDefaults.outlinedCardBorder().copy(
                width = 2.dp,
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
            )
        } else {
            CardDefaults.outlinedCardBorder()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = port.description ?: port.id,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                
                if (port.description != null) {
                    Text(
                        text = port.id,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        }
                    )
                }
            }
            
            if (isSelected) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "已选择",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
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
                        .menuAnchor()
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