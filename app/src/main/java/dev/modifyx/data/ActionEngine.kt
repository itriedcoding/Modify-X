package dev.modifyx.data

import dev.modifyx.core.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes debloat commands through the active privileged shell and then re-reads the
 * device state to confirm the change actually happened. A command that exits 0 is not
 * treated as success on its own — [ActionResult.verified] reflects the follow-up check.
 */
class ActionEngine {

    suspend fun perform(action: DebloatAction, app: AppEntry): ActionResult = withContext(Dispatchers.IO) {
        if (!PrivilegeManager.canModify()) {
            return@withContext ActionResult(
                packageName = app.packageName,
                label = app.label,
                action = action,
                success = false,
                verified = false,
                message = "No root or Shizuku shell: nothing was executed.",
                command = "",
                output = "",
            )
        }

        val command = commandFor(action, app.packageName)
        val result = PrivilegeManager.shell().exec(command, timeoutMs = 90_000)
        val verified = when (action) {
            DebloatAction.UNINSTALL_FOR_USER -> !isInstalledForUser(app.packageName)
            DebloatAction.RESTORE_FOR_USER, DebloatAction.ENABLE -> isInstalledForUser(app.packageName)
            DebloatAction.DISABLE -> isDisabled(app.packageName)
            DebloatAction.UNINSTALL_FULL -> !isInstalledForUser(app.packageName)
            DebloatAction.CLEAR_DATA, DebloatAction.FORCE_STOP -> result.success
        }

        val message = when {
            verified -> verifiedMessage(action)
            !result.success -> result.output.ifBlank { "Command exited with ${result.exitCode}" }.lines().first()
            else -> "Command succeeded but the device state did not change. This package may be protected by the system."
        }

        ActionResult(
            packageName = app.packageName,
            label = app.label,
            action = action,
            success = verified && result.success,
            verified = verified,
            message = message,
            command = command,
            output = result.output,
        )
    }

    private fun commandFor(action: DebloatAction, pkg: String): String = when (action) {
        DebloatAction.UNINSTALL_FOR_USER -> "pm uninstall --user 0 $pkg"
        DebloatAction.RESTORE_FOR_USER -> "pm install-existing --user 0 $pkg"
        DebloatAction.DISABLE -> "pm disable-user --user 0 $pkg"
        DebloatAction.ENABLE -> "pm enable --user 0 $pkg"
        DebloatAction.CLEAR_DATA -> "pm clear --user 0 $pkg"
        DebloatAction.FORCE_STOP -> "am force-stop $pkg"
        DebloatAction.UNINSTALL_FULL -> "pm uninstall $pkg"
    }

    private fun verifiedMessage(action: DebloatAction): String = when (action) {
        DebloatAction.UNINSTALL_FOR_USER -> "Package removed for the current user."
        DebloatAction.RESTORE_FOR_USER -> "Package restored for the current user."
        DebloatAction.DISABLE -> "Package disabled."
        DebloatAction.ENABLE -> "Package enabled."
        DebloatAction.CLEAR_DATA -> "App data cleared."
        DebloatAction.FORCE_STOP -> "App process stopped."
        DebloatAction.UNINSTALL_FULL -> "Package uninstalled from the device."
    }

    private suspend fun isInstalledForUser(pkg: String): Boolean {
        val result = PrivilegeManager.shell().exec("pm list packages --user 0 $pkg", 20_000)
        return result.success && result.stdout.lineSequence().map { it.trim() }.any { it == "package:$pkg" }
    }

    private suspend fun isDisabled(pkg: String): Boolean {
        val result = PrivilegeManager.shell().exec("pm list packages -d --user 0", 20_000)
        if (!result.success) return false
        return result.stdout.lineSequence().map { it.trim() }.any { it == "package:$pkg" }
    }
}
