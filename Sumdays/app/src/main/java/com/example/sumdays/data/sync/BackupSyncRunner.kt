package com.example.sumdays.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class BackupSyncResult(
    val success: Boolean,
    val type: String? = null,
    val message: String? = null,
)

object BackupSyncRunner {
    private val syncMutex = Mutex()

    suspend fun syncNow(context: Context): BackupSyncResult = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val userId = SessionManager.getUserId()
            val token = SessionManager.getToken()
                ?: return@withContext BackupSyncResult(false, "token_error", "token이 유효하지 않음")
            if (!LocalDataOwnerPrefs.isReadyFor(context.applicationContext, userId)) {
                InitialSyncScheduler.ensureScheduled(context)
                return@withContext BackupSyncResult(
                    false,
                    "account_sync_pending",
                    "계정 데이터를 준비하고 있어요. 연결되면 자동으로 재시도합니다. 잠시 후 다시 확인해 주세요.",
                )
            }

            try {
                val db = AppDatabase.getDatabase(context.applicationContext)
                val memoDao = db.memoDao()
                val userStyleDao = db.userStyleDao()
                val dailyEntryDao = db.dailyEntryDao()
                val weekSummaryDao = db.weekSummaryDao()

                val deletedMemoIds = memoDao.getDeletedMemos().map { it.id }
                val editedMemos = memoDao.getEditedMemos()
                val deletedStyleIds = userStyleDao.getDeletedStyles().map { it.styleId }
                val editedStyles = userStyleDao.getEditedStyles()
                val deletedEntryDates = dailyEntryDao.getDeletedEntries().map { it.date }
                val editedEntries = dailyEntryDao.getEditedEntries()
                val deletedSummaryStartDates = weekSummaryDao.getDeletedSummaries().map { it.startDate }
                val editedSummaryEntities = weekSummaryDao.getEditedSummaries()
                val editedSummaries = editedSummaryEntities.map { it.weekSummary }

                val request = buildSyncRequest(
                    deletedMemoIds,
                    deletedStyleIds,
                    deletedEntryDates,
                    deletedSummaryStartDates,
                    editedMemos,
                    editedStyles,
                    editedEntries,
                    editedSummaries,
                )
                if (
                    SessionManager.getUserId() != userId ||
                    !LocalDataOwnerPrefs.isReadyFor(context.applicationContext, userId)
                ) {
                    return@withContext BackupSyncResult(
                        false,
                        "account_changed",
                        "동기화 중 로그인 계정이 변경됨",
                    )
                }
                val response = ApiClient.api.syncData("Bearer $token", request)
                val body = response.body()
                if (!response.isSuccessful || body?.status != "success") {
                    return@withContext BackupSyncResult(
                        false,
                        "server_error",
                        body?.message ?: "서버 응답 없음 (${response.code()})",
                    )
                }

                if (
                    SessionManager.getUserId() != userId ||
                    !LocalDataOwnerPrefs.isReadyFor(context.applicationContext, userId)
                ) {
                    return@withContext BackupSyncResult(
                        false,
                        "account_changed",
                        "동기화 중 로그인 계정이 변경됨",
                    )
                }

                db.withTransaction {
                    if (
                        SessionManager.getUserId() != userId ||
                        !LocalDataOwnerPrefs.isReadyFor(context.applicationContext, userId)
                    ) {
                        throw AccountChangedException()
                    }

                    memoDao.resetDeletedFlags(deletedMemoIds)
                    memoDao.resetEditedFlags(editedMemos.map { it.id })
                    userStyleDao.resetDeletedFlags(deletedStyleIds)
                    userStyleDao.resetEditedFlags(editedStyles.map { it.styleId })
                    dailyEntryDao.resetDeletedFlags(deletedEntryDates)
                    dailyEntryDao.resetEditedFlags(editedEntries.map { it.date })
                    weekSummaryDao.resetDeletedFlags(deletedSummaryStartDates)
                    weekSummaryDao.resetEditedFlags(editedSummaries.map { it.startDate })

                    if (
                        SessionManager.getUserId() != userId ||
                        !LocalDataOwnerPrefs.isReadyFor(context.applicationContext, userId)
                    ) {
                        throw AccountChangedException()
                    }
                }

                BackupSyncResult(true)
            } catch (error: Exception) {
                BackupSyncResult(false, "exception", error.message ?: "알 수 없는 오류")
            }
        }
    }

    private class AccountChangedException : IllegalStateException()
}
