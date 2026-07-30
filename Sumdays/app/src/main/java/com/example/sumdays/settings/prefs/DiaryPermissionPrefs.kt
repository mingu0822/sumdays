package com.example.sumdays.settings.prefs

import android.content.Context

object DiaryPermissionPrefs {

    private const val PREF_NAME = "diary_permission_settings"
    private const val KEY_DEFAULT_MODE = "default_permission_mode"

    const val MODE_ASK = 0            // 일기 생성 시 매번 물어보기
    const val MODE_DEFAULT_FALSE = 1  // 항상 비공개로 생성
    const val MODE_DEFAULT_TRUE = 2   // 항상 공개로 생성

    fun setMode(context: Context, mode: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_DEFAULT_MODE, mode).apply()
    }

    fun getMode(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_DEFAULT_MODE, MODE_ASK)
    }
}
