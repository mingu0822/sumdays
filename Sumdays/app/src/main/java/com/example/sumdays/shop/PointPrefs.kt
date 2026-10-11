package com.example.sumdays.shop

import android.content.Context
import com.example.sumdays.auth.AccountStorage

object PointPrefs {

    private const val PREF_NAME = "shop_point_prefs"
    private const val KEY_POINT = "current_point"
    private const val DEFAULT_POINT = 9000

    fun savePoint(context: Context, point: Int) {
        val prefs = AccountStorage.preferences(context, PREF_NAME)
        prefs.edit().putInt(KEY_POINT, point).apply()
    }

    fun getPoint(context: Context): Int {
        val prefs = AccountStorage.preferences(context, PREF_NAME)
        return prefs.getInt(KEY_POINT, DEFAULT_POINT)
    }

    fun reset(context: Context) {
        savePoint(context, DEFAULT_POINT)
    }
}