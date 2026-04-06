package com.example.sumdays.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        try {
            Log.d("BackupWork","dowork (front to back")

            // 먼저 로그인 되어있는지 검사
            val token = SessionManager.getToken()
            if(token == null) {
                val serverFailData = workDataOf(
                    "type" to "token_error",
                    "message" to "token이 유효하지 않음"
                )
                return@withContext Result.failure(serverFailData)
            }
            val tokenHeader = "Bearer ${token}"

            // 1. dao 초기화
            val db = AppDatabase.getDatabase(applicationContext)
            val memoDao = db.memoDao()
            val userStyleDao = db.userStyleDao()
            val dailyEntryDao = db.dailyEntryDao()
            val weekSummaryDao = db.weekSummaryDao()


            // 2. edited, deleted 객체 가져오기 (memo, userStyle, dailyEntry, weekSummary)
            // 2-1. Memo
            val deletedMemoIds =  memoDao.getDeletedMemos().map { it.id }
            val editedMemos = memoDao.getEditedMemos()
            // 2-2. Style
            val deletedStyleIds = userStyleDao.getDeletedStyles().map { it.styleId }
            val editedStyles = userStyleDao.getEditedStyles()
            // 2-3. Entry
            val deletedEntryDates = dailyEntryDao.getDeletedEntries().map { it.date }
            val editedEntries = dailyEntryDao.getEditedEntries()
            // 2-4. Summary
            val deletedSummaryStartDates = weekSummaryDao.getDeletedSummaries().map { it.startDate }
            val editedSummaryEntities = weekSummaryDao.getEditedSummaries()
            val editedSummaries = editedSummaryEntities.map {it.weekSummary}

            // 3. 서버에 요청하기
            val syncRequest : SyncRequest = buildSyncRequest(deletedMemoIds, deletedStyleIds, deletedEntryDates, deletedSummaryStartDates,
                editedMemos, editedStyles, editedEntries, editedSummaries)
            val syncResponseBody = ApiClient.api.syncData(tokenHeader,syncRequest).body()

            // 4-1. 성공 -> flag 해제
            if (syncResponseBody != null && syncResponseBody.status == "success"){
                val editedMemoIds = editedMemos.map { it.id }
                val editedStyleIds = editedStyles.map { it.styleId }
                val editedEntryDates = editedEntries.map { it.date }
                val editedSummaryStartDates = editedSummaries.map { it.startDate }

                memoDao.resetDeletedFlags(deletedMemoIds)
                memoDao.resetEditedFlags(editedMemoIds)
                userStyleDao.resetDeletedFlags(deletedStyleIds)
                userStyleDao.resetEditedFlags(editedStyleIds)
                dailyEntryDao.resetDeletedFlags(deletedEntryDates)
                dailyEntryDao.resetEditedFlags(editedEntryDates)
                weekSummaryDao.resetDeletedFlags(deletedSummaryStartDates)
                weekSummaryDao.resetEditedFlags(editedSummaryStartDates)
                return@withContext Result.success()
            }

            // 4-2. 실패
            else {
                val serverFailData = workDataOf(
                    "type" to "server_error",
                    "message" to (syncResponseBody?.message ?: "서버 응답 없음")
                )
                return@withContext Result.failure(serverFailData)
            }
        } catch (e: Exception) {
            val exceptionData = workDataOf(
                "type" to "exception",
                "message" to (e.message ?: "알 수 없는 오류")
            )
            return@withContext Result.failure(exceptionData)
        }
    }
}
