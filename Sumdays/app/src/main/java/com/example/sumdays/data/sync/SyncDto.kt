package com.example.sumdays.data.sync

import com.example.sumdays.data.Memo
import com.example.sumdays.data.DailyEntry
import com.example.sumdays.data.UserStyle
import com.example.sumdays.data.WeekSummary

// ------------------ 최상위 Request ------------------
data class SyncRequest(
    val deleted: DeletedPayload?,
    val edited: EditedPayload?
)


data class SyncFetchResponse(
    val memo: List<MemoPayload>,
    val dailyEntry: List<DailyEntryPayload>,
    val weekSummary: List<WeekSummaryPayload>,
    val userStyle: List<UserStylePayload>
)

// ------------------ 삭제 데이터 (ID 리스트만) ------------------
data class DeletedPayload(
    val memo: List<Int>?,
    val userStyle: List<Long>?,
    val dailyEntry: List<String>?,
    val weekSummary: List<String>?
)

// ------------------ 수정/생성 데이터 (객체 리스트) ------------------
data class EditedPayload(
    val memo: List<MemoPayload>?,
    val userStyle: List<UserStylePayload>?,
    val dailyEntry: List<DailyEntryPayload>?,
    val weekSummary: List<WeekSummaryPayload>?
)

// ------------------ Memo 데이터 ------------------
data class MemoPayload(
    val room_id: Int,
    val content: String?,
    val timestamp: String?,
    val date: String,
    val memo_order: Int,
    val type: String?
)

// ------------------ DailyEntry 데이터 ------------------
data class DailyEntryPayload(
    val date: String,
    val diary: String?,
    val keywords: String?,
    val aiComment: String?,
    val emotionScore: Double?,
    val emotionIcon: String?,
    val themeIcon: String?,
    val photoUrls: String?,
    val is_allowd: Boolean?
)

// ------------------ WeekSummary 데이터 ------------------
data class WeekSummaryPayload(
    val startDate: String,
    val endDate: String,
    val diaryCount: Int,
    val emotionAnalysis: Any,
    val highlights: Any,
    val insights: Any,
    val summary: Any
)

// ------------------ UserStyle 데이터 ------------------
data class UserStylePayload(
    val styleId: Long,
    val styleName: String,
    val styleVector: List<Float>,
    val styleExamples: List<String>,
    val stylePrompt: Any, // Gson이 JSON → Any 로 자동 매핑
    val sampleDiary: String
)

// ------------------ 서버 응답 ------------------
data class SyncResponse(
    val status: String,
    val message: String
)



// request data 형태로 보내주는 함수
fun buildSyncRequest(
    deletedMemoIds: List<Int>,
    deletedStyleIds: List<Long>,
    deletedEntryDates: List<String>,
    deletedSummaryStartDates: List<String>,

    editedMemos: List<Memo>,                    // ← Room Memo
    editedStyles: List<UserStyle>,              // ← Room UserStyle
    editedEntries: List<DailyEntry>,            // ← Room DailyEntry
    editedSummaries: List<WeekSummary>          // ← Room WeekSummary
): SyncRequest {

    val deletedPayload = DeletedPayload(
        memo = deletedMemoIds.ifEmpty { null },
        userStyle = deletedStyleIds.ifEmpty { null },
        dailyEntry = deletedEntryDates.ifEmpty { null },
        weekSummary = deletedSummaryStartDates.ifEmpty { null }
    )

    // ----- Room → Payload 변환 -----
    val memoPayloads = editedMemos.map {
        MemoPayload(
            room_id = it.id,
            date = it.date,
            memo_order = it.order,
            content = it.content,
            timestamp = it.timestamp,
            type = it.type
        )
    }

    val stylePayloads = editedStyles.map {
        UserStylePayload(
            styleId = it.styleId.toLong(),
            styleName = it.styleName,
            styleVector = it.styleVector,
            styleExamples = it.styleExamples,
            stylePrompt = it.stylePrompt,
            sampleDiary = it.sampleDiary
        )
    }

    val entryPayloads = editedEntries.map {
        DailyEntryPayload(
            date = it.date,
            diary = it.diary,
            keywords = it.keywords,
            aiComment = it.aiComment,
            emotionScore = it.emotionScore,
            emotionIcon = it.emotionIcon,
            themeIcon = it.themeIcon,
            photoUrls = it.photoUrls,
            is_allowd = it.isAllowed,
        )
    }

    val summaryPayloads = editedSummaries.map {
        WeekSummaryPayload(
            startDate = it.startDate,
            endDate = it.endDate,
            diaryCount = it.diaryCount,
            emotionAnalysis = it.emotionAnalysis,
            highlights = it.highlights,
            insights = it.insights,
            summary = it.summary
        )
    }

    val editedPayload = EditedPayload(
        memo = memoPayloads.ifEmpty { null },
        userStyle = stylePayloads.ifEmpty { null },
        dailyEntry = entryPayloads.ifEmpty { null },
        weekSummary = summaryPayloads.ifEmpty { null }
    )

    // 둘 다 null이면 서버에 보낼 필요 없음
    return SyncRequest(
        deleted = if (
            deletedMemoIds.isEmpty() &&
            deletedStyleIds.isEmpty() &&
            deletedEntryDates.isEmpty() &&
            deletedSummaryStartDates.isEmpty()
        ) null else deletedPayload,

        edited = if (
            editedMemos.isEmpty() &&
            editedStyles.isEmpty() &&
            editedEntries.isEmpty() &&
            editedSummaries.isEmpty()
        ) null else editedPayload
    )
}
