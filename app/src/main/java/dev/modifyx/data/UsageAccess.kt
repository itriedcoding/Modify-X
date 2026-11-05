package dev.modifyx.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Last-used timestamps from Android's usage stats. The data only exists after the user
 * grants usage access in system settings, and the app reports that honestly instead of
 * inventing "unused" apps.
 */
object UsageAccess {

    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun hasAccess(context: Context): Boolean = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** package name -> most recent lastTimeUsed within [days]. */
    fun lastUsed(context: Context, days: Int = 120): Map<String, Long> = runCatching {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - days * DAY_MS
        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
        if (stats.isNullOrEmpty()) return emptyMap()
        val result = HashMap<String, Long>(stats.size)
        for (stat in stats) {
            val name = stat.packageName ?: continue
            if (stat.lastTimeUsed > (result[name] ?: 0L)) result[name] = stat.lastTimeUsed
        }
        result
    }.getOrDefault(emptyMap())

    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
