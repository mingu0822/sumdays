package com.example.sumdays.social

import FriendRequest
import com.example.sumdays.network.apiService.FriendRequestResponse
import com.example.sumdays.network.apiService.FriendResponse

fun FriendRequestResponse.toDomain(): FriendRequest {
    return FriendRequest(
        id = this.id,
        nickname = this.nickname ?: "이름 없음", // Null 방어 로직
    )
}

fun FriendResponse.toUser(): SocialUser {
    return SocialUser(
        id = this.id,
        name = this.nickname ?: "이름 없음",
        profileEmoji = "😊",
        summary = "",
        isFavorite  = false
    )
}
