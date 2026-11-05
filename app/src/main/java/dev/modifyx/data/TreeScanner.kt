package dev.modifyx.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.os.SystemClock
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Walks the folders the user granted through the Storage Access Framework and builds a real
 * picture of what is inside: every file, rule-matched junk, content-verified duplicates and
 * truly empty directories. No file is touched here.
 */
class TreeScanner(private val resolver: ContentResolver) {

    data class Options(
        val includeHidden: Boolean,
        val minDuplicateBytes: Long,
        val enabledRuleIds: Set<String>,
        val maxDepth: Int = 32,
        val maxHashBytes: Long = 512L * 1024 * 1024,
    )

    enum class Phase { WALKING, HASHING }

    data class Progress(val phase: Phase, val files: Int, val bytes: Long, val current: String)

    private data class Row(
        val uri: String,
        val docId: String,
        val name: String,
        val mime: String?,
        val size: Long,
        val modified: Long,
        val isDir: Boolean,
    )

    private class WalkState(
        val rootLabel: String,
        val options: Options,
        val onProgress: (Progress) -> Unit,
    ) {
        val files = mutableListOf<ScannedFile>()
        val emptyFolders = mutableListOf<EmptyFolder>()
        val warnings = mutableListOf<String>()
        var bytes = 0L
        var hidden = 0
        var lastReport = 0L

        fun report(phase: Phase, current: String, force: Boolean = false) {
            val now = SystemClock.elapsedRealtime()
            if (!force && now - lastReport < 120L) return
            lastReport = now
            onProgress(Progress(phase, files.size, bytes, current))
        }
    }

    suspend fun scan(
        roots: List<ScanRoot>,
        options: Options,
        onProgress: (Progress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): ScanResult = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        val files = mutableListOf<ScannedFile>()
        val emptyFolders = mutableListOf<EmptyFolder>()
        val warnings = mutableListOf<String>()
        var hidden = 0
        var unreadable = 0
        var tooLarge = 0
        var cancelled = false

        for (root in roots) {
            if (isCancelled()) { cancelled = true; break }
            val treeUri = runCatching { Uri.parse(root.uri) }.getOrNull() ?: continue
            val state = WalkState(root.label, options, onProgress)
            val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            if (rootId.isNullOrBlank()) {
                warnings += "${root.label}: the folder permission is no longer valid."
                continue
            }
            try {
                walk(treeUri, rootId, root.label, 0, state, false, isCancelled)
            } catch (error: SecurityException) {
                warnings += "${root.label}: access was revoked. Grant the folder again."
            } catch (error: Exception) {
                warnings += "${root.label}: ${error.message ?: "could not be read"}"
            }
            files += state.files
            emptyFolders += state.emptyFolders
            warnings += state.warnings
            hidden += state.hidden
            state.report(Phase.WALKING, "Scanned ${files.size} files", force = true)
        }

        val totalBytes = files.sumOf { it.size }
        onProgress(Progress(Phase.HASHING, files.size, totalBytes, "Comparing identical sizes"))
        val hashes = HashMap<String, String?>()
        val duplicateCandidates = files.filter { it.size >= options.minDuplicateBytes && it.size > 0L }
        val groups = DuplicateGrouper.group(duplicateCandidates, options.minDuplicateBytes) { file ->
            if (isCancelled()) return@group null
            if (file.size > options.maxHashBytes) {
                tooLarge++
                return@group null
            }
            hashes.getOrPut(file.uri) {
                onProgress(Progress(Phase.HASHING, files.size, totalBytes, file.name))
                hashOf(Uri.parse(file.uri), isCancelled)
            }
        }
        if (isCancelled()) cancelled = true

        val junk = JunkRules.classify(files, options.enabledRuleIds)
        ScanResult(
            files = files,
            junk = junk,
            duplicates = groups,
            emptyFolders = emptyFolders.sortedByDescending { it.path.length },
            scannedAt = System.currentTimeMillis(),
            durationMs = SystemClock.elapsedRealtime() - started,
            bytesScanned = totalBytes,
            skippedHidden = hidden,
            unreadable = unreadable,
            tooLargeToHash = tooLarge,
            cancelled = cancelled,
            warnings = warnings.distinct().take(6),
        )
    }

    /** Display name for a granted tree, used as the on-screen root label. */
    fun describe(uri: Uri): ScanRoot? {
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val docUri = runCatching { DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId) }.getOrNull()
        val label = docUri?.let { documentName(it) }
        val fallback = runCatching { Uri.decode(treeDocId.substringAfter(':')) }.getOrNull()
        val clean = label ?: fallback ?: return null
        return ScanRoot(uri.toString(), clean.ifBlank { "Folder" })
    }

    private fun documentName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    private fun walk(
        treeUri: Uri,
        docId: String,
        path: String,
        depth: Int,
        state: WalkState,
        insideCacheDir: Boolean,
        isCancelled: () -> Boolean,
    ): Int {
        if (depth > state.options.maxDepth) {
            state.warnings += "Skipped a folder nested deeper than ${state.options.maxDepth} levels."
            return 0
        }
        val children = rows(treeUri, docId) ?: return 0
        var entries = 0

        for (row in children) {
            if (isCancelled()) return entries
            entries++
            val childPath = "$path/${row.name}"
            if (row.isDir) {
                val cacheDir = insideCacheDir || row.name.lowercase() in JunkRules.cacheDirectories
                val childEntries = try {
                    walk(treeUri, row.docId, childPath, depth + 1, state, cacheDir, isCancelled)
                } catch (error: SecurityException) {
                    state.warnings += "$childPath: permission denied."
                    1
                }
                if (childEntries == 0) {
                    state.emptyFolders += EmptyFolder(row.uri, childPath, row.name)
                }
            } else {
                val visible = insideCacheDir || state.options.includeHidden || !row.name.startsWith(".")
                if (!visible) {
                    state.hidden++
                    continue
                }
                val category = FileCategory.of(row.name, row.mime)
                state.files += ScannedFile(
                    uri = row.uri,
                    name = row.name,
                    path = childPath,
                    size = row.size,
                    modified = row.modified,
                    category = category,
                    mime = row.mime,
                    rootLabel = state.rootLabel,
                )
                state.bytes += row.size
                state.report(Phase.WALKING, childPath)
            }
        }
        return entries
    }

    private fun rows(treeUri: Uri, docId: String): List<Row>? {
        val childrenUri = runCatching { DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId) }.getOrNull()
            ?: return null
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val result = mutableListOf<Row>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                val childId = cursor.getString(idIndex) ?: continue
                val name = cursor.getString(nameIndex) ?: continue
                val mime = cursor.getString(mimeIndex)
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val size = if (cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex)
                val modified = if (cursor.isNull(modifiedIndex)) 0L else cursor.getLong(modifiedIndex)
                val childUri = runCatching { DocumentsContract.buildDocumentUriUsingTree(treeUri, childId) }
                    .getOrNull() ?: continue
                result += Row(childUri.toString(), childId, name, mime, size, modified, isDir)
            }
        }
        return result
    }

    private fun hashOf(uri: Uri, isCancelled: () -> Boolean): String? = try {
        resolver.openInputStream(uri)?.use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(128 * 1024)
            while (true) {
                if (isCancelled()) return null
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (error: Exception) {
        null
    }
}
