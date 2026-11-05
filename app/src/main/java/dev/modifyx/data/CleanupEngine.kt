package dev.modifyx.data

import android.content.ContentResolver
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A file or empty folder the user asked to delete. */
data class DeleteTarget(
    val uri: String,
    val name: String,
    val size: Long,
    val directory: Boolean = false,
)

/**
 * Deletes documents through the Storage Access Framework and then re-queries each one.
 * A delete is only reported as successful when the platform really stops serving the
 * document, so a no-op or silent failure can never be shown as freed space.
 */
class CleanupEngine(private val resolver: ContentResolver) {

    suspend fun delete(
        targets: List<DeleteTarget>,
        label: String,
        onProgress: (done: Int, total: Int, name: String) -> Unit = { _, _, _ -> },
    ): CleanupReport = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val began = SystemClock.elapsedRealtime()
        val outcomes = ArrayList<DeleteOutcome>(targets.size)

        targets.distinctBy { it.uri }.forEachIndexed { index, target ->
            onProgress(index, targets.size, target.name)
            val uri = runCatching { Uri.parse(target.uri) }.getOrNull()
            if (uri == null) {
                outcomes += DeleteOutcome(target.uri, target.name, target.size, false, "The document address is invalid.")
                return@forEachIndexed
            }
            val removed = runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)
            val gone = !exists(uri)
            when {
                removed && gone -> outcomes += DeleteOutcome(target.uri, target.name, target.size, true, "Deleted")
                gone -> outcomes += DeleteOutcome(target.uri, target.name, target.size, true, "Removed by the provider")
                removed -> outcomes += DeleteOutcome(
                    target.uri, target.name, target.size, false,
                    "The provider accepted the delete but still lists the file.",
                )
                else -> outcomes += DeleteOutcome(
                    target.uri, target.name, target.size, false,
                    "The provider refused the delete. The folder may be read-only.",
                )
            }
        }
        onProgress(targets.size, targets.size, "")

        CleanupReport(
            label = label,
            outcomes = outcomes,
            startedAt = started,
            durationMs = SystemClock.elapsedRealtime() - began,
        )
    }

    /** True when the provider still serves the document. */
    fun exists(uri: Uri): Boolean = try {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
            ?.use { it.count > 0 } ?: false
    } catch (error: Exception) {
        false
    }
}
