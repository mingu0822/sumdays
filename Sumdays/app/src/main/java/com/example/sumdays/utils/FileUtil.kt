package com.example.sumdays.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object FileUtil {

    fun getFileFromUri(context: Context, uri: Uri): File? {
        val fileName = getFileName(context, uri) ?: return null
        val file = File(context.cacheDir, fileName)

        try {
            // Content Resolver를 통해 InputStream을 열고, 임시 파일에 복사
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return file
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        return cursor.getString(nameIndex)
                    }
                }
            }
        }
        // content scheme이 아니거나 추출 실패 시, 마지막 경로 세그먼트를 사용
        return uri.lastPathSegment
    }
}