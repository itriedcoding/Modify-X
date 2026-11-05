package dev.modifyx.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Hands the automation run to WorkManager after a reboot or app update, so the batch is not
 * limited to the receiver's short execution window.
 */
class BootProfileReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // Give the system a moment to finish bringing package services up.
        enqueue(context, delaySeconds = 20)
    }

    companion object {
        private const val WORK_NAME = "modifyx-profile-automation"

        fun enqueue(context: Context, delaySeconds: Long = 0L) {
            val request = OneTimeWorkRequestBuilder<ProfileAutomationWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
