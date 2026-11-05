package dev.modifyx.ui

import dev.modifyx.core.UpdateChecker

/** Progress of a running batch, shown by the blocking dialog. */
data class Busy(val title: String, val done: Int, val total: Int, val current: String)

/** Everything the update UI needs, shared by Home and Settings. */
data class UpdateState(
    val currentVersion: String = "",
    val checking: Boolean = false,
    val available: UpdateChecker.UpdateInfo? = null,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val message: String? = null,
)
