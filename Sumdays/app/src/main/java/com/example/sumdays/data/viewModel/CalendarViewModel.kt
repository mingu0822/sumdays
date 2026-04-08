package com.example.sumdays.data.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.data.EmojiData
import kotlinx.coroutines.flow.map

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.Companion.getDatabase(application).dailyEntryDao()

    fun getMonthlyEmojis(fromDate: String, toDate: String): LiveData<Map<String, Pair<Boolean, String?>>> {
        return dao.getMonthlyEmojis(fromDate, toDate)
            .map { list: List<EmojiData> ->
                list.associate {
                    it.date to Pair(!it.diary.isNullOrEmpty(), it.themeIcon)
                }
            }
            .asLiveData()
    }
}