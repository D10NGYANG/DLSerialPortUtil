package com.d10ng.serialport

import com.d10ng.serialport.demo.Utf8StreamDecoder
import kotlin.test.Test
import kotlin.test.assertEquals

class Utf8StreamDecoderTest {
    @Test fun everySplitPreservesText() {
        val text = "AT 中文😀\r\n"
        val bytes = text.encodeToByteArray()
        for (split in 0..bytes.size) {
            val decoder = Utf8StreamDecoder()
            assertEquals(text, decoder.decode(bytes.copyOfRange(0, split)) +
                decoder.decode(bytes.copyOfRange(split, bytes.size)) + decoder.finish())
        }
        val decoder = Utf8StreamDecoder()
        assertEquals(text, bytes.joinToString("") { decoder.decode(byteArrayOf(it)) } + decoder.finish())
    }
    @Test fun incompleteAndInvalidInputIsNotLost() {
        val decoder = Utf8StreamDecoder()
        assertEquals("", decoder.decode(byteArrayOf(0xE4.toByte())))
        assertEquals("\uFFFD", decoder.finish())
        assertEquals("A", decoder.decode(byteArrayOf(65)))
        assertEquals("\uFFFD", decoder.decode(byteArrayOf(0xFF.toByte())))
    }
}
