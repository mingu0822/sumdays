package com.example.sumdays.image
import android.content.Context
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import com.example.sumdays.network.apiService.UpdateProfileAvatarRequest
import com.example.sumdays.settings.prefs.ProfileImagePrefs

suspend fun uploadProfileImageToServer(
    context: Context,
    imageFile: File
): Boolean = withContext(Dispatchers.IO) {
    try {
        val rawToken = SessionManager.getToken()
            ?: return@withContext false

        val token = if (rawToken.startsWith("Bearer ")) {
            rawToken
        } else {
            "Bearer $rawToken"
        }

        val requestBody = imageFile.asRequestBody(
            "image/*".toMediaTypeOrNull()
        )

        val part = MultipartBody.Part.createFormData(
            "profileImage",
            imageFile.name,
            requestBody
        )

        val response = ApiClient.api.updateProfileImage(token, part)

        if (response.isSuccessful && response.body()?.success == true) {
            val imageUrl = response.body()?.profileImageUrl
                ?: return@withContext false

            ProfileImagePrefs.setImageUrl(context, imageUrl)
            ProfileImagePrefs.setMode(context, "PHOTO")

            true
        } else {
            false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

/**
 * 아바타 설정을 서버에 저장
 * @return 저장 성공 여부
 */
suspend fun uploadProfileAvatarToServer(
    faceId: Int,
    eyesId: Int,
    mouthId: Int,
    accessoryId: Int
): Boolean = withContext(Dispatchers.IO) {
    try {
        val rawToken = SessionManager.getToken()
            ?: return@withContext false

        val token = if (rawToken.startsWith("Bearer ")) {
            rawToken
        } else {
            "Bearer $rawToken"
        }

        val request = UpdateProfileAvatarRequest(
            faceId = faceId,
            eyesId = eyesId,
            mouthId = mouthId,
            accessoryId = accessoryId
        )

        val response = ApiClient.api.updateProfileAvatar(token, request)

        response.isSuccessful && response.body()?.success == true

    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}