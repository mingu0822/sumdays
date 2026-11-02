package com.example.sumdays.shop

import android.content.Context
import com.example.sumdays.auth.AccountStorage

object OwnedPrefs {

    private const val PREF_NAME = "owned_items"
    private const val DEFAULT_THEME_ID = 1

    fun saveOwned(context: Context, id: Int) {
        val prefs = AccountStorage.preferences(context, PREF_NAME)
        prefs.edit().putBoolean(id.toString(), true).apply()
    }

    fun saveOwned(context: Context, userId: Int, id: Int) {
        val prefs = AccountStorage.preferences(context, PREF_NAME, userId)
        prefs.edit().putBoolean(id.toString(), true).apply()
    }

    fun isOwned(context: Context, id: Int): Boolean {
        val prefs = AccountStorage.preferences(context, PREF_NAME)
        return id == DEFAULT_THEME_ID || prefs.getBoolean(id.toString(), false)
    }

    fun clear(context: Context) {
        val prefs = AccountStorage.preferences(context, PREF_NAME)
        prefs.edit().clear().apply()
    }

    fun replaceOwned(context: Context, userId: Int, ownedIds: Set<Int>) {
        val editor = AccountStorage.preferences(context, PREF_NAME, userId).edit().clear()
        ownedIds.forEach { editor.putBoolean(it.toString(), true) }
        editor.apply()
    }
}
