package com.example.sumdays.network.apiService

import retrofit2.Response
import retrofit2.http.*

interface SocialApiService {

    // 1. 친구 요청하기
    @POST("/api/friend/request")
    suspend fun requestFriend(
        @Body body: Map<String, Int> // { "receiverId": 123 }
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

    // 4. 목록 조회 (type: received / sent)
    @GET("/api/friend/requests")
    suspend fun getPendingRequests(
        @Query("type") type: String
    ): Response<List<FriendRequestResponse>>

    // 5. 내 전체 친구 목록 조회
    @GET("/api/friend/friends")
    suspend fun getMyFriends(): Response<List<FriendResponse>>

    // 6. 친구 삭제
    @DELETE("/api/friend/friends/{friendId}")
    suspend fun deleteFriend(
        @Path("friendId") friendId: Int
    ): Response<BaseResponse>
}

// 공통 응답 DTO (메시지만 오는 경우)
data class BaseResponse(val message: String?)

// 친구 요청 응답 DTO
data class FriendRequestResponse(
    val id: Int,
    val nickname: String
)

// 친구 목록 응답 DTO
data class FriendResponse(
    val id: Int,
    val nickname: String
)