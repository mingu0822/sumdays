package com.example.sumdays.daily.memo

import android.content.Context
import com.google.gson.Gson
import java.security.MessageDigest
import java.util.UUID

/** 메모 본문은 저장하지 않고 요청 해시와 ID만 보관한다. 응답 유실 시 같은 입력을 재시도한다. */
object PendingMergeStore {
    private fun prefs(context: Context) = context.getSharedPreferences("pending_merge", Context.MODE_PRIVATE)
    private fun key(userId: Int, request: MergeRequest): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(Gson().toJson(request.copy(requestId = "")).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "user_${userId}_$digest"
    }

    @Synchronized
    fun getOrCreate(context: Context, userId: Int, request: MergeRequest): String {
        val prefs = prefs(context)
        val key = key(userId, request)
        return prefs.getString(key, null) ?: UUID.randomUUID().toString().also {
            check(prefs.edit().putString(key, it).commit()) { "합치기 요청을 저장하지 못했습니다." }
        }
    }

    fun clear(context: Context, userId: Int, request: MergeRequest) {
        prefs(context).edit().remove(key(userId, request)).apply()
    }
}
