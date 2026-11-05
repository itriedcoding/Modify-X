package dev.modifyx.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.modifyx.container
import dev.modifyx.data.DebloatAction
import dev.modifyx.data.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Re-applies the profile the user marked for automation after a reboot or app update.
 * Only runs when a privileged shell is available; otherwise it silently does nothing and
 * records a skipped entry in history.
 */
class BootProfileReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pending = goAsync()
        val container = context.container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = withTimeoutOrNull(15_000) { container.settings.settings.first() } ?: return@launch
                if (!settings.autoApplyEnabled) return@launch
                val profileId = settings.autoApplyProfileId ?: return@launch

                PrivilegeManager.refresh()
                if (!PrivilegeManager.canModify()) return@launch

                container.profiles.ensureLoaded()
                val snapshot = container.repository.loadSnapshot()
                val installed = snapshot.apps.map { it.packageName }.toSet()
                val profile: Profile = container.profiles.allProfiles(installed)
                    .firstOrNull { it.id == profileId } ?: return@launch
                if (profile.packages.isEmpty()) return@launch

                val byPackage = snapshot.apps.associateBy { it.packageName }
                val targets = profile.packages.mapNotNull { byPackage[it] }
                    .filter { it.installedForUser }
                if (targets.isEmpty()) return@launch

                container.runner.runBatch(DebloatAction.UNINSTALL_FOR_USER, targets)
            } catch (_: Throwable) {
                // A boot receiver must never crash the process; history keeps what happened.
            } finally {
                pending.finish()
            }
        }
    }
}
