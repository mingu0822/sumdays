package com.example.sumdays.mission

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.sync.BackupSyncRunner
import com.example.sumdays.network.ApiClient

class MissionEventWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val expectedUserId = inputData.getInt(KEY_EXPECTED_USER_ID, -1)
        if (expectedUserId < 0 || SessionManager.getUserId() != expectedUserId) {
            return Result.failure()
        }
        val token = SessionManager.getToken() ?: return Result.failure()
        val tokenHeader = "Bearer $token"

        val response = try {
            when (inputData.getString(KEY_EVENT_TYPE)) {
                EVENT_FRIEND_DIARY -> {
                    val friendId = inputData.getInt(KEY_FRIEND_ID, -1)
                    val diaryDate = inputData.getString(KEY_EVENT_KEY) ?: return Result.failure()
                    if (friendId < 0) return Result.failure()
                    ApiClient.api.recordFriendDiaryView(
                        tokenHeader,
                        FriendDiaryViewRequest(friendId, diaryDate),
                    )
                }
                EVENT_WEEKLY_SUMMARY -> {
                    val summaryStartDate = inputData.getString(KEY_EVENT_KEY)
                        ?: return Result.failure()
                    val sync = BackupSyncRunner.syncNow(applicationContext)
                    if (!sync.success) return Result.retry()
                    ApiClient.api.recordWeeklySummaryView(
                        tokenHeader,
                        WeeklySummaryViewRequest(summaryStartDate)
                    )
                }
                else -> return Result.failure()
            }
        } catch (_: Exception) {
            return Result.retry()
        }

        return when {
            response.isSuccessful -> Result.success()
            response.code() == 408 || response.code() == 429 || response.code() >= 500 -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val KEY_EVENT_TYPE = "event_type"
        const val KEY_EVENT_KEY = "event_key"
        const val KEY_FRIEND_ID = "friend_id"
        const val KEY_EXPECTED_USER_ID = "expected_user_id"
        const val EVENT_FRIEND_DIARY = "friend_diary"
        const val EVENT_WEEKLY_SUMMARY = "weekly_summary"
    }
}
