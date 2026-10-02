package com.example.sumdays.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
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
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(InitialSyncWorker.accountWorkTag(userId))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "initial_sync_user_$userId",
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
