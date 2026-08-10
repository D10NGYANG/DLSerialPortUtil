package com.d10ng.serialport

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.text.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SerialDiagnosticsTest {

    @Test
    fun payloadLogUsesCompleteUppercaseContinuousHex() {
        val payload = ByteArray(4097) { it.toByte() }
        val log = serialPayloadLog("tx", "/dev/ttyUSB0", payload, 7)
        val hex = payload.toHexString(HexFormat.UpperCase)

        assertEquals("[serial.tx] /dev/ttyUSB0 4097B $hex op=7", log)
        assertFalse(hex.contains(' '))
        assertEquals(payload.size * 2, hex.length)
    }

    @Test
    fun payloadLogSupportsEmptyData() {
        assertEquals("[serial.rx] COM3 0B ", serialPayloadLog("rx", "COM3", byteArrayOf()))
    }

    @Test
    fun configLogContainsEverySupportedField() {
        val fields = serialConfigFields(
            SerialPortConfig(BaudRate.V57600, DataBits.V7, Parity.ODD, StopBits.V2)
        )

        assertEquals("baud=57600 data=7 stop=2 parity=O flow=none", fields)
    }

    @Test
    fun disconnectEvidenceRequiresSpecificSignal() {
        assertTrue(IllegalStateException("Bad file descriptor").hasDefinitiveDisconnectEvidence())
        assertFalse(IllegalStateException("temporary read failure").hasDefinitiveDisconnectEvidence())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun dataReceivedBeforeSubscriptionIsReplayedWithinBound() = runTest {
        val port = TestSerialPort()
        repeat(BaseSerialPort.RX_REPLAY_CAPACITY + 1) { port.receive(byteArrayOf(it.toByte())) }

        assertEquals(BaseSerialPort.RX_REPLAY_CAPACITY, port.outputDataFlow.replayCache.size)
        assertEquals(1, port.outputDataFlow.replayCache.first().single().toInt())
        val received = async { port.outputDataFlow.first() }
        assertEquals(1, received.await().single().toInt())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun replayBoundRemainsCorrectAfterSubscriberComesAndGoes() = runTest {
        val port = TestSerialPort()
        repeat(BaseSerialPort.RX_REPLAY_CAPACITY) { port.receive(byteArrayOf(it.toByte())) }
        val collector = launch { port.outputDataFlow.collect {} }
        runCurrent()
        port.receive(byteArrayOf(64))
        collector.cancelAndJoin()

        port.receive(byteArrayOf(65))

        assertEquals(BaseSerialPort.RX_REPLAY_CAPACITY, port.outputDataFlow.replayCache.size)
        assertEquals(2, port.outputDataFlow.replayCache.first().single().toInt())
        assertEquals(65, port.outputDataFlow.replayCache.last().single().toInt())
    }

    private class TestSerialPort : BaseSerialPort(SerialPortInfo("test"), SerialPortConfig()) {
        override val isDtrSupported = false
        override val isRtsSupported = false
        override suspend fun open() = Unit
        override suspend fun setDtr(enabled: Boolean) = false
        override suspend fun setRts(enabled: Boolean) = false
        override suspend fun write(data: ByteArray) = false
        override fun close() = Unit
        suspend fun receive(data: ByteArray) = emitReceived(data)
    }
}
