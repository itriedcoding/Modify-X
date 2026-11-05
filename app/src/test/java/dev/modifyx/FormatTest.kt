package dev.modifyx

import dev.modifyx.ui.formatBytes
import dev.modifyx.ui.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `bytes are formatted with sensible units`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 MB", formatBytes(1024L * 1024L * 3 / 2))
        assertEquals("2.0 GB", formatBytes(2L * 1024L * 1024L * 1024L))
    }

    @Test
    fun `durations are formatted for humans`() {
        assertEquals("250ms", formatDuration(250))
        assertEquals("1.5s", formatDuration(1500))
        assertEquals("1m 5s", formatDuration(65_000))
    }
}
