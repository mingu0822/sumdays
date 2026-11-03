package com.example.sumdays.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.sumdays.auth.SessionManager
import java.util.concurrent.TimeUnit

object BackupScheduler {
    // 3시간마다 자동 백업
    fun scheduleAutoBackup(context : Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // Wi-Fi 연결 시만 수행
            .build()

        val request = PeriodicWorkRequestBuilder<BackupWorker>(
            3, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "auto_backup",
                ExistingPeriodicWorkPolicy.KEEP, // 기존 스케줄 유지
                request
            )
    }

    // 수동 백업 (즉시 한 번 실행)
    fun triggerManualBackup(context : Context) {
        val userId = SessionManager.getUserId()
        if (userId < 0) return
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInputData(workDataOf(InitialSyncWorker.KEY_EXPECTED_USER_ID to userId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(InitialSyncWorker.accountWorkTag(userId))
            .build()
        // Appending keeps edits made during an upload from losing their next run.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "save_backup_user_$userId", ExistingWorkPolicy.APPEND_OR_REPLACE, request,
        )
    }
}
