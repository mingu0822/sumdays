package com.example.sumdays.data.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.ApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import android.net.Uri
import com.google.gson.Gson
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

data class BackupSyncResult(
    val success: Boolean,
    val type: String? = null,
    val message: String? = null,
)

object BackupSyncRunner {
    private val syncMutex = Mutex()

    suspend fun syncNow(
        context: Context,
        api: ApiService = ApiClient.api,
        db: AppDatabase = AppDatabase.getDatabase(context.applicationContext),
        logoutAfterSync: Boolean = false,
    ): BackupSyncResult = syncMutex.withLock {
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
                val memoDao = db.memoDao()
                val userStyleDao = db.userStyleDao()
                val dailyEntryDao = db.dailyEntryDao()
                val weekSummaryDao = db.weekSummaryDao()

                val deletedMemos = memoDao.getDeletedMemos()
                val deletedMemoIds = deletedMemos.map { it.id }
                val editedMemos = memoDao.getEditedMemos()
                val deletedStyles = userStyleDao.getDeletedStyles()
                val deletedStyleIds = deletedStyles.map { it.styleId }
                val editedStyles = userStyleDao.getEditedStyles()
                val deletedEntries = dailyEntryDao.getDeletedEntries()
                val deletedEntryDates = deletedEntries.map { it.date }
                val editedEntries = dailyEntryDao.getEditedEntries()



                // 사진
                val dailyPhotosPayloads = mutableListOf<DailyPhotosPayload>()
                val photoParts = mutableListOf<MultipartBody.Part>()
                editedEntries.forEach { entry ->
                    val photos = entry.photoUrls
                        ?.split(",")
                        ?.filter { it.isNotBlank() }
                        ?: emptyList()

                    val serverPhotos = photos.filter {
                        it.startsWith("http://") || it.startsWith("https://")
                    }

                    val localPhotos = photos.filter {
                        it.startsWith("content://") || it.startsWith("file://")
                    }

                    val uploadKeys = mutableListOf<String>()

                    localPhotos.forEachIndexed { index, uriString ->
                        val uploadKey = "${entry.date}_${index}.jpg"
                        uploadKeys.add(uploadKey)

                        photoParts.add(
                            uriToMultipart(context.applicationContext, uriString, uploadKey)
                        )
                    }

                    dailyPhotosPayloads.add(
                        DailyPhotosPayload(
                            date = entry.date,
                            serverPhotoUrls = serverPhotos
                                .takeIf { it.isNotEmpty() }
                                ?.joinToString(","),
                            uploadKeys = uploadKeys
                                .takeIf { it.isNotEmpty() }
                        )
                    )
                }




                val deletedSummaries = weekSummaryDao.getDeletedSummaries()
                val deletedSummaryStartDates = deletedSummaries.map { it.startDate }
                val editedSummaryEntities = weekSummaryDao.getEditedSummaries()
                val editedSummaries = editedSummaryEntities.map { it.weekSummary }

                var syncedPhotos: List<SyncedDailyPhotoUrlsPayload>? = null
                val request = buildSyncRequest(
                    deletedMemoIds,
                    deletedStyleIds,
                    deletedEntryDates,
                    deletedSummaryStartDates,
                    editedMemos,
                    editedStyles,
                    editedEntries,
                    editedSummaries,
                    dailyPhotosPayloads
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
                if (request.edited != null || request.deleted != null) {
                    val syncJson = Gson().toJson(request)

                    val syncRequestBody = syncJson.toRequestBody(
                        "application/json".toMediaTypeOrNull()
                    )

                    val response = api.syncData(
                        "Bearer $token",
                        syncRequestBody,
                        photoParts
                    )
                    val body = response.body()
                    if (!response.isSuccessful || body?.status != "success") {
                        return@withContext BackupSyncResult(
                            false,
                            if (response.code() in listOf(408, 429) || response.code() >= 500) "server_retry" else "server_error",
                            body?.message ?: "서버 응답 없음 (${response.code()})",
                        )
                    }
                    syncedPhotos = body.dailyPhotos
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

                    val unchangedEntryDates = dailyEntryDao.getEditedEntries()
                        .filter { it in editedEntries }
                        .map { it.date }
                        .toSet()

                    // Only acknowledge the exact snapshots uploaded. A newer
                    // local edit/deletion must remain pending for the next run.
                    memoDao.resetDeletedFlags(memoDao.getDeletedMemos().filter { it in deletedMemos }.map { it.id })
                    memoDao.resetEditedFlags(memoDao.getEditedMemos().filter { it in editedMemos }.map { it.id })
                    userStyleDao.resetDeletedFlags(userStyleDao.getDeletedStyles().filter { it in deletedStyles }.map { it.styleId })
                    userStyleDao.resetEditedFlags(userStyleDao.getEditedStyles().filter { it in editedStyles }.map { it.styleId })
                    dailyEntryDao.resetDeletedFlags(dailyEntryDao.getDeletedEntries().filter { it in deletedEntries }.map { it.date })
                    dailyEntryDao.resetEditedFlags(unchangedEntryDates.toList())
                    weekSummaryDao.resetDeletedFlags(weekSummaryDao.getDeletedSummaries().filter { it in deletedSummaries }.map { it.startDate })
                    weekSummaryDao.resetEditedFlags(weekSummaryDao.getEditedSummaries().filter { it in editedSummaryEntities }.map { it.startDate })

                    syncedPhotos?.forEach { photo ->
                        if (photo.date in unchangedEntryDates) {
                            dailyEntryDao.updatePhotoUrls(
                                photo.date,
                                photo.photoUrls
                            )
                        }
                    }

                    if (
                        SessionManager.getUserId() != userId ||
                        !LocalDataOwnerPrefs.isReadyFor(context.applicationContext, userId)
                    ) {
                        throw AccountChangedException()
                    }
                }

                if (logoutAfterSync) {
                    // Do not erase anything until every pending change is on
                    // the server. Cancellation cannot split DB/session cleanup.
                    withContext(NonCancellable) {
                        db.withTransaction {
                            if (SessionManager.getUserId() != userId || !LocalDataOwnerPrefs.isReadyFor(context, userId)) {
                                throw AccountChangedException()
                            }
                            if (hasPendingChanges(db)) throw PendingChangesException()
                            memoDao.clearAll()
                            dailyEntryDao.clearAll()
                            userStyleDao.clearAll()
                            weekSummaryDao.clearAll()
                        }
                        LocalDataOwnerPrefs.clear(context)
                        SessionManager.clearSession()
                        WorkManager.getInstance(context).cancelAllWorkByTag(InitialSyncWorker.accountWorkTag(userId))
                    }
                }

                BackupSyncResult(true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                BackupSyncResult(false, "connection_error", "서버에 연결하지 못했어요. 기기의 일기는 보관되어 있습니다.")
            } catch (error: PendingChangesException) {
                BackupSyncResult(false, "changes_pending", "새 변경사항을 저장하고 있어요. 잠시 후 다시 시도해 주세요.")
            } catch (error: Exception) {
                BackupSyncResult(false, "exception", error.message ?: "알 수 없는 오류")
            }
        }
    }

    private suspend fun hasPendingChanges(db: AppDatabase): Boolean =
        db.dailyEntryDao().getEditedEntries().isNotEmpty() || db.dailyEntryDao().getDeletedEntries().isNotEmpty() ||
                db.memoDao().getEditedMemos().isNotEmpty() || db.memoDao().getDeletedMemos().isNotEmpty() ||
                db.userStyleDao().getEditedStyles().isNotEmpty() || db.userStyleDao().getDeletedStyles().isNotEmpty() ||
                db.weekSummaryDao().getEditedSummaries().isNotEmpty() || db.weekSummaryDao().getDeletedSummaries().isNotEmpty()

    private class PendingChangesException : IllegalStateException()
    private class AccountChangedException : IllegalStateException()
}

private fun uriToMultipart(
    context: Context,
    uriString: String,
    uploadKey: String
): MultipartBody.Part {

    val uri = Uri.parse(uriString)

    val bytes = context.contentResolver
        .openInputStream(uri)
        ?.use { it.readBytes() }
        ?: throw IllegalArgumentException("사진을 읽을 수 없습니다: $uri")

    val mimeType =
        context.contentResolver.getType(uri)
            ?: "image/jpeg"

    val requestBody = bytes.toRequestBody(
        mimeType.toMediaTypeOrNull()
    )

    return MultipartBody.Part.createFormData(
        "photos",
        uploadKey,
        requestBody
    )
}