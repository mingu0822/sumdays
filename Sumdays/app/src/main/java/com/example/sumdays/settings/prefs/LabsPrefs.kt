package com.example.sumdays.settings.prefs

import android.content.Context

object LabsPrefs {

    private const val PREF_NAME = "labs_settings"

    private const val KEY_TEMPERATURE = "temperature_value"
    private const val KEY_ADVANCED_FLAG = "advanced_style_flag"
    private const val KEY_LENGTH_LEVEL = "length_level_value"

    private const val DEFAULT_TEMPERATURE = 0.5f
    private const val DEFAULT_ADVANCED_FLAG = false
    private const val DEFAULT_LENGTH_LEVEL = 0

    fun setTemperature(context: Context, value: Float) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_TEMPERATURE, value).apply()
    }

    fun getTemperature(context: Context): Float {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE)
    }

    fun setAdvancedFlag(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ADVANCED_FLAG, enabled).apply()
    }

    fun getAdvancedFlag(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ADVANCED_FLAG, DEFAULT_ADVANCED_FLAG)
    }

    fun setLengthLevel(context: Context, value: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_LENGTH_LEVEL, value).apply()
    }

    fun getLengthLevel(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_LENGTH_LEVEL, DEFAULT_LENGTH_LEVEL)
    }
}