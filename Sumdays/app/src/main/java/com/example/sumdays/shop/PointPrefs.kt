package com.example.sumdays.shop

import android.content.Context
import com.example.sumdays.auth.SessionManager

object PointPrefs {

    private const val PREF_NAME = "shop_point_prefs"
    private const val KEY_LEGACY_POINT = "current_point"
    private const val KEY_LEGACY_POINT_OWNER = "legacy_point_owner_user_id"
    private const val KEY_GUEST_POINT = "guest_point"
    private const val KEY_USER_POINT_PREFIX = "current_point_user_"
    private const val DEFAULT_POINT = 9000

    fun savePoint(context: Context, point: Int) {
        savePoint(context, currentUserId(), point)
    }

    fun savePoint(context: Context, userId: Int, point: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(pointKey(userId), point).apply()
    }

    fun getPoint(context: Context): Int {
        return getPoint(context, currentUserId())
    }

    fun getPoint(context: Context, userId: Int): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(pointKey(userId), DEFAULT_POINT)
    }

    /**
     * 앱 업데이트 당시 로그인돼 있던 계정을 기존 단일 포인트의 소유자로 고정합니다.
     * 로그아웃 상태였다면 소유자를 추측하지 않아 다른 계정으로 포인트가 넘어가지 않습니다.
     */
    fun prepareLegacyMigration(context: Context) {
        val userId = currentUserId()
        if (userId < 0) return

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_LEGACY_POINT) && !prefs.contains(KEY_LEGACY_POINT_OWNER)) {
            prefs.edit().putInt(KEY_LEGACY_POINT_OWNER, userId).apply()
        }
    }

    /**
     * 서버 포인트 지갑이 도입되기 전 앱이 저장하던 단일 포인트 값입니다.
     * 일반 화면에는 노출하지 않고, 서버 지갑 최초 생성 요청에만 사용합니다.
     */
    fun getLegacyPointForMigration(context: Context): Int {
        return getLegacyPointForMigration(context, currentUserId())
    }

    fun getLegacyPointForMigration(context: Context, userId: Int): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val legacyOwnerId = prefs.getInt(KEY_LEGACY_POINT_OWNER, -1)
        return if (userId >= 0 && legacyOwnerId == userId) {
            prefs.getInt(KEY_LEGACY_POINT, getPoint(context, userId))
        } else {
            getPoint(context, userId)
        }
    }

    /**
     * 서버가 지갑 초기화 요청을 정상 처리했으면 단일 포인트 값은 더 이상 다른
     * 계정의 초기값으로 재사용되지 않도록 제거합니다.
     */
    fun markLegacyPointHandled(context: Context) {
        markLegacyPointHandled(context, currentUserId())
    }

    fun markLegacyPointHandled(context: Context, userId: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_LEGACY_POINT_OWNER, -1) == userId) {
            prefs.edit()
                .remove(KEY_LEGACY_POINT)
                .remove(KEY_LEGACY_POINT_OWNER)
                .apply()
        }
    }

    fun reset(context: Context) {
        savePoint(context, DEFAULT_POINT)
    }

    private fun pointKey(userId: Int): String {
        return if (userId >= 0) "$KEY_USER_POINT_PREFIX$userId" else KEY_GUEST_POINT
    }

    private fun currentUserId(): Int =
        runCatching { SessionManager.getUserId() }.getOrDefault(-1)
}
