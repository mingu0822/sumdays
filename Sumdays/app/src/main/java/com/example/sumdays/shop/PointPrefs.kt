package com.example.sumdays.shop

import android.content.Context

object PointPrefs {

    private const val PREF_NAME = "shop_point_prefs"
    private const val KEY_POINT = "current_point"
    private const val DEFAULT_POINT = 1240

    fun savePoint(context: Context, point: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_POINT, point).apply()
    }

    fun getPoint(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_POINT, DEFAULT_POINT)
    }

    fun reset(context: Context) {
        savePoint(context, DEFAULT_POINT)
    }
}