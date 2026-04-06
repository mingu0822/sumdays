package com.example.sumdays.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.sumdays.data.WeekSummaryEntity
import com.example.sumdays.statistics.WeekSummary

@Dao
interface WeekSummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntity(entity: WeekSummaryEntity)

    suspend fun upsert(summary: WeekSummary) {
        val entity = WeekSummaryEntity(
            startDate = summary.startDate,
            weekSummary = summary,
            isEdited = true,
            isDeleted = false
        )
        upsertEntity(entity)
    }

    @Query("SELECT * FROM weekly_summary WHERE startDate = :startDate AND isDeleted = 0 LIMIT 1")
    suspend fun getWeekSummaryEntity(startDate: String): WeekSummaryEntity?

    suspend fun getWeekSummary(startDate: String): WeekSummary? {
        return getWeekSummaryEntity(startDate)?.weekSummary
    }

    @Query("UPDATE weekly_summary SET isDeleted = 1, isEdited = 1 WHERE startDate = :startDate")
    suspend fun markAsDeleted(startDate: String)

    suspend fun deleteWeekSummary(startDate: String) {
        markAsDeleted(startDate)
    }

    @Query("DELETE FROM weekly_summary")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<WeekSummaryEntity>)

    // 통계 화면 초기 세팅용
    @Query("SELECT startDate FROM weekly_summary WHERE isDeleted = 0 ORDER BY startDate ASC")
    suspend fun getAllDatesAsc(): List<String>

    // 백업용 — 변경된 (수정/삭제된) 데이터만 가져오기
    @Query("SELECT * FROM weekly_summary WHERE isDeleted = 1")
    suspend fun getDeletedSummaries(): List<WeekSummaryEntity>

    @Query("SELECT * FROM weekly_summary WHERE isEdited = 1 AND isDeleted = 0")
    suspend fun getEditedSummaries(): List<WeekSummaryEntity>

    // 백업 후 플래그 초기화
    @Query("DELETE FROM weekly_summary WHERE startDate IN (:dates)")
    suspend fun resetDeletedFlags(dates: List<String>)

    @Query("UPDATE weekly_summary SET isEdited = 0, isDeleted = 0 WHERE startDate IN (:dates)")
    suspend fun resetEditedFlags(dates: List<String>)
}