package dev.modifyx.core

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.modifyx.data.DebloatAction
import dev.modifyx.ModifyXApp
import kotlinx.coroutines.flow.first

/**
 * Applies the automation profile in WorkManager, which keeps the process alive long enough
 * for a full batch. A broadcast receiver's ~10s window silently truncated large profiles.
 */
class ProfileAutomationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ModifyXApp ?: return Result.failure()
        val container = app.container

        return try {
            val settings = container.settings.settings.first()
            if (!settings.autoApplyEnabled) return Result.success()
            val profileId = settings.autoApplyProfileId ?: return Result.success()

            PrivilegeManager.init(applicationContext)
            PrivilegeManager.refresh()
            if (!PrivilegeManager.canModify()) return Result.success()

            container.profiles.ensureLoaded()
            val snapshot = container.repository.loadSnapshot()
            val installed = snapshot.apps.map { it.packageName }.toSet()
            val profile = container.profiles.allProfiles(installed).firstOrNull { it.id == profileId }
                ?: return Result.success()

            val byPackage = snapshot.apps.associateBy { it.packageName }
            val targets = profile.packages.mapNotNull { byPackage[it] }.filter { it.installedForUser }
            if (targets.isEmpty()) return Result.success()

            container.runner.runBatch(DebloatAction.UNINSTALL_FOR_USER, targets)
            Result.success()
        } catch (error: Throwable) {
            // Never retry a destructive batch automatically; history already records what ran.
            Result.failure()
        }
    }
}
