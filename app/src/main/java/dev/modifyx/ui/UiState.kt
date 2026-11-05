package dev.modifyx.ui

import dev.modifyx.core.UpdateChecker
import dev.modifyx.data.BackupDocument

/** Progress of a running batch operation, shown by the blocking dialog. */
data class Busy(val title: String, val done: Int, val total: Int, val current: String)

/** Result of the last console command. */
data class ConsoleState(
    val running: Boolean = false,
    val command: String = "",
    val output: String = "",
    val exitCode: Int? = null,
    val durationMs: Long = 0L,
)

/** Everything the update UI needs, kept in one place so Home and Settings agree. */
data class UpdateState(
    val currentVersion: String = "",
    val checking: Boolean = false,
    val available: UpdateChecker.UpdateInfo? = null,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val message: String? = null,
)

/** Parsed backup waiting for the user to choose restore or re-remove. */
data class ImportPreview(
    val document: BackupDocument,
    val restorable: Int,
    val reRemovable: Int,
)
