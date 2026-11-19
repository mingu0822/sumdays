
package com.example.sumdays.data.userInfo

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part

interface UserInfoApi {

    // 프로필 사진 변경
    @Multipart
    @POST("/api/auth/profile-image")
    suspend fun updateProfileImage(
        @Header("Authorization") token: String,
        @Part profileImage: MultipartBody.Part
    ): Response<UpdateProfileImageResponse>

    // 프로필 아바타 변경
    @POST("/api/auth/profile-avatar")
    suspend fun updateProfileAvatar(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileAvatarRequest
    ): Response<UpdateProfileAvatarResponse>
}
