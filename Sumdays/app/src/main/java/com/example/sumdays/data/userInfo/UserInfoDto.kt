package com.example.sumdays.data.userInfo

data class UpdateProfileImageResponse(
    val success: Boolean,
    val profileImageUrl: String?
)

// 아바타 변경 요청
data class UpdateProfileAvatarRequest(
    val faceId: Int,
    val eyesId: Int,
    val mouthId: Int,
    val accessoryId: Int
)

// 아바타 변경 응답
data class UpdateProfileAvatarResponse(
    val success: Boolean,
    val message: String?
)
