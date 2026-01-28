package com.example.sumdays.daily.memo

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

// ViewModel은 액티비티의 생명주기를 넘어 데이터를 유지
class MemoViewModel(private val repository: MemoRepository) : ViewModel() {

    // 특정 날짜의 메모 리스트를 LiveData로 변환하여 UI가 관찰하도록 함
    fun getMemosForDate(date: String): LiveData<List<Memo>> {
        return repository.getMemosForDate(date).asLiveData()
    }

    fun insert(memo: Memo) = viewModelScope.launch {
        repository.insert(memo)
    }

    fun update(memo: Memo) = viewModelScope.launch {
        repository.update(memo)
    }

    fun updateAll(memos: List<Memo>) = viewModelScope.launch {
        repository.updateAll(memos)
    }

    fun delete(memo: Memo) = viewModelScope.launch {
        repository.delete(memo)
    }
}

// ViewModel을 인스턴스화하기 위한 팩토리 클래스
class MemoViewModelFactory(private val repository: MemoRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MemoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MemoViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}