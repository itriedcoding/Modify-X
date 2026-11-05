package dev.modifyx

import dev.modifyx.core.ShizukuInstaller
import dev.modifyx.data.StandbyBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Guards the bundled Shizuku APK and the standby-bucket mapping the UI relies on. */
class BundledAssetsTest {

    private fun asset(): File? = listOf(
        File("src/main/assets/shizuku.apk"),
        File("app/src/main/assets/shizuku.apk"),
    ).firstOrNull { it.isFile }

    @Test
    fun bundledShizukuApkMatchesThePinnedChecksum() {
        val apk = asset()
        assertNotNull("app/src/main/assets/shizuku.apk is missing", apk)
        val digest = MessageDigest.getInstance("SHA-256")
        apk!!.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        assertEquals(ShizukuInstaller.APK_SHA256, actual)
        assertTrue("the bundled APK looks truncated", apk.length() > 2_000_000L)
    }

    @Test
    fun standbyBucketsMapBackFromTheirPlatformValues() {
        StandbyBucket.entries.forEach { bucket ->
            assertEquals(bucket, StandbyBucket.from(bucket.value))
        }
        assertEquals(null, StandbyBucket.from(null))
        assertEquals(null, StandbyBucket.from(1234))
        assertEquals(StandbyBucket.RARE, StandbyBucket.from(40))
        assertTrue(StandbyBucket.NEVER.value > StandbyBucket.RESTRICTED.value)
    }
}
