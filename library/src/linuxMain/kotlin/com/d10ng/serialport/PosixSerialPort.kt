package com.d10ng.serialport

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.posix.B115200
import platform.posix.B19200
import platform.posix.B38400
import platform.posix.B57600
import platform.posix.B9600
import platform.posix.CLOCAL
import platform.posix.CREAD
import platform.posix.CS7
import platform.posix.CS8
import platform.posix.CSIZE
import platform.posix.CSTOPB
import platform.posix.EINTR
import platform.posix.F_SETFL
import platform.posix.TCIOFLUSH
import platform.posix.TIOCMBIC
import platform.posix.TIOCMBIS
import platform.posix.TIOCM_DTR
import platform.posix.O_NOCTTY
import platform.posix.O_NONBLOCK
import platform.posix.O_RDWR
import platform.posix.PARENB
import platform.posix.PARODD
import platform.posix.TCSANOW
import platform.posix.VMIN
import platform.posix.VTIME
import platform.posix.cfmakeraw
import platform.posix.cfsetispeed
import platform.posix.cfsetospeed
import platform.posix.close
import platform.posix.errno
import platform.posix.open
import platform.posix.read
import platform.posix.tcflush
import platform.posix.fcntl
import platform.posix.ioctl
import platform.posix.tcgetattr
import platform.posix.tcsetattr
import platform.posix.termios
import platform.posix.write

/**
 * POSIX系统下的串口实现
 * @Author d10ng
 * @Date 2025/9/19 15:28
 */
@OptIn(ExperimentalForeignApi::class)
class PosixSerialPort(
    info: SerialPortInfo,
    config: SerialPortConfig
): BaseSerialPort(info, config) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 串口文件描述符
    private var fd: Int = -1

    override val isDtrSupported: Boolean = true
    
    // 缓存数据
    private val buffer = ByteArray(2048)
    
    // 循环读取数据任务
    private var readJob: Job? = null

    override suspend fun open() {
        if (fd != -1) {
            logger.w { "Serial port [${info.id}] already opened" }
            return
        }

        runCatching {
            logger.d { "open serial port [${info.id}], config: $config" }
            // 打开串口设备
            fd = open(info.id, O_RDWR or O_NOCTTY or O_NONBLOCK)
            if (fd < 0) throw Exception("Failed to open serial port: ${info.id}")
            // 清除非阻塞标志，改为阻塞读写以配合 VMIN/VTIME
            fcntl(fd, F_SETFL, 0)
            
            // 配置串口参数
            configureSerialPort()
            
            // 开始读取数据
            startRead()
            
            openStateFlow.value = true
        }.onFailure { exception ->
            logger.w { "open fail: ${exception.message}" }
            if (fd != -1) {
                close(fd)
                fd = -1
            }
            throw exception
        }
    }

    /**
     * 配置串口参数（使用 cfmakeraw 简化）
     */
    private fun configureSerialPort() {
        memScoped {
            val tty = alloc<termios>()

            // 获取当前配置
            if (tcgetattr(fd, tty.ptr) != 0) {
                throw Exception("Failed to get serial port attributes")
            }

            // 设为原始模式
            cfmakeraw(tty.ptr)

            // 启用接收器，忽略调制解调器控制线
            tty.c_cflag = (tty.c_cflag.toInt() or CREAD or CLOCAL).toUInt()

            // 配置数据位
            tty.c_cflag = (tty.c_cflag.toInt() and CSIZE.inv()).toUInt()
            tty.c_cflag = when (config.dataBits) {
                DataBits.V7 -> (tty.c_cflag.toInt() or CS7).toUInt()
                DataBits.V8 -> (tty.c_cflag.toInt() or CS8).toUInt()
            }

            // 配置停止位
            if (config.stopBits == StopBits.V2) {
                tty.c_cflag = (tty.c_cflag.toInt() or CSTOPB).toUInt()
            }

            // 配置校验位
            tty.c_cflag = when (config.parity) {
                Parity.NONE -> (tty.c_cflag.toInt() and PARENB.inv()).toUInt()
                Parity.EVEN -> (tty.c_cflag.toInt() or PARENB).toUInt()
                Parity.ODD  -> (tty.c_cflag.toInt() or PARENB or PARODD).toUInt()
            }

            // 设置波特率
            val baudRate = when (config.baudRate) {
                BaudRate.V9600   -> B9600
                BaudRate.V19200  -> B19200
                BaudRate.V38400  -> B38400
                BaudRate.V57600  -> B57600
                BaudRate.V115200 -> B115200
            }
            cfsetispeed(tty.ptr, baudRate.toUInt())
            cfsetospeed(tty.ptr, baudRate.toUInt())

            // 设置 VTIME/VMIN
            tty.c_cc[VMIN] = 1u     // 至少读 1 个字节才返回
            tty.c_cc[VTIME] = 0u    // 不使用超时（一直阻塞）

            // 应用配置
            if (tcsetattr(fd, TCSANOW, tty.ptr) != 0) {
                throw Exception("Failed to set serial port attributes")
            }
            // 清空输入输出缓冲区，避免残留数据影响
            tcflush(fd, TCIOFLUSH)
        }
    }


    /**
     * 开始读取数据（阻塞读）
     */
    private fun startRead() {
        readJob = scope.launch {
            loop@ while (isActive && fd != -1) {
                runCatching {
                    val bytesRead = read(fd, buffer.refTo(0), buffer.size.toULong()).toInt()
                    when {
                        bytesRead > 0 -> {
                            val data = buffer.copyOf(bytesRead)
                            logger.d { "RX HEX: ${data.toHexString(HexFormat.UpperCase)}" }
                            logger.d { "RX STR: ${data.decodeToString()}" }
                            outputDataFlow.tryEmit(data)
                        }
                        bytesRead == 0 -> {
                            // 仅在 VMIN=0, VTIME>0 时可能出现超时返回
                            continue@loop
                        }
                        bytesRead == -1 -> {
                            when (errno) {
                                EINTR -> continue@loop      // 被信号打断，重试
                                else -> break@loop          // 真错误，退出
                            }
                        }
                    }
                }.onFailure { exception ->
                    logger.w { "read fail: ${exception.message}" }
                    break@loop
                }
            }
            close() // 清理
        }
    }


    override suspend fun write(data: ByteArray): Boolean {
        if (fd == -1) return false
        return runCatching {
            memScoped {
                logger.d { "TX HEX: ${data.toHexString(HexFormat.UpperCase)}" }
                logger.d { "TX STR: ${data.decodeToString()}" }
                val bytesWritten = write(fd, data.refTo(0), data.size.toULong()).toInt()
                bytesWritten == data.size
            }
        }.onFailure { exception ->
            logger.w { "write fail: ${exception.message}"}
        }.getOrDefault(false)
    }

    override suspend fun setDtr(enabled: Boolean): Boolean {
        if (fd == -1) return false
        return runCatching {
            memScoped {
                val flag = alloc<IntVar>()
                flag.value = TIOCM_DTR
                ioctl(fd, (if (enabled) TIOCMBIS else TIOCMBIC).toULong(), flag.ptr) == 0
            }
        }.onFailure { exception ->
            logger.w { "set DTR to $enabled fail: ${exception.message}" }
        }.getOrDefault(false)
    }

    override fun close() {
        logger.d { "close serial port [${info.id}]" }
        runCatching { readJob?.cancel() }
        if (fd != -1) {
            runCatching { close(fd) }
            fd = -1
        }
        readJob = null
        openStateFlow.value = false
    }
}
