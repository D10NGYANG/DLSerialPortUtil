@file:OptIn(ExperimentalForeignApi::class)

package com.d10ng.serialport.cli

import com.d10ng.serialport.BaudRate
import com.d10ng.serialport.DataBits
import com.d10ng.serialport.Parity
import com.d10ng.serialport.PosixSerialPortManager
import com.d10ng.serialport.SerialPortConfig
import com.d10ng.serialport.SerialPortInfo
import com.d10ng.serialport.StopBits
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.posix.ECHO
import platform.posix.ICANON
import platform.posix.IEXTEN
import platform.posix.ISIG
import platform.posix.O_RDONLY
import platform.posix.STDIN_FILENO
import platform.posix.TCSANOW
import platform.posix.VMIN
import platform.posix.VTIME
import platform.posix.close
import platform.posix.fflush
import platform.posix.memcpy
import platform.posix.open
import platform.posix.read
import platform.posix.stdout
import platform.posix.tcgetattr
import platform.posix.tcsetattr
import platform.posix.termios

private const val MAX_LOG_BUFFER = 200
private const val MAX_RECENT_LOG_LINES = 30

fun main() = runBlocking {
    Terminal.init()
    try {
        Terminal.hideCursor()
        val portInfo = selectPort()
        val config = selectConfig()
        runChat(portInfo, config)
    } finally {
        Terminal.showCursor()
        Terminal.restore()
    }
}

private suspend fun selectPort(): SerialPortInfo {
    var selected = 1
    while (true) {
        val ports = withContext(Dispatchers.Default) { PosixSerialPortManager.listPorts() }
        val items = listOf("刷新") + ports.map { it.id + (it.description?.let { d -> "  -  $d" } ?: "") }
        val idx = chooseFromList(
            title = "请选择串口（↑/↓ 选择，Enter 确认）",
            items = items,
            selectedIndex = selected.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        )
        if (idx == 0) {
            selected = 1
            continue
        }
        if (idx in 1 until items.size) {
            return ports[idx - 1]
        }
        selected = 1
    }
}

private fun selectConfig(): SerialPortConfig {
    var cfg = SerialPortConfig()
    run {
        val items = BaudRate.entries.map { "${it.name.removePrefix("V")} (${it.intValue})" }
        val idx = chooseFromList("请选择波特率", items, BaudRate.entries.indexOf(cfg.baudRate))
        cfg = cfg.copy(baudRate = BaudRate.entries[idx])
    }
    run {
        val items = DataBits.entries.map { it.intValue.toString() }
        val idx = chooseFromList("请选择数据位", items, DataBits.entries.indexOf(cfg.dataBits))
        cfg = cfg.copy(dataBits = DataBits.entries[idx])
    }
    run {
        val items = Parity.entries.map { "${it.name} (${it.text})" }
        val idx = chooseFromList("请选择校验位", items, Parity.entries.indexOf(cfg.parity))
        cfg = cfg.copy(parity = Parity.entries[idx])
    }
    run {
        val items = StopBits.entries.map { it.intValue.toString() }
        val idx = chooseFromList("请选择停止位", items, StopBits.entries.indexOf(cfg.stopBits))
        cfg = cfg.copy(stopBits = StopBits.entries[idx])
    }
    return cfg
}

private suspend fun runChat(portInfo: SerialPortInfo, config: SerialPortConfig) {
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val logs = mutableListOf<String>()
    val logMutex = Mutex()
    val maxLog = MAX_LOG_BUFFER
    val port = PosixSerialPortManager.open(portInfo, config)
    suspend fun appendLog(line: String) {
        logMutex.withLock {
            logs.add(line)
            if (logs.size > maxLog) {
                repeat(logs.size - maxLog) { logs.removeAt(0) }
            }
        }
    }
    val jobRecv = scope.launch {
        port.outputDataFlow.collect { data ->
            val preview = data.decodeToString()
            appendLog("RX | $preview")
            val snapshot = logMutex.withLock { logs.toList() }
            renderChat(portInfo, config, snapshot, "")
        }
    }
    val inputBuffer = StringBuilder()
    run {
        val snapshot = logMutex.withLock { logs.toList() }
        renderChat(portInfo, config, snapshot, inputBuffer.toString())
    }
    while (true) {
        val ch = Terminal.readKey()
        when (ch) {
            Key.ENTER -> {
                if (inputBuffer.isNotEmpty()) {
                    val text = "${inputBuffer}\r\n"
                    val bytes = text.encodeToByteArray()
                    if (withContext(Dispatchers.Default) { port.write(bytes) }) {
                        appendLog("TX | $text")
                        inputBuffer.clear()
                    }
                }
                val snapshot = logMutex.withLock { logs.toList() }
                renderChat(portInfo, config, snapshot, inputBuffer.toString())
            }
            Key.BACKSPACE -> {
                if (inputBuffer.isNotEmpty()) inputBuffer.deleteAt(inputBuffer.lastIndex)
                val snapshot = logMutex.withLock { logs.toList() }
                renderChat(portInfo, config, snapshot, inputBuffer.toString())
            }
            Key.CTRL_C -> {
                break
            }
            is Key.CharKey -> {
                inputBuffer.append(ch.ch)
                val snapshot = logMutex.withLock { logs.toList() }
                renderChat(portInfo, config, snapshot, inputBuffer.toString())
            }
            else -> {}
        }
    }
    jobRecv.cancelAndJoin()
    port.close()
}

private fun chooseFromList(title: String, items: List<String>, selectedIndex: Int = 0): Int {
    var idx = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    renderList(title, items, idx)
    while (true) {
        when (val key = Terminal.readKey()) {
            Key.UP -> { if (items.isNotEmpty()) idx = (idx - 1 + items.size) % items.size; renderList(title, items, idx) }
            Key.DOWN -> { if (items.isNotEmpty()) idx = (idx + 1) % items.size; renderList(title, items, idx) }
            Key.ENTER -> return idx
            is Key.CharKey -> {
                when (key.ch) {
                    'j' -> { if (items.isNotEmpty()) idx = (idx + 1) % items.size; renderList(title, items, idx) }
                    'k' -> { if (items.isNotEmpty()) idx = (idx - 1 + items.size) % items.size; renderList(title, items, idx) }
                    else -> {}
                }
            }
            else -> {}
        }
    }
}

private fun renderList(title: String, items: List<String>, selectedIndex: Int) {
    Terminal.clear()
    println(title)
    println("------------------------------")
    if (items.isEmpty()) {
        println("(无可用项)")
        return
    }
    items.forEachIndexed { index, item ->
        if (index == selectedIndex) {
            println("\u001B[7m> $item\u001B[0m")
        } else {
            println("  $item")
        }
    }
    println("\n提示：'j'/'k' 也可移动；Enter 确认；首项为“刷新”")
    fflush(stdout)
}

private fun renderChat(portInfo: SerialPortInfo, config: SerialPortConfig, logs: List<String>, input: String) {
    Terminal.clear()
    println("串口: ${portInfo.id}")
    println("参数: ${config.baudRate.intValue} ${config.dataBits.intValue}${config.parity.charValue}${config.stopBits.intValue}")
    println("------------------------------ 日志 (Ctrl+C 退出) ------------------------------")
    val recent = if (logs.size > MAX_RECENT_LOG_LINES) logs.takeLast(MAX_RECENT_LOG_LINES) else logs
    recent.forEach { println(it) }
    println("--------------------------------------------------------------------------------")
    print("> $input")
    fflush(stdout)
}

@OptIn(ExperimentalForeignApi::class)
private object Terminal {
    private var orig: CPointer<termios>? = null
    private var ttyFd: Int = -1
    fun init() {
        ttyFd = runCatching { open("/dev/tty", O_RDONLY) }.getOrDefault(-1)
        val fd = if (ttyFd >= 0) ttyFd else STDIN_FILENO
        memScoped {
            val t = alloc<termios>()
            tcgetattr(fd, t.ptr)
            val saved = nativeHeap.alloc<termios>()
            memcpy(saved.ptr, t.ptr, sizeOf<termios>().convert())
            orig = saved.ptr
            val raw = t
            raw.c_lflag = (raw.c_lflag.toInt() and ECHO.inv() and ICANON.inv() and ISIG.inv() and IEXTEN.inv()).toUInt()
            raw.c_cc[VMIN] = 1u
            raw.c_cc[VTIME] = 0u
            tcsetattr(fd, TCSANOW, raw.ptr)
        }
    }
    fun restore() {
        val fd = if (ttyFd >= 0) ttyFd else STDIN_FILENO
        orig?.let { o ->
            tcsetattr(fd, TCSANOW, o)
            nativeHeap.free(o)
            orig = null
        }
        if (ttyFd >= 0) {
            runCatching { close(ttyFd) }
            ttyFd = -1
        }
    }
    fun clear() {
        print("\u001B[2J\u001B[H")
        fflush(stdout)
    }
    fun hideCursor() { print("\u001B[?25l"); fflush(stdout) }
    fun showCursor() { print("\u001B[?25h"); fflush(stdout) }
    fun readKey(): Key {
        val fd = if (ttyFd >= 0) ttyFd else STDIN_FILENO
        val buf = ByteArray(3)
        val n = buf.usePinned { pinned ->
            read(fd, pinned.addressOf(0), 1.convert())
        }.toInt()
        if (n <= 0) return Key.NONE
        val c = buf[0].toInt() and 0xFF
        return when (c) {
            3 -> Key.CTRL_C
            10, 13 -> Key.ENTER
            127 -> Key.BACKSPACE
            27 -> {
                val n2 = buf.usePinned { pinned ->
                    read(fd, pinned.addressOf(1), 2.convert())
                }.toInt()
                if (n2 >= 2 && buf[1] == '['.code.toByte()) {
                    return when (buf[2].toInt()) {
                        'A'.code -> Key.UP
                        'B'.code -> Key.DOWN
                        else -> Key.NONE
                    }
                }
                Key.NONE
            }
            else -> {
                if (c in 32..126) Key.CharKey(c.toChar()) else Key.NONE
            }
        }
    }
}

private sealed class Key {
    data object NONE: Key()
    data object ENTER: Key()
    data object BACKSPACE: Key()
    data object UP: Key()
    data object DOWN: Key()
    data object CTRL_C: Key()
    data class CharKey(val ch: Char): Key()
}