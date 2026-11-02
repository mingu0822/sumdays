package com.example.sumdays.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val syncResult = BackupSyncRunner.syncNow(applicationContext)
        if (syncResult.success) return Result.success()
        return Result.failure(
            workDataOf(
                "type" to syncResult.type,
                "message" to syncResult.message,
            )
        )
    }
}
