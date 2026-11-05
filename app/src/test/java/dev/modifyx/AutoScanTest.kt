package dev.modifyx

import dev.modifyx.data.AutoScanPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoScanTest {

    private val hour = 60L * 60_000L

    @Test
    fun disabledNeverScans() {
        assertFalse(AutoScanPolicy.isDue(lastScanAt = 0L, now = 100 * hour, intervalHours = 6, enabled = false))
    }

    @Test
    fun firstRunIsDueImmediately() {
        assertTrue(AutoScanPolicy.isDue(lastScanAt = 0L, now = hour, intervalHours = 6, enabled = true))
    }

    @Test
    fun freshResultIsNotDue() {
        assertFalse(AutoScanPolicy.isDue(lastScanAt = hour, now = 5 * hour, intervalHours = 6, enabled = true))
        assertFalse(AutoScanPolicy.isDue(lastScanAt = 5 * hour, now = 5 * hour, intervalHours = 6, enabled = true))
    }

    @Test
    fun staleResultIsDue() {
        assertTrue(AutoScanPolicy.isDue(lastScanAt = hour, now = 7 * hour, intervalHours = 6, enabled = true))
    }

    @Test
    fun intervalIsClampedToAUsableRange() {
        assertEquals(hour, AutoScanPolicy.intervalMillis(0))
        assertEquals(hour, AutoScanPolicy.intervalMillis(-5))
        assertEquals(168 * hour, AutoScanPolicy.intervalMillis(9_999))
        assertFalse(AutoScanPolicy.isDue(hour, hour + hour / 2, intervalHours = 0, enabled = true))
        assertTrue(AutoScanPolicy.isDue(hour, hour + hour, intervalHours = 0, enabled = true))
    }

    @Test
    fun futureTimestampsNeverTriggerAScan() {
        assertFalse(AutoScanPolicy.isDue(lastScanAt = 10 * hour, now = hour, intervalHours = 1, enabled = true))
    }
}
