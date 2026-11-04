@file:OptIn(ExperimentalWasmJsInterop::class)

package com.d10ng.serialport

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class WebSerialPortLifecycleTest {
    @Test fun concurrentOpenWaitsForResourceInstallation() = runTest {
        withContext(Dispatchers.Default) {
            val handle = fakePort()
            val port = WebSerialPort(SerialPortInfo("fake", obj = handle), SerialPortConfig())
            try {
                val first = async { port.open() }
                delay(20)
                val second = async { port.open() }
                delay(20)
                assertFalse(second.isCompleted)
                releaseOpen(handle)
                first.await(); second.await()
                assertTrue(port.openStateFlow.value)
                assertEquals(1, openCount(handle))
            } finally { port.closeAndAwait() }
            assertEquals(1, closeCount(handle))
        }
    }

    @Test fun closeDuringOpenReleasesLateBrowserHandle() = runTest {
        withContext(Dispatchers.Default) {
            val handle = fakePort()
            val port = WebSerialPort(SerialPortInfo("fake", obj = handle), SerialPortConfig())
            val opening = async { runCatching { port.open() } }
            delay(20)
            val closing = async { port.closeAndAwait() }
            delay(20)
            releaseOpen(handle)
            assertTrue(opening.await().isFailure)
            closing.await()
            assertFalse(port.openStateFlow.value)
            assertEquals(1, closeCount(handle))
            port.open()
            assertTrue(port.openStateFlow.value)
            port.closeAndAwait()
            assertEquals(2, closeCount(handle))
        }
    }

    @Test fun cancelledOpenStillReleasesLateHandle() = runTest {
        withContext(Dispatchers.Default) {
            val handle = fakePort()
            val port = WebSerialPort(SerialPortInfo("fake", obj = handle), SerialPortConfig())
            val opening = launch { port.open() }
            delay(20)
            opening.cancel()
            releaseOpen(handle)
            opening.join()
            assertFalse(port.openStateFlow.value)
            assertEquals(1, closeCount(handle))
        }
    }

    @Test fun recoverableErrorReplacesReaderAndReceivesAgain() = runTest {
        withContext(Dispatchers.Default) {
            val handle = fakePort()
            allowOpen(handle)
            val port = WebSerialPort(SerialPortInfo("fake", obj = handle), SerialPortConfig())
            try {
                port.open()
                delay(20)
                replaceErroredStream(handle)
                delay(80)
                feed(handle)
                val data = withTimeout(2_000) { port.outputDataFlow.first() }
                assertEquals(65, data.single().toInt())
                assertTrue(port.openStateFlow.value)
                assertEquals(0, closeCount(handle))
            } finally { port.closeAndAwait() }
            assertEquals(1, closeCount(handle))
        }
    }
}

@JsFun("""() => {
 const p = {connected:true, opened:false, opens:0, closes:0, hold:true};
 p.newReadable = () => { p.readable = new ReadableStream({start(c){p.controller=c;}}); };
 p.open = async () => { p.opens++; if(p.hold) await new Promise(r=>{p.release=()=>{p.hold=false;r();};}); p.opened=true; p.newReadable(); p.writable=new WritableStream({write(){}}); };
 p.close = async () => { if(p.readable?.locked || p.writable?.locked) throw new Error('stream still locked'); p.opened=false; p.closes++; };
 p.setSignals = async () => {};
 p.getInfo = () => ({});
 return p;
}""")
private external fun fakePort(): SerialPort

@JsFun("""(p) => p.release()""")
private external fun releaseOpen(p: SerialPort): Unit

@JsFun("""(p) => { p.hold=false; }""")
private external fun allowOpen(p: SerialPort): Unit

@JsFun("""(p) => p.opens""")
private external fun openCount(p: SerialPort): Int

@JsFun("""(p) => p.closes""")
private external fun closeCount(p: SerialPort): Int

@JsFun("""(p) => { p.controller.error(new Error('parity error')); p.newReadable(); }""")
private external fun replaceErroredStream(p: SerialPort): Unit

@JsFun("""(p) => p.controller.enqueue(new Uint8Array([65]))""")
private external fun feed(p: SerialPort): Unit
