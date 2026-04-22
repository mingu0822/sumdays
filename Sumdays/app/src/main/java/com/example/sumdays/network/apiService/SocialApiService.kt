package com.example.sumdays.network.apiService

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import retrofit2.Response
import retrofit2.http.*

interface SocialApiService {

    // 1. 친구 요청하기
    @POST("/api/friend/request")
    suspend fun requestFriend(
        @Body body: Map<String, String> // { "receiverEmail": "akd122" }
    ): Response<BaseResponse>

    // 2. 친구 요청 취소
    @HTTP(method = "DELETE", path = "/api/friend/request/cancel", hasBody = true)
    suspend fun cancelRequest(
        @Body body: Map<String, Int> //  { "receiverId": 123 }
    ): Response<BaseResponse>

    // 3. 받은 요청 처리 (수락/거절)
    @PATCH("/api/friend/request/{id}")
    suspend fun handleRequest(
        @Path("id") requestId: Int,
        @Body body: Map<String, String> // { "action": "ACCEPT" }
    ): Response<BaseResponse>

    // 4. 친구 요청 조회
    @GET("/api/friend/requests")
    suspend fun getFriendRequests(): Response<FriendRequestListResponse>

    // 5. 내 전체 친구 목록 조회
    @GET("/api/friend/friends")
    suspend fun getMyFriends(): FriendInfoListResponse

    // 6. 친구 삭제
    @DELETE("/api/friend/friends/{friendId}")
    suspend fun deleteFriend(
        @Path("friendId") friendId: Int
    ): Response<BaseResponse>
}

// 공통 응답 DTO (메시지만 오는 경우)
data class BaseResponse(val message: String?)

// 친구 요청 응답 DTO
data class FriendRequest(
    val id: Int,
    val nickname: String
)
data class FriendRequestListResponse(
    val success: Boolean,
    val received: List<FriendRequest>,
    val sent: List<FriendRequest>
)
// 친구 목록 응답 DTO

data class FriendInfoListResponse(
    val success: Boolean,
    val friends: List<FriendInfo> // 실제 리스트는 여기 들어있음
)
@Parcelize
data class FriendInfo(
    val id: Int,
    val nickname: String,
    val profileImageUrl: String?, // 사진이 없을 수도 있으니까 Nullable(?) 처리
    val createdAt: String,       // "2026-04-20" 형식
    val countDiaries: Int,
    val streak: Int,
    val countWeeklySummaries: Int,
    val lastDiaryUpdateDate: String? // 일기를 한 번도 안 썼을 수 있으니 Nullable
) : Parcelable