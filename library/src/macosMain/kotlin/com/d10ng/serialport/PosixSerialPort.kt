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
import platform.posix.CCTS_OFLOW
import platform.posix.CREAD
import platform.posix.CRTS_IFLOW
import platform.posix.CS7
import platform.posix.CS8
import platform.posix.CSIZE
import platform.posix.CSTOPB
import platform.posix.EAGAIN
import platform.posix.EACCES
import platform.posix.EBADF
import platform.posix.EINTR
import platform.posix.EIO
import platform.posix.ENODEV
import platform.posix.EPERM
import platform.posix.EWOULDBLOCK
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
import platform.posix.ioctl
import platform.posix.open
import platform.posix.read
import platform.posix.tcflush
import platform.posix.tcgetattr
import platform.posix.tcsetattr
import platform.posix.termios
import platform.posix.write
import kotlin.time.TimeSource

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

    companion object {
        private const val WRITE_TIMEOUT_MILLIS = 2_000L
    }

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
                logger.i { "[serial.open] ${info.id} ${serialConfigFields(config)}" }
                openedFd = open(info.id, O_RDWR or O_NOCTTY or O_NONBLOCK)
                if (openedFd < 0 && (errno == EACCES || errno == EPERM)) {
                    throw IllegalStateException(
                        "Serial port [${info.id}] access denied errno=$errno; configure device-file permissions"
                    )
                }
                check(openedFd >= 0) { "Serial port [${info.id}] open failed errno=$errno" }
                configureSerialPort(openedFd)

                fd = openedFd
                generation += 1
                startRead(openedFd, generation)
                logger.i { "[serial.open.ok] ${info.id} fd=$openedFd" }
            }.onFailure { exception ->
                logger.w { "[serial.open.fail] ${info.id} fd=$openedFd error=${exception.serialContext()} errno=$errno" }
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
            tty.c_cflag = (
                (tty.c_cflag.toInt() or CREAD or CLOCAL) and
                    (CCTS_OFLOW or CRTS_IFLOW).inv()
                ).toULong()

            tty.c_cflag = (tty.c_cflag.toInt() and CSIZE.inv()).toULong()
            tty.c_cflag = when (config.dataBits) {
                DataBits.V7 -> (tty.c_cflag.toInt() or CS7).toULong()
                DataBits.V8 -> (tty.c_cflag.toInt() or CS8).toULong()
            }

            tty.c_cflag = when (config.stopBits) {
                StopBits.V1 -> (tty.c_cflag.toInt() and CSTOPB.inv()).toULong()
                StopBits.V2 -> (tty.c_cflag.toInt() or CSTOPB).toULong()
            }

            tty.c_cflag = when (config.parity) {
                Parity.NONE -> (tty.c_cflag.toInt() and (PARENB or PARODD).inv()).toULong()
                Parity.EVEN -> ((tty.c_cflag.toInt() or PARENB) and PARODD.inv()).toULong()
                Parity.ODD -> (tty.c_cflag.toInt() or PARENB or PARODD).toULong()
            }

            val baudRate = when (config.baudRate) {
                BaudRate.V9600 -> B9600
                BaudRate.V19200 -> B19200
                BaudRate.V38400 -> B38400
                BaudRate.V57600 -> B57600
                BaudRate.V115200 -> B115200
            }
            check(cfsetispeed(tty.ptr, baudRate.toULong()) == 0) {
                "Failed to set serial port input speed"
            }
            check(cfsetospeed(tty.ptr, baudRate.toULong()) == 0) {
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
            var disconnectReason: String? = null
            logger.i { "[serial.read.start] ${info.id} fd=$portFd" }
            try {
                loop@ while (isActive && isCurrent(portFd, token)) {
                    val bytesRead = buffer.usePinned { pinned ->
                        read(portFd, pinned.addressOf(0), buffer.size.toULong())
                    }.toInt()
                    when {
                        bytesRead > 0 -> {
                            val data = buffer.copyOf(bytesRead)
                            emitReceived(data)
                        }
                        bytesRead == 0 -> continue@loop
                        errno == EINTR -> continue@loop
                        errno == EAGAIN || errno == EWOULDBLOCK -> {
                            delay(1)
                            continue@loop
                        }
                        errno == EBADF || errno == ENODEV || errno == EIO -> {
                            disconnectReason = "errno=$errno"
                            break@loop
                        }
                        else -> {
                            logger.w { "[serial.read.fail] ${info.id} fd=$portFd errno=$errno action=retry" }
                            delay(50)
                        }
                    }
                }
            } finally {
                logger.i {
                    "[serial.read.stop] ${info.id} fd=$portFd reason=${disconnectReason ?: "cancelled-or-replaced"}"
                }
                disconnectReason?.let { closeIfCurrent(portFd, token, it) }
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

    override suspend fun write(data: ByteArray): Boolean = withWriteOperation(data.size) { operationId ->
        writeMutex.withLock {
            val portFd = fd
            val token = generation
            if (portFd == -1) {
                logger.w { "[serial.write.fail] ${info.id} op=$operationId reason=not-open" }
                return@withLock false
            }

            logger.d { "[serial.write.start] ${info.id} fd=$portFd timeout=${WRITE_TIMEOUT_MILLIS}ms op=$operationId" }
            logger.d { serialPayloadLog("tx", info.id, data, operationId) }
            if (data.isEmpty()) {
                logger.d { "[serial.write.ok] ${info.id} 0B op=$operationId" }
                return@withLock true
            }
            var offset = 0
            val started = TimeSource.Monotonic.markNow()
            while (offset < data.size && isCurrent(portFd, token)) {
                if (started.elapsedNow().inWholeMilliseconds >= WRITE_TIMEOUT_MILLIS) {
                    logger.w {
                        "[serial.write.timeout] ${info.id} fd=$portFd written=$offset " +
                            "expected=${data.size} op=$operationId"
                    }
                    return@withLock false
                }
                val bytesWritten = data.usePinned { pinned ->
                    write(portFd, pinned.addressOf(offset), (data.size - offset).toULong())
                }.toInt()
                when {
                    bytesWritten > 0 -> offset += bytesWritten
                    bytesWritten == -1 && errno == EINTR -> continue
                    bytesWritten == -1 && (errno == EAGAIN || errno == EWOULDBLOCK) -> delay(1)
                    else -> {
                        logger.w {
                            "[serial.write.fail] ${info.id} fd=$portFd written=$offset " +
                                "expected=${data.size} errno=$errno op=$operationId"
                        }
                        return@withLock false
                    }
                }
            }
            val completed = offset == data.size
            if (completed) {
                logger.d { "[serial.write.ok] ${info.id} ${data.size}B op=$operationId" }
            } else {
                logger.w { "[serial.write.cancel] ${info.id} written=$offset expected=${data.size} op=$operationId" }
            }
            completed
        }
    }

    override suspend fun setDtr(enabled: Boolean): Boolean = writeMutex.withLock {
        val portFd = fd
        if (portFd == -1) return@withLock false
        runCatching {
            memScoped {
                val flag = alloc<IntVar>()
                flag.value = TIOCM_DTR
                ioctl(portFd, if (enabled) TIOCMBIS else TIOCMBIC, flag.ptr) == 0
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
                ioctl(portFd, if (enabled) TIOCMBIS else TIOCMBIC, flag.ptr) == 0
            }
        }.onFailure { exception ->
            logger.w { "set RTS to $enabled fail: ${exception.message}" }
        }.getOrDefault(false)
    }

    override fun close() {
        logger.i { "[serial.close] ${info.id} reason=caller" }
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
        logger.i { "[serial.cleanup] ${info.id} fd=$portFd state=closed" }
        return job
    }

    private fun isCurrent(portFd: Int, token: Long): Boolean =
        fd == portFd && generation == token

    private suspend fun closeIfCurrent(portFd: Int, token: Long, reason: String) {
        lifecycleMutex.withLock {
            writeMutex.withLock {
                if (isCurrent(portFd, token)) {
                    logger.w { "[serial.disconnect] ${info.id} fd=$portFd evidence=$reason" }
                    closeInternal()
                }
            }
        }
    }
}
