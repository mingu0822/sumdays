package com.example.sumdays.utils

import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response

object ApiErrorUtil {

    fun parseErrorMessage(errorBody: ResponseBody?, defaultMessage : String): String {
        return try {
            val errorString = errorBody?.string()
            if (errorString.isNullOrBlank()) return defaultMessage

            val json = JSONObject(errorString)
            val message = json.optString("message")

            if (message.isNullOrBlank()) defaultMessage else message

        } catch (e: Exception) {
            defaultMessage
        }
    }
}

fun <T> Response<T>.getErrorMessage(defaultMessage : String = "요청 실패"): String {
    return ApiErrorUtil.parseErrorMessage(this.errorBody(), defaultMessage)
}