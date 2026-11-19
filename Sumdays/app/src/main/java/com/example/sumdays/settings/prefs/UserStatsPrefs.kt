
package com.example.sumdays.settings.prefs

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class UserStatsPrefs(context: Context) {

    companion object {
        private const val APP_PREFS_NAME = "user_stats_prefs"

        private const val KEY_NICKNAME = "nickname"
        private const val KEY_STREAK = "current_streak"
        private const val KEY_LAST_DATE = "last_diary_date"
        private const val KEY_LEAF_COUNT = "leaf_count"
        private const val KEY_GRAPE_COUNT = "grape_count"
        private const val KEY_ACTIVE_STYLE_ID = "active_style_id"

        private const val DEFAULT_STYLE_ID = -1L
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(
            APP_PREFS_NAME,
            Context.MODE_PRIVATE
        )

    // -----------------------------
    // 닉네임
    // -----------------------------

    fun saveNickname(nickname: String) {
        prefs.edit().putString(KEY_NICKNAME, nickname).apply()
    }

    fun getNickname(): String {
        return prefs.getString(KEY_NICKNAME, "사용자") ?: "사용자"
    }

    // -----------------------------
    // 스트릭
    // -----------------------------

    fun saveStreak(streak: Int) {
        prefs.edit().putInt(KEY_STREAK, streak).apply()
    }

    fun getStreak(): Int {
        return prefs.getInt(KEY_STREAK, 0)
    }

    fun getLastDiaryDate(): String? {
        return prefs.getString(KEY_LAST_DATE, null)
    }

    fun saveLastDiaryDate(date: String) {
        prefs.edit().putString(KEY_LAST_DATE, date).apply()
    }

    // 오늘 일기 저장 완료 시 스트릭 갱신
    @RequiresApi(Build.VERSION_CODES.O)
    fun onDiarySaved(entryDateStr: String) {
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE

        val entryDate = runCatching {
            LocalDate.parse(entryDateStr, fmt)
        }.getOrNull() ?: return

        val today = LocalDate.now()
        if (entryDate != today) return

        val lastStr = getLastDiaryDate()
        val oldStreak = getStreak()

        val newStreak = if (lastStr == null) {
            1
        } else {
            val lastDate = runCatching {
                LocalDate.parse(lastStr, fmt)
            }.getOrNull()

            if (lastDate == null) {
                1
            } else {
                val diff = ChronoUnit.DAYS
                    .between(lastDate, today)
                    .toInt()

                when (diff) {
                    0 -> oldStreak
                    1 -> oldStreak + 1
                    else -> 1
                }
            }
        }

        prefs.edit()
            .putInt(KEY_STREAK, newStreak)
            .putString(KEY_LAST_DATE, today.toString())
            .apply()
    }

    // 스트릭 조회 시 만료 여부 확인
    @RequiresApi(Build.VERSION_CODES.O)
    fun refreshOnOpen() {
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE

        val lastStr = getLastDiaryDate() ?: return

        val lastDate = runCatching {
            LocalDate.parse(lastStr, fmt)
        }.getOrNull() ?: return

        val today = LocalDate.now()

        val diff = ChronoUnit.DAYS
            .between(lastDate, today)
            .toInt()

        if (diff >= 2) {
            saveStreak(0)
        }
    }

    // -----------------------------
    // 나뭇잎
    // -----------------------------

    fun incrementLeafCount() {
        val currentCount = getLeafCount()
        prefs.edit()
            .putInt(KEY_LEAF_COUNT, currentCount + 1)
            .apply()
    }

    fun getLeafCount(): Int {
        return prefs.getInt(KEY_LEAF_COUNT, 0)
    }

    // -----------------------------
    // 포도
    // -----------------------------

    fun incrementGrapeCount() {
        val currentCount = getGrapeCount()
        prefs.edit()
            .putInt(KEY_GRAPE_COUNT, currentCount + 1)
            .apply()
    }

    fun getGrapeCount(): Int {
        return prefs.getInt(KEY_GRAPE_COUNT, 0)
    }

    // -----------------------------
    // 활성 AI 스타일 (추후 이동)
    // -----------------------------

    fun saveActiveStyleId(styleId: Long) {
        prefs.edit()
            .putLong(KEY_ACTIVE_STYLE_ID, styleId)
            .apply()
    }

    fun getActiveStyleId(): Long {
        return prefs.getLong(
            KEY_ACTIVE_STYLE_ID,
            DEFAULT_STYLE_ID
        )
    }

    fun clearActiveStyleId() {
        prefs.edit()
            .remove(KEY_ACTIVE_STYLE_ID)
            .apply()
    }
}
