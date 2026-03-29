package com.example.sumdays.theme

import android.content.Context

object ThemePrefs {

    private const val PREF = "theme_settings"
    private const val KEY = "default"

    fun saveTheme(context: Context, themeName: String) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY, themeName).apply()
    }

    fun getTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return prefs.getString(KEY, "default")!!
    }
}