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

private fun fakePort(): dynamic = js("""(function() {
 var p = {connected:true, opened:false, opens:0, closes:0, hold:true};
 p.newReadable = function() { p.readable = new ReadableStream({start:function(c){p.controller=c;}}); };
 p.open = function() {
   p.opens++;
   var ready = p.hold ? new Promise(function(r){p.release=function(){p.hold=false;r();};}) : Promise.resolve();
   return ready.then(function(){p.opened=true; p.newReadable(); p.writable=new WritableStream({write:function(){}});});
 };
 p.close = function() {
   if((p.readable && p.readable.locked) || (p.writable && p.writable.locked)) return Promise.reject(new Error('stream still locked'));
   p.opened=false; p.closes++; return Promise.resolve();
 };
 p.setSignals = function() {return Promise.resolve();};
 p.getInfo = function() {return {};};
 return p;
})()""")
private fun releaseOpen(p: dynamic) { p.release() }
private fun allowOpen(p: dynamic) { p.hold = false }
private fun openCount(p: dynamic): Int = p.opens as Int
private fun closeCount(p: dynamic): Int = p.closes as Int
private fun replaceErroredStream(p: dynamic) {
    p.controller.error(js("new Error('parity error')"))
    p.newReadable()
}
private fun feed(p: dynamic) { p.controller.enqueue(js("new Uint8Array([65])")) }
