package dev.modifyx.data

/** One application as the device reports it. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isUpdatedSystem: Boolean,
    val enabled: Boolean,
    val installedForUser: Boolean,
    val suspended: Boolean,
    val versionName: String?,
    val versionCode: Long,
    val targetSdk: Int,
    val minSdk: Int,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val apkBytes: Long,
    val uid: Int,
    val launchable: Boolean,
    val installer: String?,
    /** From usage stats when the user granted usage access; 0 means no recorded use. */
    val lastUsedAt: Long = 0L,
) {
    val isUserApp: Boolean get() = !isSystem
    val removedForUser: Boolean get() = !installedForUser
    val disabled: Boolean get() = installedForUser && !enabled
}

data class AppSnapshot(
    val apps: List<AppEntry> = emptyList(),
    val loadedAt: Long = 0L,
    val privileged: Boolean = false,
    val warning: String? = null,
)

data class PermissionRow(
    val name: String,
    val granted: Boolean,
    val dangerous: Boolean,
)

data class AppDetails(
    val packageName: String,
    val apkPaths: List<String>,
    val signatureSha256: String?,
    val permissions: List<PermissionRow>,
    val activities: Int,
    val services: Int,
    val receivers: Int,
    val providers: Int,
    val dataBytes: Long?,
    val cacheBytes: Long?,
    val isDebuggable: Boolean,
    val installedBy: String?,
    val processes: List<String>,
)

/** What a debloat action does, and how it can be undone. */
enum class DebloatAction(val title: String, val verbPast: String) {
    UNINSTALL_FOR_USER("Uninstall for user 0", "Uninstalled"),
    RESTORE_FOR_USER("Restore for user 0", "Restored"),
    DISABLE("Disable", "Disabled"),
    ENABLE("Enable", "Enabled"),
    CLEAR_DATA("Clear app data", "Cleared data of"),
    FORCE_STOP("Force stop", "Force stopped"),
    UNINSTALL_FULL("Uninstall completely", "Uninstalled"),
    ;

    /** The action that reverses this one, when it is safely reversible. */
    fun inverse(): DebloatAction? = when (this) {
        UNINSTALL_FOR_USER -> RESTORE_FOR_USER
        RESTORE_FOR_USER -> UNINSTALL_FOR_USER
        DISABLE -> ENABLE
        ENABLE -> DISABLE
        else -> null
    }

    val reversible: Boolean get() = inverse() != null

    companion object {
        /** Restoring or enabling are safe; everything else changes device state. */
        fun fromStorage(raw: String?): DebloatAction? = entries.firstOrNull { it.name == raw }
    }
}

data class ActionResult(
    val packageName: String,
    val label: String,
    val action: DebloatAction,
    val success: Boolean,
    val verified: Boolean,
    val message: String,
    val command: String,
    val output: String,
)

data class BatchSummary(
    val results: List<ActionResult>,
    val startedAt: Long,
    val durationMs: Long,
) {
    val successes: Int get() = results.count { it.success }
    val failures: Int get() = results.size - successes
}
