package com.example.sumdays.data.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.data.EmojiData
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DailyEntryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.Companion.getDatabase(application).dailyEntryDao()

    fun getEntry(date: String) =
        dao.getEntry(date).asLiveData()

    fun getMonthlyEmojis(fromDate: String, toDate: String): LiveData<Map<String, Pair<Boolean, String?>>> {
        return dao.getMonthlyEmojis(fromDate, toDate)
            .map { list: List<EmojiData> ->
                list.associate {
                    it.date to Pair(!it.diary.isNullOrEmpty(), it.themeIcon)
                }
            }
            .asLiveData()
    }

    // 부분 업데이트 (선택 인자 방식)
    fun updateEntry(
        date: String,
        diary: String? = null,
        keywords: String? = null,
        aiComment: String? = null,
        emotionScore: Double? = null,
        emotionIcon: String? = null,
        themeIcon: String? = null,
        photoUrls: String? = null
    ) = viewModelScope.launch {
        dao.updateEntry(
            date = date,
            diary = diary,
            keywords = keywords,
            aiComment = aiComment,
            emotionScore = emotionScore,
            emotionIcon = emotionIcon,
            themeIcon = themeIcon ,
            photoUrls = photoUrls
        )
    }

    // 모든 작성 날짜를 조회하는 함수 추가 (Strike 계산용)
    fun getAllWrittenDates(): List<String> {
        // ViewModelScope를 사용하지 않음: 호출하는 곳에서 코루틴을 관리하고 동기적으로 결과를 받음
        return dao.getAllWrittenDates()
    }

    fun deleteEntry(date: String) = viewModelScope.launch {
        dao.deleteEntry(date)
    }

}