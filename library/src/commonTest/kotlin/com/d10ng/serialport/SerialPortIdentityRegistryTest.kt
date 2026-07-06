package com.d10ng.serialport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SerialPortIdentityRegistryTest {

    @Test
    fun samePlatformObjectKeepsIdentity() {
        val registry = SerialPortIdentityRegistry("test")
        val platformPort = Any()

        val first = registry.getOrCreate(platformPort, "first")
        val second = registry.getOrCreate(platformPort, "second")

        assertSame(first, second)
        assertEquals("test-1", first.id)
        assertSame(platformPort, first.obj)
    }

    @Test
    fun differentPlatformObjectsReceiveDifferentIdentities() {
        val registry = SerialPortIdentityRegistry("test")

        val first = registry.getOrCreate(Any(), null)
        val second = registry.getOrCreate(Any(), null)

        assertNotEquals(first.id, second.id)
        assertEquals("test-1", first.id)
        assertEquals("test-2", second.id)
    }

    @Test
    fun removedPlatformObjectReceivesNewIdentityWhenSeenAgain() {
        val registry = SerialPortIdentityRegistry("test")
        val platformPort = Any()
        val first = registry.getOrCreate(platformPort, "serial")

        assertSame(first, registry.remove(platformPort))
        assertNull(registry.remove(platformPort))

        val reconnected = registry.getOrCreate(platformPort, "serial")
        assertNotEquals(first.id, reconnected.id)
        assertEquals("test-2", reconnected.id)
    }
}
