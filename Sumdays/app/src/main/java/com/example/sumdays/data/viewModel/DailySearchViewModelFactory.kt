package com.example.sumdays.search

import DailySearchViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.sumdays.data.repository.DailyEntryRepository

class DailySearchViewModelFactory(
    private val repo: DailyEntryRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DailySearchViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DailySearchViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
