package com.example.sumdays.reminder


import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.content.edit
class ReminderPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    // 1. 마스터 ON/OFF 상태 저장
    fun setMasterSwitch(isOn: Boolean) {
        prefs.edit().putBoolean("master_switch", isOn).apply()
    }
    fun isMasterOn(): Boolean {
        return prefs.getBoolean("master_switch", false)
    }

    // 2. 알람 시간 목록 저장 (최대 10개, "HH:mm" 형식 문자열 리스트)
    fun setAlarmTimes(times: List<String>) {
        val json = gson.toJson(times)
        prefs.edit().putString("alarm_times", json).apply()
    }
    fun getAlarmTimes(): List<String> {
        val json = prefs.getString("alarm_times", "[]")
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
    fun clear() {
        prefs.edit { clear() }
    }
}