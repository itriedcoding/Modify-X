package dev.modifyx

import dev.modifyx.data.FileCategory
import dev.modifyx.data.JunkRules
import dev.modifyx.data.ScannedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class JunkRulesTest {

    private fun file(name: String, size: Long = 1024L, path: String = "Internal/Download/$name") = ScannedFile(
        uri = "content://doc/$path",
        name = name,
        path = path,
        size = size,
        modified = 0L,
        category = FileCategory.of(name, null),
        mime = null,
        rootLabel = "Internal",
    )

    @Test
    fun temporaryExtensionsMatchRegardlessOfCase() {
        assertEquals("temp", JunkRules.ruleFor(file("photo.TMP"))?.id)
        assertEquals("temp", JunkRules.ruleFor(file("movie.mp4.part"))?.id)
        assertEquals("temp", JunkRules.ruleFor(file("download.crdownload"))?.id)
    }

    @Test
    fun logAndCrashFilesMatch() {
        assertEquals("logs", JunkRules.ruleFor(file("app.log"))?.id)
        assertEquals("logs", JunkRules.ruleFor(file("hs_err_pid1234.log"))?.id)
        assertEquals("logs", JunkRules.ruleFor(file("dump.hprof"))?.id)
    }

    @Test
    fun editorBackupsMatchByNameToo() {
        assertEquals("backups", JunkRules.ruleFor(file("notes.md.bak"))?.id)
        assertEquals("backups", JunkRules.ruleFor(file("notes.md~"))?.id)
        assertEquals("backups", JunkRules.ruleFor(file("settings.OLD"))?.id)
    }

    @Test
    fun desktopMetadataMatchesExactly() {
        assertEquals("os-metadata", JunkRules.ruleFor(file("Thumbs.db"))?.id)
        assertEquals("os-metadata", JunkRules.ruleFor(file("desktop.ini"))?.id)
        assertEquals("os-metadata", JunkRules.ruleFor(file(".DS_Store"))?.id)
        assertNull(JunkRules.ruleFor(file("my-thumbs.db.txt")))
    }

    @Test
    fun emptyFilesRuleIgnoresNomedia() {
        val empty = file("blank.txt", size = 0L)
        assertEquals("empty-files", JunkRules.ruleFor(empty)?.id)
        assertNull(JunkRules.ruleFor(file(".nomedia", size = 0L)))
        // A zero-byte image still matches the empty rule, which is the honest classification.
        assertEquals("empty-files", JunkRules.ruleFor(file("photo.jpg", size = 0L))?.id)
    }

    @Test
    fun thumbnailRuleMatchesFolderSegmentsOnly() {
        val inside = file("1.jpg", path = "Internal/Pictures/MyApp/.thumbnails/1.jpg")
        assertEquals("thumbnails", JunkRules.ruleFor(inside)?.id)

        val unrelated = file("1.jpg", path = "Internal/Pictures/thumbnails.jpg")
        assertNull(JunkRules.ruleFor(unrelated))
    }

    @Test
    fun ordinaryFilesAreNeverJunk() {
        listOf("holiday.jpg", "report.pdf", "song.mp3", "backup.zip", "notes.txt").forEach { name ->
            assertNull("$name should not be junk", JunkRules.ruleFor(file(name)))
        }
    }

    @Test
    fun classificationHonoursDisabledRules() {
        val files = listOf(file("a.tmp"), file("b.log"), file("holiday.jpg"))
        val allEnabled = JunkRules.classify(files, JunkRules.all.map { it.id }.toSet())
        assertEquals(2, allEnabled.size)

        val withoutLogs = JunkRules.classify(files, JunkRules.all.map { it.id }.toSet() - "logs")
        assertEquals(1, withoutLogs.size)
        assertEquals("temp", withoutLogs.first().ruleId)
        assertNotNull(withoutLogs.first().file.uri)
    }
}
