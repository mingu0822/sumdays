package com.example.sumdays.network

data class UpdateNicknameRequest(val newNickname: String)

data class UpdateNicknameResponse(
    val success: Boolean,
    val message: String
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

data class ChangePasswordResponse(
    val success: Boolean,
    val message: String
)
data class SignupRequest(
    val nickname: String,
    val email: String,
    val password: String
)

data class SignupResponse(
    val success: Boolean,
    val message: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class GoogleLoginRequest(val idToken: String)

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val userId: Int?,
    val token: String?,
    val nickname: String?
)
