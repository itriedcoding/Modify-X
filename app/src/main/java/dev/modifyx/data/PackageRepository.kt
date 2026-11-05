package dev.modifyx.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.modifyx.core.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Reads the real package database through [PackageManager] and cross-checks install state
 * with `pm` so that apps removed for the current user stay visible and restorable.
 */
class PackageRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val iconCache = LruCache<String, ImageBitmap>(160)
    private val metaFile = File(context.filesDir, "package-meta.json")

    suspend fun loadSnapshot(): AppSnapshot = withContext(Dispatchers.IO) {
        val privileged = PrivilegeManager.canModify()
        val meta = readMeta()

        val packages = runCatching {
            installedPackages(PackageManager.GET_PERMISSIONS or PackageManager.MATCH_DISABLED_COMPONENTS)
        }.getOrElse {
            return@withContext AppSnapshot(
                privileged = privileged,
                loadedAt = System.currentTimeMillis(),
                warning = "Package manager refused the query: ${it.message}",
            )
        }

        val apps = ArrayList<AppEntry>(packages.size + 16)
        var warning: String? = null
        val usage = if (UsageAccess.hasAccess(context)) UsageAccess.lastUsed(context) else emptyMap()

        for (info in packages) {
            val appInfo = info.applicationInfo ?: continue
            apps += info.toEntry(appInfo, usage[info.packageName] ?: 0L)
        }

        if (privileged) {
            val removed = detectRemovedForUser()
            if (removed == null) {
                warning = "Could not read the package list from the shell; removed apps may be missing."
            } else {
                val present = apps.mapTo(HashSet()) { it.packageName }
                for (pkg in removed) {
                    if (pkg in present) continue
                    val cached = meta[pkg]
                    apps += AppEntry(
                        packageName = pkg,
                        label = cached?.label ?: pkg,
                        isSystem = cached?.system ?: true,
                        isUpdatedSystem = false,
                        enabled = false,
                        installedForUser = false,
                        suspended = false,
                        versionName = null,
                        versionCode = 0,
                        targetSdk = 0,
                        minSdk = 0,
                        firstInstallTime = 0L,
                        lastUpdateTime = 0L,
                        apkBytes = cached?.bytes ?: 0L,
                        uid = -1,
                        launchable = false,
                        installer = null,
                    )
                }
            }
        }

        apps.sortWith(compareBy({ it.label.lowercase() }, { it.packageName }))
        writeMeta(apps)
        AppSnapshot(
            apps = apps,
            loadedAt = System.currentTimeMillis(),
            privileged = privileged,
            warning = warning,
        )
    }

    private fun PackageInfo.toEntry(appInfo: ApplicationInfo, lastUsedAt: Long): AppEntry {
        val flags = appInfo.flags
        val system = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val updatedSystem = (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val bytes = buildList {
            appInfo.sourceDir?.let(::add)
            appInfo.splitSourceDirs?.let { addAll(it) }
        }.sumOf { path -> runCatching { File(path).length() }.getOrDefault(0L) }

        return AppEntry(
            packageName = packageName,
            label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(packageName),
            isSystem = system,
            isUpdatedSystem = updatedSystem,
            enabled = appInfo.enabled,
            installedForUser = true,
            suspended = runCatching { pm.isPackageSuspended(packageName) }.getOrDefault(false),
            versionName = versionName,
            versionCode = versionCodeCompat(),
            targetSdk = appInfo.targetSdkVersion,
            minSdk = appInfo.minSdkVersion,
            firstInstallTime = firstInstallTime,
            lastUpdateTime = lastUpdateTime,
            apkBytes = bytes,
            uid = appInfo.uid,
            launchable = runCatching { pm.getLaunchIntentForPackage(packageName) != null }.getOrDefault(false),
            installer = installerOf(),
            lastUsedAt = lastUsedAt,
        )
    }

    private fun PackageInfo.installerOf(): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(packageName)
        }
    }.getOrNull()

    /** `pm list packages -u` minus `pm list packages` = removed for this user. */
    private suspend fun detectRemovedForUser(): Set<String>? {
        val shell = PrivilegeManager.shell()
        val installed = shell.exec("pm list packages --user 0", 40_000)
        if (!installed.success) return null
        val everything = shell.exec("pm list packages -u --user 0", 40_000)
        if (!everything.success) return null
        val installedSet = parsePackageList(installed.stdout)
        val allSet = parsePackageList(everything.stdout)
        return allSet - installedSet
    }

    private fun parsePackageList(raw: String): Set<String> = raw.lineSequence()
        .mapNotNull { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("package:")) null else trimmed.removePrefix("package:").trim()
        }
        .filter { it.isNotEmpty() }
        .toSet()

    suspend fun loadDetails(pkg: String): AppDetails? = withContext(Dispatchers.IO) {
        val info = queryPackage(
            pkg,
            PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS or
                PackageManager.MATCH_DISABLED_COMPONENTS,
        ) ?: return@withContext null
        val appInfo = info.applicationInfo ?: return@withContext null

        val permissions = info.requestedPermissions?.map { name ->
            PermissionRow(
                name = name,
                granted = runCatching {
                    pm.checkPermission(name, pkg) == PackageManager.PERMISSION_GRANTED
                }.getOrDefault(false),
                dangerous = runCatching {
                    val protection = pm.getPermissionInfo(name, 0).protection
                    (protection and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE) ==
                        android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
                }.getOrDefault(false),
            )
        } ?: emptyList()

        val paths = buildList {
            appInfo.sourceDir?.let(::add)
            appInfo.splitSourceDirs?.let { addAll(it) }
        }

        val dataBytes: Long?
        val cacheBytes: Long?
        if (PrivilegeManager.state.value.mode == dev.modifyx.core.PrivilegeMode.ROOT) {
            dataBytes = directorySizeKb("/data/data/$pkg")
            cacheBytes = directorySizeKb("/data/data/$pkg/cache")
        } else {
            dataBytes = null
            cacheBytes = null
        }

        AppDetails(
            packageName = pkg,
            apkPaths = paths,
            signatureSha256 = signatureHash(info),
            permissions = permissions,
            activities = info.activities?.size ?: 0,
            services = info.services?.size ?: 0,
            receivers = info.receivers?.size ?: 0,
            providers = info.providers?.size ?: 0,
            dataBytes = dataBytes,
            cacheBytes = cacheBytes,
            isDebuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            installedBy = runCatching { info.installerOf() }.getOrNull(),
            processes = processesOf(pkg),
        )
    }

    private suspend fun directorySizeKb(path: String): Long? {
        val result = PrivilegeManager.shell().exec("du -sk $path 2>/dev/null | cut -f1", 15_000)
        if (!result.success) return null
        val kb = result.stdout.trim().toLongOrNull() ?: return null
        return kb * 1024L
    }

    private suspend fun processesOf(pkg: String): List<String> {
        val result = PrivilegeManager.shell().exec("ps -A -o NAME | grep -F \"$pkg\" | head -20", 15_000)
        if (!result.success) return emptyList()
        return result.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private fun signatureHash(info: PackageInfo): String? = runCatching {
        val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        } ?: return@runCatching null
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(":") { "%02X".format(it) }
    }.getOrNull()

    /** The flag-based overloads only exist from API 33; older releases need the int form. */
    fun queryPackage(pkg: String, flags: Int): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, flags)
        }
    }.getOrNull()

    private fun installedPackages(flags: Int): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(flags)
        }

    @Suppress("DEPRECATION")
    private fun PackageInfo.versionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    fun iconFor(pkg: String): ImageBitmap? {
        iconCache.get(pkg)?.let { return it }
        val bitmap = runCatching {
            pm.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap()
        }.getOrNull() ?: return null
        iconCache.put(pkg, bitmap)
        return bitmap
    }

    fun clearIconCache() = iconCache.evictAll()

    // --- lightweight metadata cache so removed apps keep their real names ---

    private data class Meta(val label: String, val system: Boolean, val bytes: Long)

    private fun readMeta(): Map<String, Meta> = runCatching {
        if (!metaFile.exists()) return emptyMap()
        val root = JSONObject(metaFile.readText())
        val map = HashMap<String, Meta>(root.length())
        for (key in root.keys()) {
            val obj = root.optJSONObject(key) ?: continue
            map[key] = Meta(
                label = obj.optString("label", key),
                system = obj.optBoolean("system", true),
                bytes = obj.optLong("bytes", 0L),
            )
        }
        map
    }.getOrDefault(emptyMap())

    private fun writeMeta(apps: List<AppEntry>) {
        runCatching {
            val existing = readMeta()
            val root = JSONObject()
            for (app in apps) {
                val previous = existing[app.packageName]
                val system = if (app.installedForUser) app.isSystem else previous?.system ?: app.isSystem
                root.put(
                    app.packageName,
                    JSONObject()
                        .put("label", app.label)
                        .put("system", system)
                        .put("bytes", app.apkBytes),
                )
            }
            metaFile.writeText(root.toString())
        }
    }
}
