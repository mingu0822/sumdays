package com.example.sumdays.social

import FriendRequest
import com.example.sumdays.network.apiService.FriendRequestResponse

fun FriendRequestResponse.toDomain(): FriendRequest {
    return FriendRequest(
        id = this.id,
        nickname = this.nickname ?: "이름 없음", // Null 방어 로직
    )
}

