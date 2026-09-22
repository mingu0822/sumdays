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
import android.net.Uri
import java.io.FileOutputStream

/**
 * 이미지 파일을 서버에 전송하는 단독 함수
 * @param imageFile 전송할 File 객체
 * @return 업로드 성공 여부 (Boolean)
 */
suspend fun uploadProfileImageToServer(imageFile: File): Boolean = withContext(Dispatchers.IO) {
    try {
        val rawToken = SessionManager.getToken() ?: return@withContext false
        val token = if (rawToken.startsWith("Bearer ")) rawToken else "Bearer $rawToken"

        val requestBody = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("profileImage", imageFile.name, requestBody)

        val response = ApiClient.api.updateProfileImage(token, part)
        response.isSuccessful && response.body()?.success == true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun prepareTempFile(context: Context, sourcePath: String): File? {
    return try {
        // 캐시 디렉터리에 타임스탬프 기반 임시 파일 생성
        val tempFile = File(context.cacheDir, "profile_upload_${System.currentTimeMillis()}.jpg")

        if (sourcePath.startsWith("content://")) {
            // 갤러리 URI인 경우 ContentResolver를 사용해 복사
            val uri = Uri.parse(sourcePath)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            // 크롭 후 생성된 일반 로컬 파일 경로인 경우 바로 복사
            File(sourcePath).copyTo(tempFile, overwrite = true)
        }

        tempFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}