package com.example.sumdays.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.sumdays.daily.memo.Memo
import kotlinx.coroutines.flow.Flow

// Room의 DAO(Data Access Object) 인터페이스
@Dao
interface MemoDao {

    // insert 시 새로 생성된 메모이므로 isEdited = true, isDeleted = false
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRaw(memo: Memo)

    suspend fun insert(memo: Memo) {
        insertRaw(memo.copy(isEdited = true, isDeleted = false))
    }

    // 특정 날짜의 메모만 조회 (삭제된 메모는 제외)
    @Query("SELECT * FROM memo_table WHERE date = :date AND isDeleted = 0 ORDER BY memo_order ASC")
    fun getMemosForDate(date: String): Flow<List<Memo>>

    // 메모를 수정할 때 isEdited = true로 표시
    @Update
    suspend fun updateRaw(memo: Memo)

    suspend fun update(memo: Memo) {
        updateRaw(memo.copy(isEdited = true))
    }

    // 여러 메모의 순서를 한 번에 업데이트할 때도 isEdited = true로 표시
    @Update
    suspend fun updateAllRaw(memos: List<Memo>)

    suspend fun updateAll(memos: List<Memo>) {
        updateAllRaw(memos.map { it.copy(isEdited = true) })
    }

    // 삭제 대신 soft delete (isDeleted = 1, isEdited = 1)
    @Query("UPDATE memo_table SET isDeleted = 1, isEdited = 1 WHERE id = :id")
    suspend fun markAsDeleted(id: Int)
    suspend fun delete(memo: Memo) {
        markAsDeleted(memo.id)
    }
    @Query("DELETE FROM memo_table")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<Memo>)

    // reminder에서 memo를 추가할 때 order 계산용 (삭제된 메모 제외)
    @Query("SELECT COUNT(*) FROM memo_table WHERE date = :date AND isDeleted = 0")
    suspend fun getMemoCountByDate(date: String): Int

    // 백업용 — 변경된(수정·삭제된) 메모만 가져오기
    @Query("SELECT * FROM memo_table WHERE isDeleted = 1")
    suspend fun getDeletedMemos(): List<Memo>

    @Query("SELECT * FROM memo_table WHERE isEdited = 1 AND isDeleted = 0")
    suspend fun getEditedMemos(): List<Memo>

    // 백업 후 flag 초기화 (deleted, edited)
    @Query("DELETE FROM memo_table WHERE id IN (:ids)")
    suspend fun resetDeletedFlags(ids: List<Int>)

    @Query("UPDATE memo_table SET isEdited = 0, isDeleted = 0 WHERE id IN (:ids)")
    suspend fun resetEditedFlags(ids: List<Int>)
}