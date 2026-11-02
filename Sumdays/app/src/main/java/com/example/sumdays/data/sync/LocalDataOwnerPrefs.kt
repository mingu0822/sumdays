package com.example.sumdays.data.sync

import android.content.Context
import com.example.sumdays.auth.SessionManager

/**
 * 계정 공용 Room DB가 현재 어느 사용자의 서버 데이터와 동기화됐는지 기록합니다.
 * 소유자가 일치하지 않는 동안에는 로컬 데이터를 서버로 업로드하지 않습니다.
 */
object LocalDataOwnerPrefs {
    private const val PREF_NAME = "local_data_owner"
    private const val KEY_READY_USER_ID = "ready_user_id"
    private const val NO_USER = -1

    fun prepareForCurrentSession(context: Context) {
        val userId = currentUserId()
        if (userId < 0) return

        val prefs = prefs(context)
        if (!prefs.contains(KEY_READY_USER_ID)) {
            prefs.edit().putInt(KEY_READY_USER_ID, userId).apply()
        }
    }

    fun beginAccountSwitch(context: Context) {
        prefs(context).edit().putInt(KEY_READY_USER_ID, NO_USER).apply()
    }

    fun markReady(context: Context, userId: Int) {
        if (currentUserId() == userId) {
            prefs(context).edit().putInt(KEY_READY_USER_ID, userId).apply()
        }
    }

    fun isReadyFor(context: Context, userId: Int): Boolean =
        userId >= 0 && prefs(context).getInt(KEY_READY_USER_ID, NO_USER) == userId

    fun clear(context: Context) {
        prefs(context).edit().putInt(KEY_READY_USER_ID, NO_USER).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun currentUserId(): Int =
        runCatching { SessionManager.getUserId() }.getOrDefault(NO_USER)
}
