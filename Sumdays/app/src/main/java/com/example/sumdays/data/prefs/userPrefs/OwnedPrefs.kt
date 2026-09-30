package com.example.sumdays.shop

import android.content.Context

object OwnedPrefs {

    private const val PREF_NAME = "owned_items"

    fun saveOwned(context: Context, id: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(id.toString(), true).apply()
    }

    fun isOwned(context: Context, id: Int): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(id.toString(), false)
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}