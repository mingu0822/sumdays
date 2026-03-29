package com.example.sumdays.theme

import android.content.Context

object ThemePrefs {

    private const val PREF_THEME = "theme_settings"
    private const val KEY_THEME = "selected_theme"

    private const val PREF_FOX = "fox_settings"
    private const val KEY_FOX = "selected_fox"

    fun saveTheme(context: Context, themeName: String) {
        val prefs = context.getSharedPreferences(PREF_THEME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, themeName).apply()
    }

    fun getTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_THEME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME, "default") ?: "default"
    }

    fun saveFox(context: Context, foxName: String) {
        val prefs = context.getSharedPreferences(PREF_FOX, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FOX, foxName).apply()
    }

    fun getFox(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_FOX, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FOX, "default") ?: "default"
    }
}