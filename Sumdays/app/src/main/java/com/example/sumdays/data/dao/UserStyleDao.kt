package com.example.sumdays.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.sumdays.data.style.UserStyle

@Dao
interface UserStyleDao {

    // 1. 새 스타일 저장 (AI 추출 성공 시)
    @Insert
    suspend fun insertStyleRaw(style: UserStyle) : Long
    suspend fun insertStyle(style: UserStyle) : Long{
        return insertStyleRaw(style.copy(isEdited = true, isDeleted = false))
    }

    // 2. 모든 스타일 목록 조회 (SettingsActivity에서 사용)
    @Query("SELECT * FROM user_style WHERE isDeleted = 0 ORDER BY styleId ASC")
    fun getAllStyles(): LiveData<List<UserStyle>>

    // 3. 스타일 삭제
    @Query("UPDATE user_style SET isDeleted = 1, isEdited = 1 WHERE styleId = :styleId")
    suspend fun markAsDeleted(styleId: Long)
    suspend fun deleteStyle(style: UserStyle) {
        markAsDeleted(style.styleId)
    }

    // 4. 활성 스타일 조회 (UserStats의 activeStyleId를 통해 조회)
    @Query("SELECT * FROM user_style WHERE styleId = :styleId AND isDeleted = 0")
    suspend fun getStyleById(styleId: Long): UserStyle?

    // 5. 모든 스타일 삭제 (계정 탈퇴 시 등 - userId 없이 전체 삭제)
    @Query("DELETE FROM user_style")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<UserStyle>)


    // 서버 sync 용도
    @Query("SELECT * FROM user_style WHERE isDeleted = 1")
    suspend fun getDeletedStyles(): List<UserStyle>

    // 백업용 — 수정된(삭제되지 않은) 스타일만 조회
    @Query("SELECT * FROM user_style WHERE isEdited = 1 AND isDeleted = 0")
    suspend fun getEditedStyles(): List<UserStyle>

    // 백업 후 — 삭제된 항목 실제 제거
    @Query("DELETE FROM user_style WHERE styleId IN (:ids)")
    suspend fun resetDeletedFlags(ids: List<Long>)

    // 백업 후 — 수정 플래그 초기화
    @Query("UPDATE user_style SET isEdited = 0, isDeleted = 0 WHERE styleId IN (:ids)")
    suspend fun resetEditedFlags(ids: List<Long>)

    @Update
    suspend fun updateStyleRaw(style: UserStyle)

    suspend fun updateStyle(style: UserStyle) {
        updateStyleRaw(
            style.copy(
                isEdited = true,
                isDeleted = false
            )
        )
    }
    // 7. 모든 스타일 이름 조회 (자동 이름 증가에 필요)
    @Query("SELECT styleName FROM user_style WHERE isDeleted = 0")
    suspend fun getAllStyleNames(): List<String>

    // 8. 샘플 다이어리 조회
    @Query("""
        UPDATE user_style 
        SET sampleDiary = :diary, isEdited = 1 
        WHERE styleId = :id AND isDeleted = 0
    """)
    suspend fun updateSampleDiary(id: Long, diary: String)

    // 9. 전체 리스트 가져옴(DB 비어있는지 확인 위함)
    @Query("SELECT * FROM user_style WHERE isDeleted = 0")
    suspend fun getAllStylesDirect(): List<UserStyle>

}