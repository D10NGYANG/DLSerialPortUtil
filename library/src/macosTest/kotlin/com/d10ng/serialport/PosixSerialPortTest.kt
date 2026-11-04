@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.d10ng.serialport

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import platform.posix.*
import kotlin.test.*

class PosixSerialPortTest {
    @Test fun idlePortStaysOpenAndHangupClosesIt() = runBlocking {
        val master = posix_openpt(O_RDWR or O_NOCTTY)
        assertTrue(master >= 0)
        var masterOpen = true
        var port: PosixSerialPort? = null
        try {
            assertEquals(0, grantpt(master))
            assertEquals(0, unlockpt(master))
            val path = ptsname(master)!!.toKString()
            port = PosixSerialPort(SerialPortInfo(path), SerialPortConfig())
            port.open()
            delay(50)
            assertTrue(port.openStateFlow.value)
            val bytes = "hello".encodeToByteArray()
            bytes.usePinned { assertEquals(bytes.size.toLong(), write(master, it.addressOf(0), bytes.size.toULong())) }
            assertContentEquals(bytes, withTimeout(2_000) { port.outputDataFlow.first() })
            close(master)
            masterOpen = false
            withTimeout(2_000) { port.openStateFlow.first { !it } }
            port.closeAndAwait()
        } finally {
            port?.closeAndAwait()
            if (masterOpen) close(master)
        }
    }

    @Test fun closeAndReopenDoesNotLeaveOldReaders() = runBlocking {
        val master = posix_openpt(O_RDWR or O_NOCTTY)
        assertTrue(master >= 0)
        var port: PosixSerialPort? = null
        try {
            assertEquals(0, grantpt(master)); assertEquals(0, unlockpt(master))
            port = PosixSerialPort(SerialPortInfo(ptsname(master)!!.toKString()), SerialPortConfig())
            repeat(20) { index ->
                port.open()
                val bytes = byteArrayOf(index.toByte())
                bytes.usePinned { assertEquals(1L, write(master, it.addressOf(0), 1u)) }
                assertContentEquals(bytes, withTimeout(2_000) {
                    port.outputDataFlow.first { it.contentEquals(bytes) }
                })
                // Reopen without joining the old reader, exercising fd reuse.
                port.close()
                assertFalse(port.openStateFlow.value)
            }
        } finally {
            port?.closeAndAwait()
            close(master)
        }
    }
}
