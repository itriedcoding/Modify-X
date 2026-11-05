package dev.modifyx.data

import dev.modifyx.core.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies an installed package's APKs to Download/Modify-X before the user removes it.
 * Only the shell can read /data/app, so this needs root or the ADB bridge.
 */
class ApkExporter {

    data class Result(
        val success: Boolean,
        val message: String,
        val files: List<String> = emptyList(),
    )

    suspend fun export(pkg: String): Result = withContext(Dispatchers.IO) {
        if (!PrivilegeManager.canModify()) {
            return@withContext Result(false, "Exporting APKs needs root or the ADB bridge; nothing was copied.")
        }

        val shell = PrivilegeManager.shell()
        val paths = shell.exec("pm path $pkg", 20_000).stdout
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (paths.isEmpty()) {
            return@withContext Result(false, "The package manager reported no APK path for $pkg.")
        }

        val directory = "/sdcard/Download/Modify-X"
        shell.exec("mkdir -p \"$directory\"", 15_000)

        val saved = ArrayList<String>(paths.size)
        paths.forEachIndexed { index, source ->
            val name = if (index == 0) "$pkg.apk" else "$pkg-split$index.apk"
            val target = "$directory/$name"
            shell.exec("cp \"$source\" \"$target\"", 60_000)
            val size = shell.exec("stat -c %s \"$target\"", 15_000).stdout.trim().toLongOrNull() ?: 0L
            if (size > 0L) saved += target
        }

        if (saved.isEmpty()) {
            Result(false, "Nothing was copied. The shell may be unable to read /data/app on this build.")
        } else {
            Result(true, "Copied ${saved.size} file(s) to Download/Modify-X.", saved)
        }
    }
}
