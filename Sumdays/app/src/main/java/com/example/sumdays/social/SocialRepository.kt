package com.example.sumdays.social

import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.FriendInfo

class SocialRepository {

    suspend fun getMyFriends(): Result<List<FriendInfo>> {
        return try {
            val response = ApiClient.socialApi.getMyFriends()

            if (response.success) {
                Result.success(response.friends)
            } else {
                Result.failure(Exception("친구 목록을 불러오지 못했습니다."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}