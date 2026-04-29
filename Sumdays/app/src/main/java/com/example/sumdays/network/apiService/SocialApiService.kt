package com.example.sumdays.network.apiService

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import retrofit2.Response
import retrofit2.http.*

interface SocialApiService {

    // 1. 친구 요청하기
    @POST("/api/friend/request")
    suspend fun requestFriend(
        @Body body: RequestFriendBody
    ): Response<ApiResponse<FriendInfo?>>

    // 2. 친구 요청 취소
    @HTTP(method = "DELETE", path = "/api/friend/request/cancel", hasBody = true)
    suspend fun cancelRequest(
        @Body body: CancelRequestBody
    ): Response<ApiResponse<Unit?>>

    // 3. 받은 요청 처리 (수락/거절)
    @PATCH("/api/friend/request")
    suspend fun handleRequest(
        @Body body: HandleRequestBody
    ): Response<ApiResponse<FriendInfo?>>

    // 4. 친구 요청 조회
    @GET("/api/friend/requests")
    suspend fun getFriendRequests(): Response<ApiResponse<FriendRequestData>>

    // 5. 내 전체 친구 목록 조회
    @GET("/api/friend/friends")
    suspend fun getMyFriends(): Response<ApiResponse<List<FriendInfo>>>

    // 6. 친구 삭제
    @DELETE("/api/friend/friends/{friendId}")
    suspend fun deleteFriend(
        @Path("friendId") friendId: Int
    ): Response<ApiResponse<Unit?>>
}

// 공통 응답 DTO
data class ApiResponse<T>(
    val success: Boolean,
    val code: String,
    val message: String,
    val data: T?
)

// Request Body DTO
data class RequestFriendBody(
    val receiverEmail: String
)

data class CancelRequestBody(
    val receiverId: Int
)

data class HandleRequestBody(
    val requesterId: Int,
    val action: String // "ACCEPT" or "REJECT"
)

// Response data
data class FriendRequestData(
    val received: List<FriendRequest>,
    val sent: List<FriendRequest>
)

data class FriendRequest(
    val userId: Int,
    val nickname: String,
    val profile_image_url: String?
)

// 친구 목록 data
@Parcelize
data class FriendInfo(
    val id: Int,
    val nickname: String,
    val profileImageUrl: String?,
    val createdAt: String, // yyyy-mm-dd
    val countDiaries: Int,
    val streak: Int,
    val countWeeklySummaries: Int,
    val lastDiaryUpdateDate: String?
) : Parcelable