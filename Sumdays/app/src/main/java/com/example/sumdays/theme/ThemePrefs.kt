package com.example.sumdays.theme

import android.content.Context

object ThemePrefs {

    private const val PREF_THEME = "theme_settings"
    private const val KEY_THEME = "selected_theme"

    private const val PREF_FOX = "fox_settings"
    private const val KEY_FOX = "selected_fox"

    fun saveTheme(context: Context, themeId: Int) {
        val prefs = context.getSharedPreferences(PREF_THEME, Context.MODE_PRIVATE)

        prefs.edit()
            .putInt(KEY_THEME, themeId)
            .apply()
    }

    fun getTheme(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_THEME, Context.MODE_PRIVATE)

        return prefs.getInt(KEY_THEME, 1)
    }

    fun saveFoxItem(context: Context, foxId: Int) {
        val prefs = context.getSharedPreferences(PREF_FOX, Context.MODE_PRIVATE)

        prefs.edit()
            .putInt(KEY_FOX, foxId)
            .apply()
    }

    fun getFoxItem(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_FOX, Context.MODE_PRIVATE)

        return prefs.getInt(KEY_FOX, 1)
    }
}