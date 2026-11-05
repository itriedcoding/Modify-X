package dev.modifyx.data

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class BackupEntry(
    val packageName: String,
    val label: String,
    val state: String,
    val system: Boolean,
) {
    val isRemoved: Boolean get() = state == STATE_REMOVED
    val isDisabled: Boolean get() = state == STATE_DISABLED

    companion object {
        const val STATE_INSTALLED = "installed"
        const val STATE_REMOVED = "removed"
        const val STATE_DISABLED = "disabled"
    }
}

data class BackupDocument(
    val schema: Int,
    val createdAt: Long,
    val device: String,
    val androidRelease: String,
    val entries: List<BackupEntry>,
) {
    val removedCount: Int get() = entries.count { it.isRemoved }
    val disabledCount: Int get() = entries.count { it.isDisabled }
}

data class SnapshotFile(val file: File, val createdAt: Long, val removed: Int, val disabled: Int)

/**
 * Builds and reads portable JSON snapshots of the debloat state, so a profile can be
 * recreated on another ROM or after a factory reset.
 */
class BackupManager(private val context: Context) {

    private val dir = File(context.filesDir, "snapshots").apply { mkdirs() }

    fun saveLocal(document: BackupDocument): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(document.createdAt))
        val file = File(dir, "modifyx-$stamp.json")
        file.writeText(toJson(document))
        return file
    }

    fun listLocal(): List<SnapshotFile> = dir.listFiles { file -> file.extension == "json" }
        ?.sortedByDescending { it.lastModified() }
        ?.mapNotNull { file ->
            runCatching {
                val doc = parse(file.readText())
                SnapshotFile(file, doc.createdAt, doc.removedCount, doc.disabledCount)
            }.getOrNull()
        }
        ?: emptyList()

    fun readLocal(file: File): BackupDocument = parse(file.readText())

    fun deleteLocal(file: File) {
        runCatching { file.delete() }
    }

    companion object {
        const val SCHEMA = 1

        fun buildDocument(
            apps: List<AppEntry>,
            device: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidRelease: String = Build.VERSION.RELEASE ?: "unknown",
            createdAt: Long = System.currentTimeMillis(),
        ): BackupDocument = BackupDocument(
            schema = SCHEMA,
            createdAt = createdAt,
            device = device,
            androidRelease = androidRelease,
            entries = apps.map { app ->
                BackupEntry(
                    packageName = app.packageName,
                    label = app.label,
                    state = when {
                        app.removedForUser -> BackupEntry.STATE_REMOVED
                        app.disabled -> BackupEntry.STATE_DISABLED
                        else -> BackupEntry.STATE_INSTALLED
                    },
                    system = app.isSystem,
                )
            },
        )

        fun toJson(document: BackupDocument): String {
            val array = JSONArray()
            document.entries.forEach { entry ->
                array.put(
                    JSONObject()
                        .put("package", entry.packageName)
                        .put("label", entry.label)
                        .put("state", entry.state)
                        .put("system", entry.system),
                )
            }
            return JSONObject()
                .put("app", "Modify-X")
                .put("schema", document.schema)
                .put("createdAt", document.createdAt)
                .put("device", document.device)
                .put("android", document.androidRelease)
                .put("entries", array)
                .toString(2)
        }

        fun parse(raw: String): BackupDocument {
            val json = JSONObject(raw)
            val schema = json.optInt("schema", 0)
            require(schema == SCHEMA) { "Unsupported backup schema: $schema" }
            val array = json.optJSONArray("entries") ?: throw IllegalArgumentException("Backup has no entries")
            val entries = buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val pkg = obj.optString("package").ifBlank { continue }
                    val state = obj.optString("state").takeIf { it.isNotBlank() } ?: BackupEntry.STATE_INSTALLED
                    add(
                        BackupEntry(
                            packageName = pkg,
                            label = obj.optString("label", pkg),
                            state = state,
                            system = obj.optBoolean("system", true),
                        ),
                    )
                }
            }
            return BackupDocument(
                schema = schema,
                createdAt = json.optLong("createdAt", 0L),
                device = json.optString("device", "unknown"),
                androidRelease = json.optString("android", "unknown"),
                entries = entries,
            )
        }

        /**
         * Packages the backup marks as removed that are not installed right now.
         * These are the ones `pm install-existing` can bring back.
         */
        fun restoreCandidates(document: BackupDocument, current: List<AppEntry>): List<BackupEntry> {
            val installed = current.filter { it.installedForUser }.mapTo(HashSet()) { it.packageName }
            return document.entries.filter { it.isRemoved && it.packageName !in installed }
        }

        /**
         * Packages the backup marks as removed but that are installed right now —
         * re-applying these recreates the backed-up debloat state.
         */
        fun reapplyCandidates(document: BackupDocument, current: List<AppEntry>): List<BackupEntry> {
            val installed = current.filter { it.installedForUser }.mapTo(HashSet()) { it.packageName }
            return document.entries.filter { it.isRemoved && it.packageName in installed }
        }
    }
}
