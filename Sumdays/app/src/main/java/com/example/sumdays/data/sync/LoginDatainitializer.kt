
package com.example.sumdays.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.*
import com.example.sumdays.data.style.StylePrompt
import com.example.sumdays.mission.MissionConfig
import com.example.sumdays.shop.PointWalletRepository
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class LoginDataInitializer(
    context: Context
) {
    private val appContext = context.applicationContext
    private val gson = Gson()

    suspend fun initialize(
        expectedUserId: Int,
        serverData: SyncFetchResponse
    ) = withContext(Dispatchers.IO) {

        val db = AppDatabase.getDatabase(appContext)

        // 서버 데이터를 Room 엔티티로 변환
        val memos = serverData.memo.map { p ->
            Memo(
                id = p.room_id,
                content = p.content ?: "",
                timestamp = p.timestamp ?: "",
                date = p.date,
                order = p.memo_order,
                type = p.type ?: "text",
                isEdited = false,
                isDeleted = false
            )
        }

        val entries = serverData.dailyEntry.map { p ->
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

        val styles = serverData.userStyle.map { p ->
            UserStyle(
                styleId = p.styleId,
                styleName = p.styleName,
                styleVector = p.styleVector,
                styleExamples = p.styleExamples,
                stylePrompt = gson.fromJson(
                    gson.toJson(p.stylePrompt),
                    StylePrompt::class.java
                ),
                sampleDiary = p.sampleDiary,
                isEdited = false,
                isDeleted = false
            )
        }

        val summaries = serverData.weekSummary.map { p ->
            WeekSummaryEntity(
                startDate = p.startDate,
                weekSummary = convertToWeekSummary(p),
                isEdited = false,
                isDeleted = false
            )
        }

        // 기존 데이터 삭제 + 서버 데이터 삽입을 하나의 트랜잭션으로 처리
        db.withTransaction {
            check(SessionManager.getUserId() == expectedUserId) {
                "로그인 계정이 변경되었습니다."
            }

            db.memoDao().clearAll()
            db.dailyEntryDao().clearAll()
            db.userStyleDao().clearAll()
            db.weekSummaryDao().clearAll()

            db.memoDao().insertAll(memos)
            db.dailyEntryDao().insertAll(entries)
            db.userStyleDao().insertAll(styles)
            db.weekSummaryDao().insertAll(summaries)

            check(SessionManager.getUserId() == expectedUserId) {
                "로그인 계정이 변경되었습니다."
            }
        }

        // SharedPreferences 초기화
        check(SessionManager.getUserId() == expectedUserId)
        PrefManager.resetLocalPrefs(appContext)
        PrefManager.initServerPrefs(appContext, serverData.prefs)

        // 상점 데이터 갱신
        if (MissionConfig.USE_SERVER_MISSIONS) {
            try {
                PointWalletRepository(appContext).refreshShopCatalog()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("LoginDataInitializer", "상점 데이터 갱신 실패", e)
            }
        }

        check(SessionManager.getUserId() == expectedUserId)
    }

    private fun convertToWeekSummary(
        p: WeekSummaryPayload
    ): WeekSummary {
        return WeekSummary(
            startDate = p.startDate,
            endDate = p.endDate,
            diaryCount = p.diaryCount,
            emotionAnalysis = gson.fromJson(
                gson.toJson(p.emotionAnalysis),
                EmotionAnalysis::class.java
            ),
            highlights = gson.fromJson(
                gson.toJson(p.highlights),
                Array<Highlight>::class.java
            ).toList(),
            insights = gson.fromJson(
                gson.toJson(p.insights),
                Insights::class.java
            ),
            summary = gson.fromJson(
                gson.toJson(p.summary),
                SummaryDetails::class.java
            )
        )
    }
}
