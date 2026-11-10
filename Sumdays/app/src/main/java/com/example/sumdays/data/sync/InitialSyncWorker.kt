package com.example.sumdays.data.sync

// --- 너의 엔티티 & DAO 관련 ---

// --- 네트워크 ---
import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.data.DailyEntry
import com.example.sumdays.data.EmotionAnalysis
import com.example.sumdays.data.Highlight
import com.example.sumdays.data.Insights
import com.example.sumdays.data.Memo
import com.example.sumdays.data.SummaryDetails
import com.example.sumdays.data.UserStyle
import com.example.sumdays.data.WeekSummary
import com.example.sumdays.data.WeekSummaryEntity
import com.example.sumdays.data.style.StylePrompt
import com.example.sumdays.mission.MissionConfig
import com.example.sumdays.network.ApiClient
import com.example.sumdays.settings.prefs.ProfileImagePrefs
import com.example.sumdays.shop.PointWalletRepository
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class InitialSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private fun convertToWeekSummary(p: WeekSummaryPayload, gson: Gson): WeekSummary {
        val emotionAnalysis = gson.fromJson(
            gson.toJson(p.emotionAnalysis),
            EmotionAnalysis::class.java
        )

        val highlights = gson.fromJson(
            gson.toJson(p.highlights),
            Array<Highlight>::class.java
        ).toList()

        val insights = gson.fromJson(
            gson.toJson(p.insights),
            Insights::class.java
        )

        val summary = gson.fromJson(
            gson.toJson(p.summary),
            SummaryDetails::class.java
        )

        return WeekSummary(
            startDate = p.startDate,
            endDate = p.endDate,
            diaryCount = p.diaryCount ?: 0,
            emotionAnalysis = emotionAnalysis,
            highlights = highlights,
            insights = insights,
            summary = summary
        )
    }


    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        try {
            Log.d("initWork","dowork (back to frontr")

            val expectedUserId = inputData.getInt(KEY_EXPECTED_USER_ID, -1)
            if (expectedUserId < 0 || SessionManager.getUserId() != expectedUserId) {
                return@withContext Result.failure()
            }

            // 0) login 되어있는지 확인
            val token = SessionManager.getToken()
            if(token == null) {
                val serverFailData = workDataOf(
                    "type" to "token_error",
                    "message" to "token이 유효하지 않음"
                )
                return@withContext Result.failure(serverFailData)
            }
            val tokenHeader = "Bearer ${token}"

            // 1) 서버에서 전체 데이터 가져오기
            val response = ApiClient.api.fetchServerData(tokenHeader)
            if (!response.isSuccessful) {
                return@withContext if (InitialSyncRetryPolicy.shouldRetry(response.code())) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf("message" to "초기 동기화 거절: ${response.code()}"))
                }
            }
            if (response.body() == null) return@withContext Result.failure()
            val serverData = response.body()!!

            if (SessionManager.getUserId() != expectedUserId) {
                return@withContext Result.failure()
            }


            /// printEditedUserStyles(frp = serverData)

            // 2) Room DB 준비
            val db = AppDatabase.getDatabase(applicationContext)
            val memoDao = db.memoDao()
            val dailyDao = db.dailyEntryDao()
            val styleDao = db.userStyleDao()
            val weekDao = db.weekSummaryDao()

            // 3) 삭제와 삽입을 한 트랜잭션으로 처리해 중간 상태를 노출하지 않습니다.
            db.withTransaction {
                if (SessionManager.getUserId() != expectedUserId) {
                    throw AccountChangedException()
                }

                memoDao.clearAll()
                dailyDao.clearAll()
                styleDao.clearAll()
                weekDao.clearAll()

                // --- 서버 데이터 삽입 ---
                // Memo
                val memoList = serverData.memo.map { payload ->
                    Memo(
                        id = payload.room_id,
                        content = payload.content ?: "",
                        timestamp = payload.timestamp ?: "",
                        date = payload.date,
                        order = payload.memo_order,
                        type = payload.type ?: "text",
                        isEdited = false,
                        isDeleted = false
                    )
                }
                memoDao.insertAll(memoList)

                // DailyEntry
                val dailyEntryList = serverData.dailyEntry.map { p ->
                    DailyEntry(
                        date = p.date,
                        diary = p.diary,
                        keywords = p.keywords,
                        aiComment = p.aiComment,
                        emotionScore = p.emotionScore,
                        emotionIcon = p.emotionIcon,
                        themeIcon = p.themeIcon,
                        photoUrls = p.photoUrls,
                        isAllowed = (p.is_allowed ?: 0) != 0,
                        isEdited = false,
                        isDeleted = false
                    )
                }

                dailyEntryList.forEach { entry ->
                    Log.d("Sync-DailyEntry", """
        ---- DailyEntry ----
        date = ${entry.date}
        diary = ${entry.diary}
        keywords = ${entry.keywords}
        aiComment = ${entry.aiComment}
        emotionScore = ${entry.emotionScore}
        emotionIcon = ${entry.emotionIcon}
        themeIcon = ${entry.themeIcon}
        photoUrls = ${entry.photoUrls}
        isAllowed = ${entry.isAllowed}
        isEdited = ${entry.isEdited}
        isDeleted = ${entry.isDeleted}
    """.trimIndent())
                }

                dailyDao.insertAll(dailyEntryList)

                // UserStyle
                val gson = Gson()
                val styleList = serverData.userStyle.map { p ->
                    val stylePrompt = gson.fromJson(
                        gson.toJson(p.stylePrompt),
                        StylePrompt::class.java
                    )
                    UserStyle(
                        styleId = p.styleId,
                        styleName = p.styleName,
                        styleVector = p.styleVector,
                        styleExamples = p.styleExamples,
                        stylePrompt = stylePrompt,
                        sampleDiary = p.sampleDiary,
                        isEdited = false,
                        isDeleted = false
                    )
                }
                styleDao.insertAll(styleList)

                // WeekSummary
                val weekSummaryList = serverData.weekSummary.map { payload ->
                    WeekSummaryEntity(
                        startDate = payload.startDate,
                        weekSummary = convertToWeekSummary(payload, gson),
                        isEdited = false,
                        isDeleted = false
                    )
                }
                weekDao.insertAll(weekSummaryList)

                if (SessionManager.getUserId() != expectedUserId) {
                    throw AccountChangedException()
                }
            }

            if (SessionManager.getUserId() != expectedUserId) {
                return@withContext Result.failure()
            }

            // 4) pref 초기화
            // TODO : 다른 pref 초기화
            ProfileImagePrefs.clear(applicationContext)
            ProfileImagePrefs.saveBase64Image(
                applicationContext,
                serverData.profileImage
            )
            val file = java.io.File(applicationContext.filesDir, "profile_image.jpg")
            Log.d(
                "ProfileDebug",
                "exists=${file.exists()}, " +
                        "size=${file.length()}, " +
                        "mode=${ProfileImagePrefs.getMode(applicationContext)}, " +
                        "uri=${ProfileImagePrefs.getPhotoUri(applicationContext)}"
            )

            if (MissionConfig.USE_SERVER_MISSIONS) {
                runCatching {
                    PointWalletRepository(applicationContext).refreshShopCatalog()
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    Log.w("InitialSyncWorker", "상점 인벤토리 초기 동기화 실패", error)
                }
            }

            if (SessionManager.getUserId() != expectedUserId) {
                return@withContext Result.failure()
            }
            LocalDataOwnerPrefs.markReady(applicationContext, expectedUserId)

            return@withContext Result.success()

        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // Keep ownership unready; WorkManager resumes after connectivity recovers.
            Log.w("InitialSyncWorker", "초기 동기화 연결 실패: 자동 재시도", e)
            return@withContext Result.retry()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.failure()
        }
    }

    companion object {
        const val KEY_EXPECTED_USER_ID = "expected_user_id"

        fun accountWorkTag(userId: Int): String = "account_work_user_$userId"
    }

    private class AccountChangedException : IllegalStateException()
}
