package com.example.sumdays.data.repository

import com.example.sumdays.data.DailyEntry
import com.example.sumdays.data.EmojiData
import com.example.sumdays.data.dao.DailyEntryDao
import kotlinx.coroutines.flow.Flow

// ⭐ DB 작업만 전담하는 새로운 Repository
class DailyEntryRepository (
    private val dao: DailyEntryDao
) {
    // 1. 일기 내용 저장 (DAO의 트랜잭션 함수 사용)
    suspend fun saveDiary(date: String, content: String) {
        dao.updateEntry(date = date, diary = content)
    }

    // 2. 일기 조회 (Flow 반환)
    fun getEntry(date: String): Flow<DailyEntry?> {
        return dao.getEntry(date)
    }

    // 2-1. 일기 내용 조회 (Worker용 Suspend 반환)
    suspend fun getEntrySnapshot(date: String): DailyEntry? {
        return dao.getEntrySnapshot(date)
    }

    // 3. 모든 작성 날짜 조회 (스케줄러/통계용)
    suspend fun getAllWrittenDates(): List<String> {
        return dao.getAllWrittenDates()
    }

    // 4. 월별 이모지 데이터 조회
    fun getMonthlyEmojis(fromDate: String, toDate: String): Flow<List<EmojiData>> {
        return dao.getMonthlyEmojis(fromDate, toDate)
    }

    // 5. 일기 삭제 (Soft Delete)
    suspend fun deleteEntry(date: String) {
        dao.deleteEntry(date)
    }

    // 6. AI 분석 결과 등 전체 필드 업데이트가 필요할 때 사용
    suspend fun updateEntryFull(
        date: String,
        diary: String? = null,
        keywords: String? = null,
        aiComment: String? = null,
        emotionScore: Double? = null,
        emotionIcon: String? = null,
        themeIcon: String? = null
    ) {
        dao.updateEntry(
            date = date,
            diary = diary,
            keywords = keywords,
            aiComment = aiComment,
            emotionScore = emotionScore,
            emotionIcon = emotionIcon,
            themeIcon = themeIcon
        )
    }

    fun search(q: String) = dao.searchEntries(q)

    //  기간 내 일기 조회 함수
    suspend fun getEntriesBetween(startDate: String, endDate: String): List<DailyEntry> {
        return dao.getEntriesBetween(startDate, endDate)
    }

    // 백업 관련 함수들 (필요 시 ViewModel에서 호출)
    suspend fun getDeletedEntries() = dao.getDeletedEntries()
    suspend fun getEditedEntries() = dao.getEditedEntries()
    suspend fun resetDeletedFlags(dates: List<String>) = dao.resetDeletedFlags(dates)
    suspend fun resetEditedFlags(dates: List<String>) = dao.resetEditedFlags(dates)
}