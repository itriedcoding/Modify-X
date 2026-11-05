package dev.modifyx.data

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single entry point for changing device state. Every action is executed through
 * [ActionEngine] and written to [HistoryStore] so it can be undone and audited.
 */
class ActionRunner(
    private val engine: ActionEngine,
    private val history: HistoryStore,
    private val settings: SettingsStore,
) {

    suspend fun run(action: DebloatAction, app: AppEntry): ActionResult {
        val result = engine.perform(action, app)
        history.append(history.recordFor(result))
        return result
    }

    suspend fun runBatch(
        action: DebloatAction,
        apps: List<AppEntry>,
        onProgress: (done: Int, total: Int, app: AppEntry) -> Unit = { _, _, _ -> },
    ): BatchSummary = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        val results = ArrayList<ActionResult>(apps.size)
        apps.forEachIndexed { index, app ->
            onProgress(index, apps.size, app)
            results += run(action, app)
        }
        BatchSummary(results, started, SystemClock.elapsedRealtime() - started)
    }

    suspend fun undo(record: HistoryRecord): ActionResult? {
        val action = record.undoAction ?: return null
        val app = AppEntry(
            packageName = record.packageName,
            label = record.label,
            isSystem = true,
            isUpdatedSystem = false,
            enabled = true,
            installedForUser = true,
            suspended = false,
            versionName = null,
            versionCode = 0,
            targetSdk = 0,
            minSdk = 0,
            firstInstallTime = 0L,
            lastUpdateTime = 0L,
            apkBytes = 0L,
            uid = -1,
            launchable = false,
            installer = null,
        )
        return run(action, app)
    }

    /** Packages of a profile that are currently installed and would be acted on. */
    fun plan(profile: Profile, apps: Map<String, AppEntry>): List<AppEntry> = profile.packages
        .mapNotNull { apps[it] }
        .filter { it.installedForUser }
}
