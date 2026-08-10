package com.d10ng.serialport

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SerialPortConnectionStateRegistryTest {

    @Test
    fun repeatedConnectedEventsAreIgnored() {
        val registry = SerialPortConnectionStateRegistry()
        val platformPort = Any()

        assertTrue(registry.markConnected(platformPort, "usb:1:1"))
        assertFalse(registry.markConnected(platformPort, "usb:1:1"))
        assertFalse(registry.markConnected(platformPort, "usb:1:1"))
    }

    @Test
    fun repeatedDisconnectedEventsAreIgnored() {
        val registry = SerialPortConnectionStateRegistry()
        val platformPort = Any()

        assertTrue(registry.markDisconnected(platformPort, "usb:1:1"))
        assertFalse(registry.markDisconnected(platformPort, "usb:1:1"))
        assertFalse(registry.markDisconnected(platformPort, "usb:1:1"))
    }

    @Test
    fun realStateTransitionsAreReported() {
        val registry = SerialPortConnectionStateRegistry()
        val platformPort = Any()

        assertTrue(registry.markConnected(platformPort, "usb:1:1"))
        assertTrue(registry.markDisconnected(platformPort, "usb:1:1"))
        assertTrue(registry.markConnected(platformPort, "usb:1:1"))
    }

    @Test
    fun differentPlatformObjectsHaveIndependentStates() {
        val registry = SerialPortConnectionStateRegistry()
        val firstPort = Any()
        val secondPort = Any()

        assertTrue(registry.markConnected(firstPort, "usb:1:1"))
        assertTrue(registry.markConnected(secondPort, "usb:2:2"))
        assertFalse(registry.markConnected(firstPort, "usb:1:1"))
        assertFalse(registry.markConnected(secondPort, "usb:2:2"))
    }

    @Test
    fun differentWrappersForSameLogicalPortAreMergedWithinWindow() {
        var now = 1_000L
        val registry = SerialPortConnectionStateRegistry(nowMillisProvider = { now })

        assertTrue(registry.markDisconnected(Any(), "usb:6790:29987"))
        now += 2
        assertFalse(registry.markDisconnected(Any(), "usb:6790:29987"))
        now += 2
        assertFalse(registry.markDisconnected(Any(), "usb:6790:29987"))
    }

    @Test
    fun sameLogicalPortCanReportSameStateOutsideDuplicateWindow() {
        var now = 1_000L
        val registry = SerialPortConnectionStateRegistry(nowMillisProvider = { now })

        assertTrue(registry.markConnected(Any(), "usb:6790:29987"))
        now += 101
        assertTrue(registry.markConnected(Any(), "usb:6790:29987"))
    }

    @Test
    fun oldestObjectStateIsEvictedAtCapacity() {
        var now = 1_000L
        val registry = SerialPortConnectionStateRegistry(
            nowMillisProvider = { now },
            maxTrackedObjects = 1,
            maxLogicalPorts = 1
        )
        val firstPort = Any()

        assertTrue(registry.markConnected(firstPort, "port-1"))
        assertTrue(registry.markConnected(Any(), "port-2"))
        now += 101
        assertTrue(registry.markConnected(firstPort, "port-1"))
    }
}
