package com.example.sumdays.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.sumdays.daily.memo.Memo
import com.example.sumdays.data.DailyEntry
import com.example.sumdays.data.EmojiData
import kotlinx.coroutines.flow.Flow
import com.example.sumdays.data.AppDatabase

@Dao
interface DailyEntryDao {

    // 특정 날짜의 엔트리(일기) 가져오기
    @Query("SELECT * FROM daily_entry WHERE date = :date AND isDeleted = 0")
    fun getEntry(date: String): Flow<DailyEntry?>

    // Suspend 반환 (Worker/Repository 내부 로직용 - Flow 아님)
    @Query("SELECT * FROM daily_entry WHERE date = :date AND isDeleted = 0")
    suspend fun getEntrySnapshot(date: String): DailyEntry?

    @Query("SELECT date FROM daily_entry WHERE diary IS NOT NULL AND diary != '' AND isDeleted = 0 ORDER BY date DESC")
    fun getAllWrittenDates(): List<String> // 일기 내용이 비어있지 않은 모든 날짜를 최신순으로 조회


    /////////// update
    // 1. 날짜가 없으면 초기값으로 삽입하는 함수
    @Query("""
    INSERT OR IGNORE INTO daily_entry 
    (date, diary, keywords, aiComment, emotionScore, emotionIcon, themeIcon, photoUrls, isEdited, isDeleted)
    VALUES (:date, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0)
    """)
    suspend fun _insertInitialEntry(date: String)

    // 2. 항목을 업데이트하는 함수
    @Query("""
    UPDATE daily_entry
    SET 
        diary = COALESCE(:diary, diary),
        keywords = COALESCE(:keywords, keywords),
        aiComment = COALESCE(:aiComment, aiComment),
        emotionScore = COALESCE(:emotionScore, emotionScore),
        emotionIcon = COALESCE(:emotionIcon, emotionIcon),
        themeIcon = COALESCE(:themeIcon, themeIcon),
        photoUrls = COALESCE(:photoUrls, photoUrls),
        isEdited = 1,
        isDeleted = 0
    WHERE date = :date
    """)
    suspend fun _updateEntryDetails(
        date: String,
        diary: String? = null,
        keywords: String? = null,
        aiComment: String? = null,
        emotionScore: Double? = null,
        emotionIcon: String? = null,
        themeIcon: String? = null,
        photoUrls: String? = null // ★★★ 파라미터 추가 ★★★
    )

    // 3. 두 함수를 트랜잭션으로 묶어 호출
    @Transaction
    suspend fun updateEntry(
        date: String,
        diary: String? = null,
        keywords: String? = null,
        aiComment: String? = null,
        emotionScore: Double? = null,
        emotionIcon: String? = null,
        themeIcon: String? = null,
        photoUrls: String? = null // ★★★ 파라미터 추가 ★★★
    ) {
        _insertInitialEntry(date) // 1. 먼저 삽입 시도 (무시될 수 있음)
        // ★★★ photoUrls 전달 ★★★
        _updateEntryDetails(date, diary, keywords, aiComment, emotionScore, emotionIcon, themeIcon, photoUrls) // 2. 업데이트
    }

    @Query("DELETE FROM daily_entry")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DailyEntry>)


    // 해당 날짜의 일기 삭제

    @Query("UPDATE daily_entry SET isDeleted = 1, isEdited = 1 WHERE date = :date")
    suspend fun markAsDeleted(date: String)

    // deleteEntry → soft delete로 변경
    suspend fun deleteEntry(date : String) {
        markAsDeleted(date)
    }

    @Query("SELECT date, diary, themeIcon FROM daily_entry WHERE date BETWEEN :fromDate AND :toDate AND isDeleted = 0")
    fun getMonthlyEmojis(fromDate : String, toDate : String): Flow<List<EmojiData>>

    // [추가] 특정 기간 내의 모든 일기 가져오기
    @Query("SELECT * FROM daily_entry WHERE date >= :startDate AND date <= :endDate")
    suspend fun getEntriesBetween(startDate: String, endDate: String): List<DailyEntry>

    ////// To Server
    // 백업용 — 변경된(수정·삭제된) 일기만 가져오기
    @Query("SELECT * FROM daily_entry WHERE isDeleted = 1")
    suspend fun getDeletedEntries(): List<DailyEntry>

    @Query("SELECT * FROM daily_entry WHERE isEdited = 1 AND isDeleted = 0")
    suspend fun getEditedEntries(): List<DailyEntry>

    // 백업 후 flag 초기화 (deleted, edited)
    @Query("DELETE FROM daily_entry WHERE date IN (:dates)")
    suspend fun resetDeletedFlags(dates: List<String>)

    @Query("UPDATE daily_entry SET isEdited = 0, isDeleted = 0 WHERE date IN (:dates)")
    suspend fun resetEditedFlags(dates: List<String>)

    @Query("""
        SELECT * FROM daily_entry
        WHERE isDeleted = 0
          AND (
            diary LIKE '%' || :q || '%'
            OR keywords LIKE '%' || :q || '%'
            OR aiComment LIKE '%' || :q || '%'
          )
        ORDER BY date DESC
    """)
    fun searchEntries(q: String): LiveData<List<DailyEntry>>
}