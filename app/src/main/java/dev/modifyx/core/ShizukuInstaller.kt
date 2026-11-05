package dev.modifyx.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Installs Shizuku from the APK bundled in this app's assets.
 *
 * The bundle is pinned by checksum and signing certificate, so a modified or corrupted
 * asset is refused instead of installed. With a privileged shell available the install
 * is silent; otherwise the system installer is launched with a one-tap confirmation.
 */
object ShizukuInstaller {

    const val PACKAGE = "moe.shizuku.privileged.api"
    const val VERSION = "13.6.0.r1086.2650830c"
    const val APK_SHA256 = "6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f"
    const val SIGNER_SHA256 = "268b5590e868fb08bae7e0ac413564cd1ff88f5ccff74af9dbd0dc918e30db30"

    private const val ASSET = "shizuku.apk"

    data class InstallResult(
        val installed: Boolean,
        val installerShown: Boolean,
        val message: String,
    )

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0) != null
    }.getOrDefault(false)

    fun installedVersion(context: Context): String? = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(PACKAGE, 0).versionName
    }.getOrNull()

    fun launchIntent(context: Context): Intent? =
        runCatching { context.packageManager.getLaunchIntentForPackage(PACKAGE) }.getOrNull()

    fun open(context: Context): Boolean {
        val intent = launchIntent(context) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    suspend fun install(context: Context): InstallResult = withContext(Dispatchers.IO) {
        val staged = runCatching { stage(context) }.getOrElse { error ->
            return@withContext InstallResult(false, false, "Could not stage the bundled Shizuku package: ${error.message}")
        }

        val verification = verify(context, staged)
        if (verification != null) {
            return@withContext InstallResult(false, false, "Bundled Shizuku APK rejected: $verification")
        }

        if (PrivilegeManager.canModify()) {
            val command = "pm install -r \"${staged.absolutePath}\""
            val result = PrivilegeManager.shell().exec(command, 180_000)
            val installedNow = isInstalled(context)
            val message = when {
                installedNow -> "Shizuku $VERSION installed silently through the privileged shell."
                result.success -> "The shell reported success but Shizuku is not visible yet. Open the app list and refresh."
                else -> "Silent install failed: ${result.output.take(300).ifBlank { "exit ${result.exitCode}" }}"
            }
            return@withContext InstallResult(installedNow, false, message)
        }

        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", staged)
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
                false,
                false,
                "No installer accepted the APK. Allow \"Install unknown apps\" for Modify-X, then retry.",
            )
        }
        InstallResult(
            installed = false,
            installerShown = true,
            message = "Confirm the install in the system dialog, then open Shizuku and start the service.",
        )
    }

    /** Copies the asset next to the app so the installer or shell can read it. */
    private fun stage(context: Context): File {
        val dir = context.getExternalFilesDir("shizuku") ?: File(context.cacheDir, "shizuku")
        if (!dir.exists() && !dir.mkdirs()) error("cannot create ${dir.absolutePath}")
        val target = File(dir, "shizuku-$VERSION.apk")
        if (target.isFile && target.length() > 0L && sha256(target) == APK_SHA256) return target

        context.assets.open(ASSET).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        val digest = sha256(target)
        require(digest == APK_SHA256) { "checksum $digest does not match the pinned release" }
        return target
    }

    /** Confirms the archive really is Shizuku, signed by Rikka's release key. */
    private fun verify(context: Context, apk: File): String? {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(
            apk.absolutePath,
            PackageManager.GET_SIGNATURES,
        ) ?: return "the asset is not a readable APK"

        if (info.packageName != PACKAGE) return "unexpected package ${info.packageName}"

        @Suppress("DEPRECATION")
        val signature = info.signatures?.firstOrNull()?.toByteArray()
            ?: return "the APK is unsigned"
        val digest = MessageDigest.getInstance("SHA-256").digest(signature)
            .joinToString("") { "%02x".format(it) }
        if (!digest.equals(SIGNER_SHA256, ignoreCase = true)) {
            return "signer $digest is not the official Shizuku key"
        }
        return null
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
