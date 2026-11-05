package dev.modifyx.data

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class CleanupRecord(
    val id: String,
    val timestamp: Long,
    val label: String,
    val bytesFreed: Long,
    val deleted: Int,
    val failed: Int,
    val durationMs: Long,
)

/** Local, append-only log of cleanups. It never leaves the device. */
class HistoryStore(private val context: Context) {

    private val file = File(context.filesDir, "cleanup_history.json")
    private val mutex = Mutex()
    private val _records = MutableStateFlow<List<CleanupRecord>>(emptyList())
    val records: StateFlow<List<CleanupRecord>> = _records.asStateFlow()

    suspend fun ensureLoaded() = mutex.withLock {
        if (_records.value.isNotEmpty() || !file.exists()) return@withLock
        val loaded = withContext(Dispatchers.IO) {
            runCatching { parse(file.readText()) }.getOrDefault(emptyList())
        }
        _records.value = loaded.sortedByDescending { it.timestamp }
    }

    suspend fun append(report: CleanupReport) = mutex.withLock {
        if (report.outcomes.isEmpty()) return@withLock
        val record = CleanupRecord(
            id = UUID.randomUUID().toString().take(8),
            timestamp = report.startedAt,
            label = report.label,
            bytesFreed = report.bytesFreed,
            deleted = report.deleted,
            failed = report.failed,
            durationMs = report.durationMs,
        )
        val updated = (listOf(record) + _records.value).take(MAX_RECORDS)
        _records.value = updated
        write(updated)
    }

    suspend fun clear() = mutex.withLock {
        _records.value = emptyList()
        withContext(Dispatchers.IO) { file.delete() }
    }

    private suspend fun write(records: List<CleanupRecord>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("id", record.id)
                    .put("timestamp", record.timestamp)
                    .put("label", record.label)
                    .put("bytes", record.bytesFreed)
                    .put("deleted", record.deleted)
                    .put("failed", record.failed)
                    .put("duration", record.durationMs),
            )
        }
        runCatching { file.writeText(array.toString()) }
    }

    private fun parse(raw: String): List<CleanupRecord> {
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            CleanupRecord(
                id = obj.optString("id", index.toString()),
                timestamp = obj.optLong("timestamp"),
                label = obj.optString("label", "Cleanup"),
                bytesFreed = obj.optLong("bytes"),
                deleted = obj.optInt("deleted"),
                failed = obj.optInt("failed"),
                durationMs = obj.optLong("duration"),
            )
        }
    }

    private companion object {
        const val MAX_RECORDS = 100
    }
}
