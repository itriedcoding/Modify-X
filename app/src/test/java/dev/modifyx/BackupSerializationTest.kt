package dev.modifyx

import dev.modifyx.data.AppEntry
import dev.modifyx.data.BackupEntry
import dev.modifyx.data.BackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializationTest {

    private fun app(
        pkg: String,
        installed: Boolean = true,
        enabled: Boolean = true,
        system: Boolean = true,
        label: String = pkg,
    ) = AppEntry(
        packageName = pkg,
        label = label,
        isSystem = system,
        isUpdatedSystem = false,
        enabled = enabled,
        installedForUser = installed,
        suspended = false,
        versionName = "1.0",
        versionCode = 1L,
        targetSdk = 34,
        minSdk = 26,
        firstInstallTime = 0L,
        lastUpdateTime = 0L,
        apkBytes = 1024L,
        uid = 10000,
        launchable = true,
        installer = null,
    )

    @Test
    fun `document maps package states correctly`() {
        val apps = listOf(
            app("com.example.installed"),
            app("com.example.removed", installed = false),
            app("com.example.disabled", enabled = false),
        )
        val document = BackupManager.buildDocument(apps, device = "Test Device", androidRelease = "15")

        assertEquals(BackupManager.SCHEMA, document.schema)
        assertEquals("Test Device", document.device)
        assertEquals(1, document.removedCount)
        assertEquals(1, document.disabledCount)
        assertEquals(
            BackupEntry.STATE_REMOVED,
            document.entries.first { it.packageName == "com.example.removed" }.state,
        )
        assertEquals(
            BackupEntry.STATE_DISABLED,
            document.entries.first { it.packageName == "com.example.disabled" }.state,
        )
        assertEquals(
            BackupEntry.STATE_INSTALLED,
            document.entries.first { it.packageName == "com.example.installed" }.state,
        )
    }

    @Test
    fun `json survives a full round trip`() {
        val document = BackupManager.buildDocument(
            listOf(
                app("com.example.a", label = "Alpha & <Beta>"),
                app("com.example.b", installed = false, label = "Gamma"),
            ),
            device = "Pixel 9",
            androidRelease = "16",
            createdAt = 1_700_000_000_000L,
        )

        val restored = BackupManager.parse(BackupManager.toJson(document))

        assertEquals(document.schema, restored.schema)
        assertEquals(document.createdAt, restored.createdAt)
        assertEquals(document.device, restored.device)
        assertEquals(document.androidRelease, restored.androidRelease)
        assertEquals(document.entries, restored.entries)
        assertTrue(restored.entries.any { it.label == "Alpha & <Beta>" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown schema is rejected`() {
        BackupManager.parse("""{"schema":99,"entries":[]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing entries is rejected`() {
        BackupManager.parse("""{"schema":1}""")
    }

    @Test
    fun `restore candidates are removed in the backup and missing now`() {
        // At backup time both packages were removed for user 0.
        val document = BackupManager.buildDocument(
            listOf(
                app("com.example.present", installed = false),
                app("com.example.missing", installed = false),
            ),
        )
        // Now only com.example.present is installed again.
        val current = listOf(
            app("com.example.present"),
            app("com.example.missing", installed = false),
        )

        val restorable = BackupManager.restoreCandidates(document, current)
        val reapply = BackupManager.reapplyCandidates(document, current)

        assertEquals(listOf("com.example.missing"), restorable.map { it.packageName })
        assertEquals(listOf("com.example.present"), reapply.map { it.packageName })
    }
}
