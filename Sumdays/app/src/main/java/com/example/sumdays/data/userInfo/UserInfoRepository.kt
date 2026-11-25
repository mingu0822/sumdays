
package com.example.sumdays.data.userInfo

import android.content.Context
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.network.ApiClient
import com.example.sumdays.data.userInfo.prefs.*
import com.example.sumdays.network.UpdateNicknameRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import kotlinx.coroutines.CancellationException

class UserInfoRepository(context: Context) {

    private val appContext = context.applicationContext

    private fun getBearerToken(): String? {
        val rawToken = SessionManager.getToken() ?: return null

        return if (rawToken.startsWith("Bearer ")) {
            rawToken
        } else {
            "Bearer $rawToken"
        }
    }

    suspend fun updateNickname(
        nickname: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getBearerToken()
                ?: return@withContext Result.failure(
                    Exception("인증 정보가 없습니다.")
                )

            val request = UpdateNicknameRequest(nickname)

            val response = ApiClient.api.updateNickname(
                token,
                request
            )

            val body = response.body()

            if (response.isSuccessful && body?.success == true) {
                // 서버에서 최종 저장된 닉네임
                val savedNickname = body.nickname
                    ?: return@withContext Result.failure(
                        Exception("서버에서 닉네임을 반환하지 않았습니다.")
                    )

                // 서버 반환값으로 Pref 갱신
                SessionManager.saveNickname(savedNickname)

                Result.success(savedNickname)
            } else {
                Result.failure(
                    Exception(
                        body?.message ?: "닉네임 변경 실패 (${response.code()})"
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



    // ProfileImagePref

    suspend fun updateProfilePhoto(
        imageFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = getBearerToken()
                ?: return@withContext false

            val requestBody = imageFile.asRequestBody(
                "image/*".toMediaTypeOrNull()
            )

            val part = MultipartBody.Part.createFormData(
                "profileImage",
                imageFile.name,
                requestBody
            )

            val response = ApiClient.userInfoApi.updateProfileImage(
                token,
                part
            )

            if (response.isSuccessful &&
                response.body()?.success == true
            ) {
                val imageUrl = response.body()?.profileImageUrl
                    ?: return@withContext false

                ProfileImagePrefs.setImageUrl(
                    appContext,
                    imageUrl
                )
                ProfileImagePrefs.setMode(
                    appContext,
                    "PHOTO"
                )

                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateProfileAvatar(
        faceId: Int,
        eyesId: Int,
        mouthId: Int,
        accessoryId: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = getBearerToken()
                ?: return@withContext false

            val request = UpdateProfileAvatarRequest(
                faceId = faceId,
                eyesId = eyesId,
                mouthId = mouthId,
                accessoryId = accessoryId
            )

            val response = ApiClient.userInfoApi.updateProfileAvatar(
                token,
                request
            )

            if (response.isSuccessful &&
                response.body()?.success == true
            ) {
                ProfileImagePrefs.setProfileIds(
                    context = appContext,
                    face = faceId,
                    eyes = eyesId,
                    mouth = mouthId,
                    acc = accessoryId
                )

                ProfileImagePrefs.setMode(
                    appContext,
                    "AVATAR"
                )

                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

}
