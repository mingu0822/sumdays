package com.example.sumdays.data.prefs.userPrefs

import android.content.Context
import android.content.SharedPreferences

class UserStatsPrefs(context: Context) {

    // 1. 상수 정의
    companion object {
        private const val APP_PREFS_NAME = "user_stats_prefs" // SharedPreferences 파일 이름
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_STREAK = "current_streak"
        private const val KEY_LEAF_COUNT = "leaf_count"
        private const val KEY_GRAPE_COUNT = "grape_count"
        private const val KEY_ACTIVE_STYLE_ID = "active_style_id"
        private const val DEFAULT_STYLE_ID = -1L // 활성 스타일이 없을 때의 기본값
    }

    // SharedPreferences 인스턴스 초기화
    private val prefs: SharedPreferences =
        context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)

    // --- 닉네임 관리 ---

    fun saveNickname(nickname: String) {
        prefs.edit().putString(KEY_NICKNAME, nickname).apply()
    }

    fun getNickname(): String {
        // 기본값은 "사용자" 또는 앱에서 정의한 익명 값으로 설정
        return prefs.getString(KEY_NICKNAME, "사용자") ?: "사용자"
    }

    // --- Strike 관리 ---

    fun saveStreak(streak: Int) {
        prefs.edit().putInt(KEY_STREAK, streak).apply()
    }

    fun getStreak(): Int {
        return prefs.getInt(KEY_STREAK, 0) // 기본값 0
    }

    // --- Leaf Count 관리 ---

    fun incrementLeafCount() {
        val currentCount = getLeafCount()
        prefs.edit().putInt(KEY_LEAF_COUNT, currentCount + 1).apply()
    }

    fun getLeafCount(): Int {
        return prefs.getInt(KEY_LEAF_COUNT, 0) // 기본값 0
    }

    // --- Grape Count 관리 ---

    fun incrementGrapeCount() {
        val currentCount = getGrapeCount()
        prefs.edit().putInt(KEY_GRAPE_COUNT, currentCount + 1).apply()
    }

    fun getGrapeCount(): Int {
        return prefs.getInt(KEY_GRAPE_COUNT, 0) // 기본값 0
    }

    // ===================================
    // ★ AI 스타일 설정 관련 로직 (추가됨) ★
    // ===================================

    /**
     * 현재 활성 스타일의 ID를 SharedPreferences에 저장합니다.
     */
    fun saveActiveStyleId(styleId: Long) {
        prefs.edit().putLong(KEY_ACTIVE_STYLE_ID, styleId).apply()
    }

    /**
     * 저장된 활성 스타일의 ID를 가져옵니다.
     * 활성 스타일이 설정되지 않았다면 0L이 반환합니다.
     */
    fun getActiveStyleId(): Long {
        return prefs.getLong(KEY_ACTIVE_STYLE_ID, 0L)
    }

    /**
     * 활성 스타일 ID 설정을 해제(제거)합니다.
     * (스타일 삭제 시 활성 스타일이었다면 호출)
     */
    fun clearActiveStyleId() {
        prefs.edit().remove(KEY_ACTIVE_STYLE_ID).apply()
    }
}