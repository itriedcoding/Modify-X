package dev.modifyx.data

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class HistoryRecord(
    val id: String,
    val timestamp: Long,
    val action: DebloatAction,
    val packageName: String,
    val label: String,
    val success: Boolean,
    val verified: Boolean,
    val message: String,
) {
    val undoAction: DebloatAction? get() = if (success) action.inverse() else null
}

/**
 * Append-only log of everything the app actually did, persisted as JSON lines.
 * It doubles as the undo source: every reversible action knows its inverse.
 */
class HistoryStore(context: Context) {

    private val file = File(context.filesDir, "history.jsonl")
    private val mutex = Mutex()
    private val _records = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val records: StateFlow<List<HistoryRecord>> = _records.asStateFlow()

    @Volatile
    private var loaded = false

    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            _records.value = withContext(Dispatchers.IO) { readAll() }
            loaded = true
        }
    }

    suspend fun append(record: HistoryRecord) {
        ensureLoaded()
        mutex.withLock {
            val updated = (listOf(record) + _records.value).take(MAX_RECORDS)
            _records.value = updated
            withContext(Dispatchers.IO) {
                if (updated.size < MAX_RECORDS - 1) {
                    file.appendText(record.toJson().toString() + "\n")
                } else {
                    file.writeText(updated.reversed().joinToString("\n") { it.toJson().toString() } + "\n")
                }
            }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            _records.value = emptyList()
            withContext(Dispatchers.IO) { file.writeText("") }
        }
    }

    fun recordFor(result: ActionResult): HistoryRecord = HistoryRecord(
        id = UUID.randomUUID().toString(),
        timestamp = System.currentTimeMillis(),
        action = result.action,
        packageName = result.packageName,
        label = result.label,
        success = result.success,
        verified = result.verified,
        message = result.message,
    )

    private fun readAll(): List<HistoryRecord> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines()
                .asSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { line -> parse(line) }
                .toList()
                .reversed()
        }.getOrDefault(emptyList())
    }

    private fun parse(line: String): HistoryRecord? = runCatching {
        val json = JSONObject(line)
        val action = DebloatAction.fromStorage(json.optString("action")) ?: return null
        HistoryRecord(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            timestamp = json.optLong("ts", 0L),
            action = action,
            packageName = json.optString("pkg"),
            label = json.optString("label", json.optString("pkg")),
            success = json.optBoolean("success", false),
            verified = json.optBoolean("verified", false),
            message = json.optString("message"),
        )
    }.getOrNull()

    private fun HistoryRecord.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("ts", timestamp)
        .put("action", action.name)
        .put("pkg", packageName)
        .put("label", label)
        .put("success", success)
        .put("verified", verified)
        .put("message", message)

    private companion object {
        const val MAX_RECORDS = 2_000
    }
}
