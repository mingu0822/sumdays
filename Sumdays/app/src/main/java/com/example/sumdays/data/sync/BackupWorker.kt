package com.example.sumdays.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.sumdays.auth.SessionManager

class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val expectedUserId = inputData.getInt(InitialSyncWorker.KEY_EXPECTED_USER_ID, -1)
        if (expectedUserId >= 0 && SessionManager.getUserId() != expectedUserId) return Result.failure()
        val syncResult = BackupSyncRunner.syncNow(applicationContext)
        if (syncResult.success) return Result.success()
        if (syncResult.type in setOf("connection_error", "server_retry", "account_sync_pending", "changes_pending")) {
            return Result.retry()
        }
        return Result.failure(
            workDataOf(
                "type" to syncResult.type,
                "message" to syncResult.message,
            )
        )
    }
}
