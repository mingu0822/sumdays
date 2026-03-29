package com.example.sumdays.shop

import android.content.Context

object OwnedPrefs {

    private const val PREF_NAME = "owned_items"

    fun saveOwned(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(key, true).apply()
    }

    fun isOwned(context: Context, key: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(key, false)
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}