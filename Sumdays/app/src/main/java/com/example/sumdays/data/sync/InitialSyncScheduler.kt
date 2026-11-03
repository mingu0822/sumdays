package com.example.sumdays.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.sumdays.auth.SessionManager
import java.util.concurrent.TimeUnit

/** One recoverable initial-sync job per account. KEEP never cancels an active retry. */
object InitialSyncScheduler {
    fun ensureScheduled(context: Context, replace: Boolean = false) {
        val userId = SessionManager.getUserId()
        if (userId < 0 || LocalDataOwnerPrefs.isReadyFor(context, userId)) return
        val request = OneTimeWorkRequestBuilder<InitialSyncWorker>()
            .setInputData(workDataOf(InitialSyncWorker.KEY_EXPECTED_USER_ID to userId))
            // A reachable LAN/emulator API does not require Android's public
            // internet validation. The worker retries actual connection failures.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(InitialSyncWorker.accountWorkTag(userId))
            .build()
        val manager = WorkManager.getInstance(context.applicationContext)
        // Replace jobs installed by the old network-constrained scheduler once.
        manager.cancelUniqueWork("initial_sync_user_$userId")
        manager.enqueueUniqueWork(
            "initial_sync_v2_user_$userId",
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
