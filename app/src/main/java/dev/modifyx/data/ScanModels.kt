package dev.modifyx.data

/** Broad file kind, used for filters and for picking a preview strategy. */
enum class FileCategory(val label: String) {
    IMAGE("Images"),
    VIDEO("Video"),
    AUDIO("Audio"),
    DOCUMENT("Documents"),
    ARCHIVE("Archives"),
    OTHER("Other");

    companion object {
        fun of(name: String, mime: String?): FileCategory {
            val type = mime.orEmpty().lowercase()
            val extension = name.substringAfterLast('.', "").lowercase()
            return when {
                type.startsWith("image/") || extension in IMAGE_EXTENSIONS -> IMAGE
                type.startsWith("video/") || extension in VIDEO_EXTENSIONS -> VIDEO
                type.startsWith("audio/") || extension in AUDIO_EXTENSIONS -> AUDIO
                extension in DOCUMENT_EXTENSIONS -> DOCUMENT
                extension in ARCHIVE_EXTENSIONS -> ARCHIVE
                else -> OTHER
            }
        }

        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "dng", "tiff")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "webm", "avi", "3gp", "m4v")
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "opus", "ogg", "wav", "flac", "aac", "amr")
        private val DOCUMENT_EXTENSIONS = setOf("pdf", "doc", "docx", "txt", "md", "csv", "xls", "xlsx", "ppt", "pptx", "epub")
        private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "apk", "obb", "iso")
    }
}

/** One real file discovered under a granted folder. [uri] is a document URI that can be read and deleted. */
data class ScannedFile(
    val uri: String,
    val name: String,
    val path: String,
    val size: Long,
    val modified: Long,
    val category: FileCategory,
    val mime: String?,
    val rootLabel: String,
)

data class ScanRoot(val uri: String, val label: String)

data class JunkFinding(
    val file: ScannedFile,
    val ruleId: String,
    val ruleLabel: String,
    val safe: Boolean,
)

data class EmptyFolder(val uri: String, val path: String, val name: String)

data class DuplicateGroup(
    val key: String,
    val size: Long,
    val hash: String,
    val files: List<ScannedFile>,
    val category: FileCategory,
) {
    /** Everything except the keeper, which is what a cleanup actually reclaims. */
    val waste: Long get() = size * (files.size - 1).coerceAtLeast(0)

    /** Deterministic keeper: shortest path first, then the oldest copy. */
    val keeper: ScannedFile
        get() = files.minWithOrNull(compareBy<ScannedFile>({ it.path.length }, { it.modified }, { it.uri }))
            ?: files.first()

    val copies: List<ScannedFile> get() = files.filter { it.uri != keeper.uri }
}

data class ScanResult(
    val files: List<ScannedFile> = emptyList(),
    val junk: List<JunkFinding> = emptyList(),
    val duplicates: List<DuplicateGroup> = emptyList(),
    val emptyFolders: List<EmptyFolder> = emptyList(),
    val scannedAt: Long = 0L,
    val durationMs: Long = 0L,
    val bytesScanned: Long = 0L,
    val skippedHidden: Int = 0,
    val unreadable: Int = 0,
    val tooLargeToHash: Int = 0,
    val cancelled: Boolean = false,
    val warnings: List<String> = emptyList(),
) {
    val junkBytes: Long get() = junk.sumOf { it.file.size }
    val duplicateBytes: Long get() = duplicates.sumOf { it.waste }
    val emptyFolderBytes: Long get() = 0L
    val reclaimable: Long get() = junkBytes + duplicateBytes
    val isEmpty: Boolean get() = files.isEmpty() && emptyFolders.isEmpty()

    fun largeFiles(threshold: Long): List<ScannedFile> =
        files.filter { it.size >= threshold }.sortedByDescending { it.size }

    fun categoryBytes(): Map<FileCategory, Long> =
        files.groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.size } }
}

data class DeleteOutcome(
    val uri: String,
    val name: String,
    val bytes: Long,
    val success: Boolean,
    val message: String,
)

data class CleanupReport(
    val label: String,
    val outcomes: List<DeleteOutcome>,
    val startedAt: Long,
    val durationMs: Long,
) {
    val deleted: Int get() = outcomes.count { it.success }
    val failed: Int get() = outcomes.size - deleted
    val bytesFreed: Long get() = outcomes.filter { it.success }.sumOf { it.bytes }
}

/** Pure duplicate grouping so the rule can be unit-tested without touching a filesystem. */
object DuplicateGrouper {

    fun group(
        files: List<ScannedFile>,
        minSize: Long,
        hashOf: (ScannedFile) -> String?,
    ): List<DuplicateGroup> {
        val sizeGroups = files
            .filter { it.size >= minSize && it.size > 0L }
            .groupBy { it.size }
            .filterValues { it.size > 1 }

        val groups = mutableListOf<DuplicateGroup>()
        for ((size, sameSize) in sizeGroups) {
            val byHash = LinkedHashMap<String, MutableList<ScannedFile>>()
            for (file in sameSize) {
                val hash = hashOf(file) ?: continue
                byHash.getOrPut(hash) { mutableListOf() }.add(file)
            }
            for ((hash, sameHash) in byHash) {
                if (sameHash.size < 2) continue
                groups += DuplicateGroup(
                    key = "${sameHash.first().uri}#$hash",
                    size = size,
                    hash = hash,
                    files = sameHash.sortedWith(compareBy({ it.path.length }, { it.path })),
                    category = sameHash.first().category,
                )
            }
        }
        return groups.sortedWith(compareByDescending<DuplicateGroup> { it.waste }.thenBy { it.files.first().name })
    }
}

/**
 * Decides whether an automatic scan is due. Kept as plain arithmetic so the rule can be
 * unit-tested without a clock, a device or a filesystem.
 */
object AutoScanPolicy {

    const val MIN_HOURS = 1L
    const val MAX_HOURS = 168L

    fun intervalMillis(hours: Long): Long = hours.coerceIn(MIN_HOURS, MAX_HOURS) * 60L * 60_000L

    fun isDue(lastScanAt: Long, now: Long, intervalHours: Long, enabled: Boolean): Boolean {
        if (!enabled) return false
        if (lastScanAt <= 0L) return true
        if (now <= lastScanAt) return false
        return now - lastScanAt >= intervalMillis(intervalHours)
    }
}
