package com.example.sumdays.social

import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.FriendInfo
import com.example.sumdays.utils.getErrorMessage

class SocialRepository {

    suspend fun getMyFriends(): Result<List<FriendInfo>> {
        return try {
            val response = ApiClient.socialApi.getMyFriends()

            if (response.isSuccessful) {
                val body = response.body()

                if (body?.success == true) {
                    Result.success(body.data ?: emptyList())
                } else {
                    Result.failure(Exception(body?.message ?: "친구 목록을 불러오지 못했습니다."))
                }
            } else {
                Result.failure(Exception(response.getErrorMessage("친구 목록을 불러오지 못했습니다.")))
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}