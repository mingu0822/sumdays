package com.example.sumdays.daily.memo

import com.example.sumdays.data.dao.MemoDao
import kotlinx.coroutines.flow.Flow

class MemoRepository(private val memoDao: MemoDao) {

    suspend fun insert(memo: Memo) {
        memoDao.insert(memo)
    }

    fun getMemosForDate(date: String): Flow<List<Memo>> {
        return memoDao.getMemosForDate(date)
    }

    suspend fun update(memo: Memo) {
        memoDao.update(memo)
    }

    suspend fun updateAll(memos: List<Memo>) {
        memoDao.updateAll(memos)
    }

    suspend fun delete(memo: Memo) {
        memoDao.delete(memo)
    }
}