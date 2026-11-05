package dev.modifyx

import dev.modifyx.data.StandbyBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandbyBucketTest {

    @Test
    fun everyBucketMapsBackFromItsPlatformValue() {
        StandbyBucket.entries.forEach { bucket ->
            assertEquals(bucket, StandbyBucket.from(bucket.value))
        }
    }

    @Test
    fun unknownValuesReportUnknown() {
        assertNull(StandbyBucket.from(null))
        assertNull(StandbyBucket.from(1234))
        assertNull(StandbyBucket.from(0))
    }

    @Test
    fun bucketsAreOrderedFromMostToLeastRestricted() {
        assertEquals(StandbyBucket.RARE, StandbyBucket.from(40))
        assertTrue(StandbyBucket.NEVER.value > StandbyBucket.RESTRICTED.value)
        assertTrue(StandbyBucket.EXEMPTED.value < StandbyBucket.ACTIVE.value)
    }
}
