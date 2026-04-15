package com.example.sumdays.statistics

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object StreakPrefs {
    private const val PREF = "streak_prefs"
    private const val KEY_LAST_DATE = "last_diary_date"   // yyyy-MM-dd
    private const val KEY_STREAK = "current_streak"
    @RequiresApi(Build.VERSION_CODES.O)
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    fun getStreak(context: Context): Int {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return sp.getInt(KEY_STREAK, 0)
    }

    // 오늘 일기 저장 완료 시점에 streak를 계산한다.
    @RequiresApi(Build.VERSION_CODES.O)
    fun onDiarySaved(context: Context, entryDateStr: String) {
        val entryDate = runCatching { LocalDate.parse(entryDateStr, fmt) }.getOrNull() ?: return
        val today = LocalDate.now()
        if (entryDate != today) return

        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val lastStr = sp.getString(KEY_LAST_DATE, null)
        val oldStreak = sp.getInt(KEY_STREAK, 0)

        val newStreak = if (lastStr == null) {
            1
        } else {
            val lastDate = runCatching { LocalDate.parse(lastStr, fmt) }.getOrNull()
            if (lastDate == null) 1
            else {
                val diff = ChronoUnit.DAYS.between(lastDate, today).toInt()
                when (diff) {
                    0 -> oldStreak      // 오늘 이미 반영됨(일기 수정한 경우) -> 유지
                    1 -> oldStreak + 1  // 어제 작성한 경우 → +1
                    else -> 1           // streak 끊김 -> 1
                }
            }
        }

        sp.edit {
            putInt(KEY_STREAK, newStreak)
                .putString(KEY_LAST_DATE, today.toString())
        }
    }

    // 통계 화면에서 호출된다. (streak를 확인할 수 있는 모든 곳에서 호출해야 한다.)
    @RequiresApi(Build.VERSION_CODES.O)
    fun refreshOnOpen(context: Context) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val lastStr = sp.getString(KEY_LAST_DATE, null) ?: return

        val lastDate = runCatching { LocalDate.parse(lastStr, fmt) }.getOrNull() ?: return
        val today = LocalDate.now()
        val diff = ChronoUnit.DAYS.between(lastDate, today).toInt()

        if (diff >= 2) {
            sp.edit { putInt(KEY_STREAK, 0) }
        }
    }
}