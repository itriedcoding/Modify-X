package dev.modifyx.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Follows github.com/itriedcoding/Modify-X/releases and installs newer builds.
 *
 * A downloaded APK is only offered for installation when it is signed with the project's
 * release key, so a hijacked download cannot replace the app. The release notes come from
 * the published release itself.
 */
object UpdateChecker {

    const val REPO = "itriedcoding/Modify-X"
    const val PACKAGE = "dev.modifyx"
    const val RELEASE_SIGNER_SHA256 = "e0ba35c5485f0ce4d715976ab193332f0c006ce7b929c5de8f3f3df7a23ca035"

    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val LATEST_PAGE = "https://github.com/$REPO/releases/latest"
    private const val ASSET_NAME = "app-release.apk"
    private const val USER_AGENT = "Modify-X-Android"

    data class UpdateInfo(
        val version: String,
        val tag: String,
        val notes: String,
        val downloadUrl: String,
        val publishedAt: String = "",
    )

    data class InstallResult(
        val installed: Boolean,
        val installerShown: Boolean,
        val message: String,
    )

    /** True when [latest] is a strictly newer semantic version than [current]. */
    fun isNewer(latest: String, current: String): Boolean = compareVersions(latest, current) > 0

    fun compareVersions(a: String, b: String): Int {
        val left = parts(a)
        val right = parts(b)
        for (index in 0 until maxOf(left.size, right.size)) {
            val l = left.getOrElse(index) { 0 }
            val r = right.getOrElse(index) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun parts(version: String): List<Int> = version
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('.', '-', '+', '_')
        .mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }
        .take(3)

    /** Latest published release, or null when the page cannot be read. */
    suspend fun latest(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching { fromApi() }.getOrNull() ?: runCatching { fromRedirect() }.getOrNull()
    }

    private fun fromApi(): UpdateInfo? {
        val connection = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode != 200) return null
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = root.optString("tag_name")
            val version = tag.removePrefix("v").removePrefix("V")
            if (version.isBlank()) return null

            val assets = root.optJSONArray("assets")
            var downloadUrl: String? = null
            for (index in 0 until (assets?.length() ?: 0)) {
                val asset = assets?.optJSONObject(index) ?: continue
                if (asset.optString("name") == ASSET_NAME) {
                    downloadUrl = asset.optString("browser_download_url")
                    break
                }
            }
            if (downloadUrl.isNullOrBlank()) {
                downloadUrl = "https://github.com/$REPO/releases/download/$tag/$ASSET_NAME"
            }
            return UpdateInfo(
                version = version,
                tag = tag,
                notes = root.optString("body", ""),
                downloadUrl = downloadUrl,
                publishedAt = root.optString("published_at", ""),
            )
        } finally {
            connection.disconnect()
        }
    }

    /** Fallback that never touches the rate-limited API: read the /latest redirect. */
    private fun fromRedirect(): UpdateInfo? {
        val connection = (URL(LATEST_PAGE).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val location = connection.getHeaderField("Location") ?: return null
            val tag = location.trimEnd('/').substringAfterLast('/')
            val version = tag.removePrefix("v").removePrefix("V")
            if (version.isBlank() || !version.first().isDigit()) return null
            return UpdateInfo(
                version = version,
                tag = tag,
                notes = "",
                downloadUrl = "https://github.com/$REPO/releases/download/$tag/$ASSET_NAME",
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val directory = context.getExternalFilesDir("update") ?: File(context.cacheDir, "update")
        if (!directory.exists()) directory.mkdirs()
        val target = File(directory, "modifyx-${info.version}.apk")
        if (target.isFile && target.length() > 0L) target.delete()

        val connection = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            if (connection.responseCode != 200) {
                error("the release server answered ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (target.length() == 0L) error("the download was empty")
        onProgress(100)
        target
    }

    /** Returns a reason the APK must not be installed, or null when it is safe. */
    fun verify(context: Context, apk: File, installedVersion: String): String? {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
            ?: return "the file is not a readable APK"
        if (info.packageName != PACKAGE) return "unexpected package ${info.packageName}"

        @Suppress("DEPRECATION")
        val signature = info.signatures?.firstOrNull()?.toByteArray() ?: return "the APK is unsigned"
        val digest = MessageDigest.getInstance("SHA-256").digest(signature)
            .joinToString("") { "%02x".format(it) }
        if (!digest.equals(RELEASE_SIGNER_SHA256, ignoreCase = true)) {
            return "the APK is signed by $digest, not the Modify-X release key"
        }
        val version = info.versionName ?: return "the APK has no version name"
        if (!isNewer(version, installedVersion)) return "the downloaded build ($version) is not newer than $installedVersion"
        return null
    }

    suspend fun install(context: Context, apk: File): InstallResult = withContext(Dispatchers.IO) {
        if (PrivilegeManager.canModify()) {
            val result = PrivilegeManager.shell().exec("pm install -r \"${apk.absolutePath}\"", 180_000)
            if (result.success) {
                return@withContext InstallResult(true, false, "Update installed through the privileged shell.")
            }
        }

        val uri: Uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        }.getOrElse { error ->
            return@withContext InstallResult(false, false, "Could not expose the APK to the installer: ${error.message}")
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val launched = runCatching { context.startActivity(intent) }.isSuccess
        if (!launched) {
            return@withContext InstallResult(
                installed = false,
                installerShown = false,
                message = "No installer accepted the update. Allow \"Install unknown apps\" for Modify-X and retry.",
            )
        }
        InstallResult(false, true, "Confirm the update in the system dialog. Modify-X restarts after installing.")
    }
}
