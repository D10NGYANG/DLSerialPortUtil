package com.d10ng.serialport

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SerialOperationTest {

    @Test
    fun writesOnSamePortAreSerializedAndOrdered() = runTest {
        val releaseFirst = CompletableDeferred<Unit>()
        val entered = mutableListOf<Long>()
        val port = FakeSerialPort { _, operationId ->
            entered += operationId
            if (operationId == 1L) releaseFirst.await()
            true
        }

        val first = async { port.write(byteArrayOf(1)) }
        val second = async { port.write(byteArrayOf(2)) }
        runCurrent()
        assertEquals(listOf(1L), entered)

        releaseFirst.complete(Unit)
        advanceUntilIdle()
        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(listOf(1L, 2L), entered)
        assertEquals(1, port.maxConcurrentWrites)
    }

    @Test
    fun differentPortsDoNotShareAWriteLock() = runTest {
        val release = CompletableDeferred<Unit>()
        var entered = 0
        val behavior: suspend (ByteArray, Long) -> Boolean = { _, _ ->
            entered += 1
            release.await()
            true
        }
        val firstPort = FakeSerialPort(behavior)
        val secondPort = FakeSerialPort(behavior)

        val first = async { firstPort.write(byteArrayOf(1)) }
        val second = async { secondPort.write(byteArrayOf(2)) }
        runCurrent()
        assertEquals(2, entered)

        release.complete(Unit)
        assertTrue(first.await())
        assertTrue(second.await())
    }

    @Test
    fun cancellationReleasesWriteLock() = runTest {
        var attempts = 0
        val port = FakeSerialPort { _, _ ->
            attempts += 1
            if (attempts == 1) awaitCancellation()
            true
        }

        val cancelled = async { port.write(byteArrayOf(1)) }
        runCurrent()
        cancelled.cancelAndJoin()

        assertTrue(port.write(byteArrayOf(2)))
        assertEquals(2, attempts)
    }

    @Test
    fun timeoutReleasesWriteLock() = runTest {
        var attempts = 0
        val port = FakeSerialPort { _, _ ->
            attempts += 1
            if (attempts == 1) awaitCancellation()
            true
        }

        val timedOut = withTimeoutOrNull(100) { port.write(byteArrayOf(1)) }
        assertNull(timedOut)
        assertTrue(port.write(byteArrayOf(2)))
    }

    @Test
    fun writeFailureDoesNotCloseConnection() = runTest {
        var attempts = 0
        val port = FakeSerialPort { _, _ -> ++attempts > 1 }
        port.open()

        assertFalse(port.write(byteArrayOf(1)))
        assertTrue(port.openStateFlow.value)
        assertTrue(port.write(byteArrayOf(2)))
        assertTrue(port.openStateFlow.value)

        port.disconnect()
        assertFalse(port.openStateFlow.value)
    }

    private class FakeSerialPort(
        private val behavior: suspend (ByteArray, Long) -> Boolean
    ) : BaseSerialPort(SerialPortInfo("fake"), SerialPortConfig()) {
        private var concurrentWrites = 0
        var maxConcurrentWrites = 0
            private set

        override val isDtrSupported = false
        override val isRtsSupported = false

        override suspend fun open() {
            openStateFlow.value = true
        }

        override suspend fun setDtr(enabled: Boolean) = false
        override suspend fun setRts(enabled: Boolean) = false

        override suspend fun write(data: ByteArray): Boolean = withWriteOperation(data.size) { operationId ->
            concurrentWrites += 1
            maxConcurrentWrites = maxOf(maxConcurrentWrites, concurrentWrites)
            try {
                behavior(data, operationId)
            } finally {
                concurrentWrites -= 1
            }
        }

        override fun close() {
            openStateFlow.value = false
        }

        fun disconnect() {
            openStateFlow.value = false
        }
    }
}
