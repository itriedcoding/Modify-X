package dev.modifyx.data

/**
 * A junk rule matches on the real file name and path only. Nothing is guessed from content:
 * if a file does not match a rule it is never offered for deletion.
 */
data class JunkRule(
    val id: String,
    val label: String,
    val description: String,
    val safeByDefault: Boolean,
    private val extensions: Set<String> = emptySet(),
    private val exactNames: Set<String> = emptySet(),
    private val namePrefixes: Set<String> = emptySet(),
    private val nameSuffixes: Set<String> = emptySet(),
    private val pathSegments: Set<String> = emptySet(),
    private val emptyFilesOnly: Boolean = false,
) {
    fun matches(file: ScannedFile): Boolean {
        val name = file.name.lowercase()
        val extension = name.substringAfterLast('.', "")
        if (emptyFilesOnly) return file.size == 0L
        if (extension.isNotEmpty() && extension in extensions) return true
        if (name in exactNames) return true
        if (namePrefixes.any { name.startsWith(it) }) return true
        if (nameSuffixes.any { name.endsWith(it) }) return true
        if (pathSegments.isNotEmpty()) {
            val segments = file.path.lowercase().split('/').dropLast(1)
            if (segments.any { it in pathSegments }) return true
        }
        return false
    }
}

object JunkRules {

    /** Directory names that are always walked into, even when hidden files are excluded. */
    val cacheDirectories: Set<String> = setOf(".thumbnails", "thumbnails", ".trashed", ".pending", ".cache", "cache")

    val all: List<JunkRule> = listOf(
        JunkRule(
            id = "temp",
            label = "Temporary files",
            description = "Incomplete or scratch files left behind by downloads and editors.",
            safeByDefault = true,
            extensions = setOf("tmp", "temp", "part", "partial", "crdownload", "download", "saving"),
        ),
        JunkRule(
            id = "logs",
            label = "Logs and crash dumps",
            description = "Diagnostic logs and crash dumps that no app reads after the fact.",
            safeByDefault = true,
            extensions = setOf("log", "dmp", "stackdump", "hprof"),
            namePrefixes = setOf("hs_err_pid", "crash-", "logcat"),
        ),
        JunkRule(
            id = "backups",
            label = "Stale backups",
            description = "Editor backups such as file.bak, file.old and file~.",
            safeByDefault = true,
            extensions = setOf("bak", "old", "orig", "sav"),
            nameSuffixes = setOf("~"),
        ),
        JunkRule(
            id = "os-metadata",
            label = "OS metadata",
            description = "Thumbs.db, desktop.ini and similar bookkeeping files copied from computers.",
            safeByDefault = true,
            exactNames = setOf("thumbs.db", "ehthumbs.db", "desktop.ini", ".ds_store", ".localized"),
        ),
        JunkRule(
            id = "empty-files",
            label = "Empty files",
            description = "Zero-byte files. .nomedia is preserved because it is meaningful.",
            safeByDefault = true,
            emptyFilesOnly = true,
        ),
        JunkRule(
            id = "thumbnails",
            label = "Thumbnail caches",
            description = "Cached previews inside .thumbnails folders, regenerated on demand.",
            safeByDefault = false,
            pathSegments = setOf(".thumbnails", "thumbnails"),
        ),
    )

    fun ruleFor(file: ScannedFile): JunkRule? {
        if (file.name.equals(".nomedia", ignoreCase = true)) return null
        return all.firstOrNull { it.matches(file) }
    }

    fun classify(files: List<ScannedFile>, enabledIds: Set<String>): List<JunkFinding> = files
        .mapNotNull { file ->
            val rule = ruleFor(file) ?: return@mapNotNull null
            if (rule.id !in enabledIds) return@mapNotNull null
            JunkFinding(file, rule.id, rule.label, rule.safeByDefault)
        }
        .sortedWith(compareByDescending<JunkFinding> { it.file.size }.thenBy { it.file.name.lowercase() })
}
