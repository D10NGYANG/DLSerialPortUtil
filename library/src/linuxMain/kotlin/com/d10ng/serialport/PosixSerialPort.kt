package com.d10ng.serialport

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.posix.B115200
import platform.posix.B19200
import platform.posix.B38400
import platform.posix.B57600
import platform.posix.B9600
import platform.posix.CLOCAL
import platform.posix.CREAD
import platform.posix.CRTSCTS
import platform.posix.CS7
import platform.posix.CS8
import platform.posix.CSIZE
import platform.posix.CSTOPB
import platform.posix.EAGAIN
import platform.posix.EINTR
import platform.posix.EWOULDBLOCK
import platform.posix.F_SETFL
import platform.posix.O_NOCTTY
import platform.posix.O_NONBLOCK
import platform.posix.O_RDWR
import platform.posix.PARENB
import platform.posix.PARODD
import platform.posix.TCIOFLUSH
import platform.posix.TCSANOW
import platform.posix.TIOCMBIC
import platform.posix.TIOCMBIS
import platform.posix.TIOCM_DTR
import platform.posix.TIOCM_RTS
import platform.posix.VMIN
import platform.posix.VTIME
import platform.posix.cfmakeraw
import platform.posix.cfsetispeed
import platform.posix.cfsetospeed
import platform.posix.close
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.ioctl
import platform.posix.open
import platform.posix.read
import platform.posix.tcflush
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
    private val lifecycleMutex = Mutex()
    private val writeMutex = Mutex()

    private var fd: Int = -1
    private var readJob: Job? = null
    private var generation = 0L

    override val isDtrSupported: Boolean = true
    override val isRtsSupported: Boolean = true

    override suspend fun open() {
        lifecycleMutex.withLock {
            if (fd != -1) {
                logger.w { "Serial port [${info.id}] already opened" }
                return@withLock
            }

            var openedFd = -1
            runCatching {
                logger.d { "open serial port [${info.id}], config: $config" }
                openedFd = open(info.id, O_RDWR or O_NOCTTY or O_NONBLOCK)
                check(openedFd >= 0) { "Failed to open serial port: ${info.id}" }
                check(fcntl(openedFd, F_SETFL, 0) == 0) {
                    "Failed to switch serial port to blocking mode"
                }
                configureSerialPort(openedFd)

                fd = openedFd
                generation += 1
                startRead(openedFd, generation)
            }.onFailure { exception ->
                logger.w { "open fail: ${exception.message}" }
                if (openedFd != -1) close(openedFd)
                if (fd == openedFd) {
                    generation += 1
                    fd = -1
                    readJob = null
                }
                openStateFlow.value = false
                throw exception
            }
        }
    }

    private fun configureSerialPort(portFd: Int) {
        memScoped {
            val tty = alloc<termios>()
            check(tcgetattr(portFd, tty.ptr) == 0) {
                "Failed to get serial port attributes"
            }
            cfmakeraw(tty.ptr)
            tty.c_cflag = ((tty.c_cflag.toInt() or CREAD or CLOCAL) and CRTSCTS.toInt().inv()).toUInt()

            tty.c_cflag = (tty.c_cflag.toInt() and CSIZE.inv()).toUInt()
            tty.c_cflag = when (config.dataBits) {
                DataBits.V7 -> (tty.c_cflag.toInt() or CS7).toUInt()
                DataBits.V8 -> (tty.c_cflag.toInt() or CS8).toUInt()
            }

            tty.c_cflag = when (config.stopBits) {
                StopBits.V1 -> (tty.c_cflag.toInt() and CSTOPB.inv()).toUInt()
                StopBits.V2 -> (tty.c_cflag.toInt() or CSTOPB).toUInt()
            }

            tty.c_cflag = when (config.parity) {
                Parity.NONE -> (tty.c_cflag.toInt() and (PARENB or PARODD).inv()).toUInt()
                Parity.EVEN -> ((tty.c_cflag.toInt() or PARENB) and PARODD.inv()).toUInt()
                Parity.ODD -> (tty.c_cflag.toInt() or PARENB or PARODD).toUInt()
            }

            val baudRate = when (config.baudRate) {
                BaudRate.V9600 -> B9600
                BaudRate.V19200 -> B19200
                BaudRate.V38400 -> B38400
                BaudRate.V57600 -> B57600
                BaudRate.V115200 -> B115200
            }
            check(cfsetispeed(tty.ptr, baudRate.toUInt()) == 0) {
                "Failed to set serial port input speed"
            }
            check(cfsetospeed(tty.ptr, baudRate.toUInt()) == 0) {
                "Failed to set serial port output speed"
            }

            tty.c_cc[VMIN] = 0u
            tty.c_cc[VTIME] = 1u
            check(tcsetattr(portFd, TCSANOW, tty.ptr) == 0) {
                "Failed to set serial port attributes"
            }
            check(tcflush(portFd, TCIOFLUSH) == 0) {
                "Failed to flush serial port buffers"
            }
        }
    }

    private fun startRead(portFd: Int, token: Long) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val buffer = ByteArray(2048)
            try {
                loop@ while (isActive && isCurrent(portFd, token)) {
                    val bytesRead = buffer.usePinned { pinned ->
                        read(portFd, pinned.addressOf(0), buffer.size.toULong())
                    }.toInt()
                    when {
                        bytesRead > 0 -> {
                            val data = buffer.copyOf(bytesRead)
                            logger.d { "RX HEX: ${data.toHexString(HexFormat.UpperCase)}" }
                            logger.d { "RX STR: ${data.decodeToString()}" }
                            outputDataFlow.emit(data)
                        }
                        bytesRead == 0 -> continue@loop
                        errno == EINTR -> continue@loop
                        errno == EAGAIN || errno == EWOULDBLOCK -> {
                            delay(1)
                            continue@loop
                        }
                        else -> break@loop
                    }
                }
            } finally {
                closeIfCurrent(portFd, token)
            }
        }
        if (fd == portFd && generation == token) {
            readJob = job
            openStateFlow.value = true
            job.start()
        } else {
            job.cancel()
        }
    }

    override suspend fun write(data: ByteArray): Boolean = writeMutex.withLock {
        val portFd = fd
        val token = generation
        if (portFd == -1) return@withLock false
        if (data.isEmpty()) return@withLock true

        logger.d { "TX HEX: ${data.toHexString(HexFormat.UpperCase)}" }
        logger.d { "TX STR: ${data.decodeToString()}" }
        var offset = 0
        while (offset < data.size && isCurrent(portFd, token)) {
            val bytesWritten = data.usePinned { pinned ->
                write(portFd, pinned.addressOf(offset), (data.size - offset).toULong())
            }.toInt()
            when {
                bytesWritten > 0 -> offset += bytesWritten
                bytesWritten == -1 && errno == EINTR -> continue
                bytesWritten == -1 && (errno == EAGAIN || errno == EWOULDBLOCK) -> delay(1)
                else -> {
                    logger.w { "write fail: errno=$errno" }
                    return@withLock false
                }
            }
        }
        offset == data.size
    }

    override suspend fun setDtr(enabled: Boolean): Boolean = writeMutex.withLock {
        val portFd = fd
        if (portFd == -1) return@withLock false
        runCatching {
            memScoped {
                val flag = alloc<IntVar>()
                flag.value = TIOCM_DTR
                ioctl(portFd, (if (enabled) TIOCMBIS else TIOCMBIC).toULong(), flag.ptr) == 0
            }
        }.onFailure { exception ->
            logger.w { "set DTR to $enabled fail: ${exception.message}" }
        }.getOrDefault(false)
    }

    override suspend fun setRts(enabled: Boolean): Boolean = writeMutex.withLock {
        val portFd = fd
        if (portFd == -1) return@withLock false
        runCatching {
            memScoped {
                val flag = alloc<IntVar>()
                flag.value = TIOCM_RTS
                ioctl(portFd, (if (enabled) TIOCMBIS else TIOCMBIC).toULong(), flag.ptr) == 0
            }
        }.onFailure { exception ->
            logger.w { "set RTS to $enabled fail: ${exception.message}" }
        }.getOrDefault(false)
    }

    override fun close() {
        logger.d { "close serial port [${info.id}]" }
        runBlocking {
            lifecycleMutex.withLock {
                writeMutex.withLock { closeInternal() }
            }
        }
    }

    override suspend fun closeAndAwait() {
        val job = lifecycleMutex.withLock {
            writeMutex.withLock { closeInternal() }
        }
        job?.join()
    }

    private fun closeInternal(): Job? {
        generation += 1
        val job = readJob
        val portFd = fd
        readJob = null
        fd = -1
        openStateFlow.value = false
        job?.cancel()
        if (portFd != -1) runCatching { close(portFd) }
        return job
    }

    private fun isCurrent(portFd: Int, token: Long): Boolean =
        fd == portFd && generation == token

    private suspend fun closeIfCurrent(portFd: Int, token: Long) {
        lifecycleMutex.withLock {
            writeMutex.withLock {
                if (isCurrent(portFd, token)) closeInternal()
            }
        }
    }
}
