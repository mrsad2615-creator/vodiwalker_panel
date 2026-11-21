package dev.hgallery.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteFormatterTest {

    @Test
    fun `bytes below one kilobyte stay in bytes`() {
        assertEquals("0 B", ByteFormatter.format(0))
        assertEquals("1 B", ByteFormatter.format(1))
        assertEquals("999 B", ByteFormatter.format(999))
        assertEquals("1023 B", ByteFormatter.format(1023))
    }

    @Test
    fun `kilobytes and megabytes keep two decimals`() {
        assertEquals("1.00 KB", ByteFormatter.format(1024))
        assertEquals("1.50 KB", ByteFormatter.format(1536))
        assertEquals("2.50 MB", ByteFormatter.format(2_621_440))
    }

    @Test
    fun `gigabytes and terabytes keep one decimal`() {
        assertEquals("1.0 GB", ByteFormatter.format(1024L * 1024 * 1024))
        assertEquals("1.5 TB", ByteFormatter.format(1024L * 1024 * 1024 * 1536))
    }

    @Test
    fun `negative values are clamped instead of crashing`() {
        assertEquals("0 B", ByteFormatter.format(-5))
    }
}
