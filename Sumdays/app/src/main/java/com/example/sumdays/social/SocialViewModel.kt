package com.example.sumdays.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.sumdays.network.apiService.FriendInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SocialViewModel(
    private val repository: SocialRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SocialUiState>(SocialUiState.Idle)
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()

    private val allFriendList = mutableListOf<FriendInfo>()

    private val _filteredFriends = MutableStateFlow<List<FriendInfo>>(emptyList())
    val filteredFriends: StateFlow<List<FriendInfo>> = _filteredFriends.asStateFlow()

    private var currentQuery: String = ""

    fun loadFriends() {
        viewModelScope.launch {
            _uiState.value = SocialUiState.Loading

            repository.getMyFriends()
                .onSuccess { friends ->
                    allFriendList.clear()
                    allFriendList.addAll(friends)

                    applyFilter(currentQuery)
                    _uiState.value = SocialUiState.Success(friends)
                }
                .onFailure { e ->
                    _uiState.value = SocialUiState.Error(
                        e.message ?: "알 수 없는 오류가 발생했습니다."
                    )
                }
        }
    }

    fun updateSearchQuery(query: String) {
        currentQuery = query
        applyFilter(query)
    }
    fun removeFriendLocally(friendId: Int) {
        allFriendList.removeAll { it.id == friendId }
        applyFilter(currentQuery)

        _uiState.value = SocialUiState.Success(allFriendList.toList())
    }
    fun addFriendLocally(friend: FriendInfo) {
        // 중복 방지
        if (allFriendList.any { it.id == friend.id }) return

        allFriendList.add(friend)

        applyFilter(currentQuery)

        _uiState.value = SocialUiState.Success(allFriendList.toList())
    }
    private fun applyFilter(query: String) {
        val keyword = query.trim()

        _filteredFriends.value =
            if (keyword.isEmpty()) {
                allFriendList.toList()
            } else {
                allFriendList.filter {
                    it.nickname.contains(keyword, ignoreCase = true)
                }
            }
    }
}

class SocialViewModelFactory(
    private val repository: SocialRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SocialViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SocialViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}