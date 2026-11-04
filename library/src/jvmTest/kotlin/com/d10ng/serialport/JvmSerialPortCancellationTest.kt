package com.d10ng.serialport

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class JvmSerialPortCancellationTest {
    @Test fun cancellationBeforeDispatcherReturnClosesUnclaimedPort() = runTest {
        assumeTrue(System.getProperty("os.name").let { it.contains("Mac") || it.contains("Linux") })
        val process = ProcessBuilder("python3", "-u", "-c", """
            import os, sys
            master, slave = os.openpty()
            try:
                print(os.ttyname(slave), flush=True)
                sys.stdin.read()
            finally:
                os.close(slave)
                os.close(master)
        """.trimIndent()).start()
        var port: JvmSerialPort? = null
        try {
            val path = process.inputStream.bufferedReader().readLine()
            assertTrue(path.startsWith("/dev/"))
            val handle = SerialPort.getCommPort(path)
            port = JvmSerialPort(SerialPortInfo(path, obj = handle), SerialPortConfig())
            val opening = launch { port.open() }
            runCurrent()
            // Hold the caller dispatcher until IO has installed the resource, then cancel its return.
            val deadline = System.nanoTime() + 2_000_000_000L
            while (!port.openStateFlow.value && System.nanoTime() < deadline) Thread.sleep(1)
            assertTrue(port.openStateFlow.value)
            opening.cancel()
            opening.join()
            assertFalse(port.openStateFlow.value)
            assertFalse(handle.isOpen)
        } finally {
            port?.closeAndAwait()
            process.outputStream.close()
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
        }
    }
}
