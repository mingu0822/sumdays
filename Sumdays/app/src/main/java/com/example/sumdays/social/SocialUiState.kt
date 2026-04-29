package com.example.sumdays.social

import com.example.sumdays.network.apiService.FriendInfo

sealed class SocialUiState {
    object Idle : SocialUiState()
    object Loading : SocialUiState()
    data class Success(val friends: List<FriendInfo>) : SocialUiState()
    data class Error(val message: String) : SocialUiState()
}