package dev.modifyx

import dev.modifyx.data.DuplicateGrouper
import dev.modifyx.data.FileCategory
import dev.modifyx.data.ScannedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateGroupingTest {

    private fun file(path: String, size: Long, modified: Long = 0L) = ScannedFile(
        uri = "content://doc/$path",
        name = path.substringAfterLast('/'),
        path = path,
        size = size,
        modified = modified,
        category = FileCategory.IMAGE,
        mime = "image/jpeg",
        rootLabel = "Internal",
    )

    @Test
    fun onlyIdenticalHashesAreGrouped() {
        val a = file("Internal/Pictures/a.jpg", 2048)
        val b = file("Internal/Download/b.jpg", 2048)
        val c = file("Internal/Pictures/c.jpg", 2048)
        val groups = DuplicateGrouper.group(listOf(a, b, c), minSize = 1024) { candidate ->
            when (candidate.name) {
                "a.jpg", "b.jpg" -> "same"
                else -> "different"
            }
        }
        assertEquals(1, groups.size)
        assertEquals(setOf("a.jpg", "b.jpg"), groups.first().files.map { it.name }.toSet())
        assertEquals(2048L, groups.first().waste)
    }

    @Test
    fun filesAtOrBelowTheMinimumAreIgnored() {
        val tiny = file("Internal/a.txt", 512)
        val alsoTiny = file("Internal/b.txt", 512)
        val groups = DuplicateGrouper.group(listOf(tiny, alsoTiny), minSize = 1024) { "same" }
        assertTrue(groups.isEmpty())
    }

    @Test
    fun zeroByteFilesAreNeverDuplicates() {
        val one = file("Internal/a", 0)
        val two = file("Internal/b", 0)
        assertTrue(DuplicateGrouper.group(listOf(one, two), minSize = 0) { "same" }.isEmpty())
    }

    @Test
    fun unreadableFilesAreSkippedInsteadOfGuessed() {
        val a = file("Internal/a.jpg", 4096)
        val b = file("Internal/b.jpg", 4096)
        val groups = DuplicateGrouper.group(listOf(a, b), minSize = 1) { null }
        assertTrue(groups.isEmpty())
    }

    @Test
    fun keeperIsTheShortestPathThenTheOldest() {
        val short = file("Internal/a.jpg", 1000, modified = 500)
        val deep = file("Internal/very/deep/folder/b.jpg", 1000, modified = 100)
        val equallyShortButNewer = file("Internal/b.jpg", 1000, modified = 900)
        val group = DuplicateGrouper.group(listOf(deep, equallyShortButNewer, short), minSize = 1) { "same" }.single()
        // Both 14-character paths win over the deep one; the older of the two is the keeper.
        assertEquals("Internal/a.jpg", group.keeper.path)
        assertEquals(2, group.copies.size)
        assertTrue(group.copies.none { it.uri == group.keeper.uri })
    }

    @Test
    fun groupsAreOrderedByReclaimableSpace() {
        val small = listOf(file("Internal/s1", 2000), file("Internal/s2", 2000))
        val big = listOf(
            file("Internal/b1", 9000),
            file("Internal/b2", 9000),
            file("Internal/b3", 9000),
        )
        val groups = DuplicateGrouper.group(small + big, minSize = 1) { it.size.toString() }
        assertEquals(2, groups.size)
        assertEquals(9000L * 2, groups.first().waste)
        assertEquals(2000L, groups.last().waste)
    }
}
