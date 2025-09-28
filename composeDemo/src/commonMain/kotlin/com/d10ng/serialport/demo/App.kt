package com.d10ng.serialport.demo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.d10ng.serialport.BaseSerialPort
import com.d10ng.serialport.BaudRate
import com.d10ng.serialport.DataBits
import com.d10ng.serialport.Parity
import com.d10ng.serialport.SerialPortConfig
import com.d10ng.serialport.SerialPortInfo
import com.d10ng.serialport.StopBits
import com.d10ng.serialport.getPlatformSerialPortManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private enum class MsgType { SYSTEM, SENT, RECEIVED }
private data class Msg(val time: String, val content: String, val type: MsgType)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalTime::class,
    FormatStringsInDatetimeFormats::class
)
@Composable
fun App() {
    MaterialTheme {
        val scope = rememberCoroutineScope { Dispatchers.Default }

        // 串口与配置状态
        var ports by remember { mutableStateOf<List<SerialPortInfo>>(emptyList()) }
        var selectedPort by remember { mutableStateOf<SerialPortInfo?>(null) }

        var baudRate by remember { mutableStateOf(BaudRate.V115200) }
        var dataBits by remember { mutableStateOf(DataBits.V8) }
        var parity by remember { mutableStateOf(Parity.NONE) }
        var stopBits by remember { mutableStateOf(StopBits.V1) }

        // 连接状态
        var connecting by remember { mutableStateOf(false) }
        var connected by remember { mutableStateOf(false) }
        var port: BaseSerialPort? by remember { mutableStateOf(null) }

        // 消息与输入
        val messages = remember { mutableStateListOf<Msg>() }
        var input by remember { mutableStateOf("") }
        val listState = rememberLazyListState()

        val addMsg: (String, MsgType) -> Unit = remember {
            { content, type ->
                val ts = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    .format(LocalDateTime.Format { byUnicodePattern("HH:mm:ss.SSS") })
                messages.add(Msg(ts, content, type))
            }
        }

        val refreshPorts: () -> Unit = remember {
            {
                scope.launch {
                    runCatching { getPlatformSerialPortManager().listPorts() }
                        .onSuccess {
                            ports = it
                            if (selectedPort?.id !in it.map { p -> p.id }) {
                                selectedPort = it.firstOrNull()
                            }
                            if (it.isEmpty()) addMsg("未发现可用串口", MsgType.SYSTEM)
                        }
                        .onFailure { e -> addMsg("获取串口列表失败: ${e.message}", MsgType.SYSTEM) }
                }
            }
        }

        LaunchedEffect(Unit) {
            refreshPorts()
            addMsg("环境准备就绪，可选择串口并连接进行通讯", MsgType.SYSTEM)
        }

        // 监听串口状态与数据
        LaunchedEffect(port) {
            port ?: return@LaunchedEffect
            val p = port!!
            launch {
                p.openStateFlow.collect { isOpen ->
                    connected = isOpen
                    if (!isOpen) {
                        addMsg("串口连接已断开", MsgType.SYSTEM)
                    }
                }
            }
            launch {
                p.outputDataFlow.collect { data ->
                    val text = data.decodeToString()
                    addMsg(text, MsgType.RECEIVED)
                }
            }
        }

        // 新消息自动滚动到底部
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }

        // 窗口关闭时清理资源
        DisposableEffect(Unit) {
            onDispose {
                runCatching { port?.close() }
            }
        }

        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 左侧：串口配置区
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    var portMenuExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = portMenuExpanded,
                        onExpandedChange = { portMenuExpanded = it },
                    ) {
                        val label: String =
                            selectedPort?.description ?: selectedPort?.id ?: "选择串口"
                        TextField(
                            value = label,
                            onValueChange = {},
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryEditable)
                                .fillMaxWidth(),
                            readOnly = true,
                            label = { Text("串口设备") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = portMenuExpanded) }
                        )
                        DropdownMenu(
                            expanded = portMenuExpanded,
                            onDismissRequest = { portMenuExpanded = false }) {
                            ports.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.description ?: p.id) },
                                    onClick = {
                                        selectedPort = p
                                        portMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(
                        onClick = { refreshPorts() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("刷新") }

                    Spacer(Modifier.size(16.dp))

                    // 波特率
                    var baudExpanded by remember { mutableStateOf(false) }
                    val baudItems = listOf(
                        BaudRate.V9600,
                        BaudRate.V19200,
                        BaudRate.V38400,
                        BaudRate.V57600,
                        BaudRate.V115200
                    )
                    ExposedDropdownMenuBox(
                        expanded = baudExpanded,
                        onExpandedChange = { baudExpanded = it }) {
                        TextField(
                            value = baudRate.intValue.toString(),
                            onValueChange = {},
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable)
                                .fillMaxWidth(),
                            readOnly = true,
                            label = { Text("波特率") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = baudExpanded) }
                        )
                        DropdownMenu(
                            expanded = baudExpanded,
                            onDismissRequest = { baudExpanded = false }) {
                            baudItems.forEach { b ->
                                DropdownMenuItem(
                                    text = { Text(b.intValue.toString()) },
                                    onClick = { baudRate = b; baudExpanded = false })
                            }
                        }
                    }

                    Spacer(Modifier.size(8.dp))

                    // 数据位
                    var dataBitsExpanded by remember { mutableStateOf(false) }
                    val dataBitsItems = listOf(DataBits.V7, DataBits.V8)
                    ExposedDropdownMenuBox(
                        expanded = dataBitsExpanded,
                        onExpandedChange = { dataBitsExpanded = it }) {
                        TextField(
                            value = dataBits.intValue.toString(),
                            onValueChange = {},
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable)
                                .fillMaxWidth(),
                            readOnly = true,
                            label = { Text("数据位") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dataBitsExpanded) }
                        )
                        DropdownMenu(
                            expanded = dataBitsExpanded,
                            onDismissRequest = { dataBitsExpanded = false }) {
                            dataBitsItems.forEach { d ->
                                DropdownMenuItem(
                                    text = { Text(d.intValue.toString()) },
                                    onClick = { dataBits = d; dataBitsExpanded = false })
                            }
                        }
                    }

                    Spacer(Modifier.size(8.dp))

                    // 校验位
                    var parityExpanded by remember { mutableStateOf(false) }
                    val parityItems = listOf(Parity.NONE, Parity.EVEN, Parity.ODD)
                    ExposedDropdownMenuBox(
                        expanded = parityExpanded,
                        onExpandedChange = { parityExpanded = it }) {
                        TextField(
                            value = when (parity) {
                                Parity.NONE -> "None"; Parity.EVEN -> "Even"; Parity.ODD -> "Odd"
                            },
                            onValueChange = {},
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable)
                                .fillMaxWidth(),
                            readOnly = true,
                            label = { Text("校验位") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = parityExpanded) }
                        )
                        DropdownMenu(
                            expanded = parityExpanded,
                            onDismissRequest = { parityExpanded = false }) {
                            parityItems.forEach { p ->
                                DropdownMenuItem(text = {
                                    Text(
                                        when (p) {
                                            Parity.NONE -> "None"; Parity.EVEN -> "Even"; Parity.ODD -> "Odd"
                                        }
                                    )
                                }, onClick = { parity = p; parityExpanded = false })
                            }
                        }
                    }

                    Spacer(Modifier.size(8.dp))

                    // 停止位
                    var stopExpanded by remember { mutableStateOf(false) }
                    val stopItems = listOf(StopBits.V1, StopBits.V2)
                    ExposedDropdownMenuBox(
                        expanded = stopExpanded,
                        onExpandedChange = { stopExpanded = it }) {
                        TextField(
                            value = when (stopBits) {
                                StopBits.V1 -> "1"; StopBits.V2 -> "2"
                            },
                            onValueChange = {},
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable)
                                .fillMaxWidth(),
                            readOnly = true,
                            label = { Text("停止位") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stopExpanded) }
                        )
                        DropdownMenu(
                            expanded = stopExpanded,
                            onDismissRequest = { stopExpanded = false }) {
                            stopItems.forEach { s ->
                                DropdownMenuItem(text = {
                                    Text(
                                        when (s) {
                                            StopBits.V1 -> "1"; StopBits.V2 -> "2"
                                        }
                                    )
                                }, onClick = { stopBits = s; stopExpanded = false })
                            }
                        }
                    }

                    Spacer(Modifier.size(16.dp))

                    // 连接/断开
                    Button(
                        onClick = {
                            if (connected) {
                                runCatching { port?.close() }
                                port = null
                                connected = false
                                addMsg("已断开串口连接", MsgType.SYSTEM)
                            } else {
                                val target = selectedPort
                                if (target == null) {
                                    addMsg("请先选择串口设备", MsgType.SYSTEM)
                                    return@Button
                                }
                                scope.launch {
                                    connecting = true
                                    runCatching {
                                        val cfg = SerialPortConfig(baudRate, dataBits, parity, stopBits)
                                        val p = getPlatformSerialPortManager().open(target, cfg)
                                        port = p
                                        addMsg("串口连接成功", MsgType.SYSTEM)
                                    }.onFailure { e ->
                                        addMsg("连接串口失败: ${e.message}", MsgType.SYSTEM)
                                    }
                                    connecting = false
                                }
                            }
                        },
                        enabled = !connecting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnimatedVisibility(connecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            Text(if (connected) "断开串口" else if (connecting) "连接中..." else "连接串口")
                        }
                    }
                }
            }
            // 中间分隔线
            VerticalDivider(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxHeight(),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            // 右侧：通讯测试区
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 消息区（减少嵌套：移除内部 Surface，直接在 LazyColumn 上做圆角与背景）
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(16.dp)
                    ) {
                        items(messages) { m ->
                            val (bg, fg) = when (m.type) {
                                MsgType.SENT -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                                MsgType.RECEIVED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                                MsgType.SYSTEM -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = if (m.type == MsgType.SENT) Alignment.End else Alignment.Start
                            ) {
                                Text(
                                    m.time,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    m.content,
                                    color = fg,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(bg)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 发送区
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("输入要发送的内容，回车在设备端为\\r\\n") },
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.size(12.dp))
                        Button(
                            onClick = {
                                val msg = input.trim()
                                if (msg.isEmpty()) return@Button
                                val p = port
                                if (p == null || !connected) {
                                    addMsg("串口未连接", MsgType.SYSTEM)
                                    return@Button
                                }
                                scope.launch {
                                    val ok = runCatching {
                                        p.write((msg + "\r\n").encodeToByteArray())
                                    }.getOrDefault(false)
                                    if (ok) {
                                        addMsg(msg, MsgType.SENT)
                                        input = ""
                                    } else {
                                        addMsg("发送数据失败", MsgType.SYSTEM)
                                    }
                                }
                            },
                            enabled = connected,
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("发送") }
                    }
                }
            }
        }
    }
}