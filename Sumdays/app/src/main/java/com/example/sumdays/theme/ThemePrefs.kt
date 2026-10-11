package com.example.sumdays.theme

import android.content.Context
import com.example.sumdays.auth.AccountStorage

object ThemePrefs {

    private const val PREF_THEME = "theme_settings"
    private const val KEY_THEME = "selected_theme"

    private const val PREF_FOX = "fox_settings"
    private const val KEY_FOX = "selected_fox"

    fun saveTheme(context: Context, themeId: Int) {
        val prefs = AccountStorage.preferences(context, PREF_THEME)

        prefs.edit()
            .putInt(KEY_THEME, themeId)
            .apply()
    }

    fun getTheme(context: Context): Int {
        val prefs = AccountStorage.preferences(context, PREF_THEME)

        return prefs.getInt(KEY_THEME, 1)
    }

    fun saveFoxItem(context: Context, foxId: Int) {
        val prefs = AccountStorage.preferences(context, PREF_FOX)

        prefs.edit()
            .putInt(KEY_FOX, foxId)
            .apply()
    }

    fun getFoxItem(context: Context): Int {
        val prefs = AccountStorage.preferences(context, PREF_FOX)

        return prefs.getInt(KEY_FOX, 1)
    }
}