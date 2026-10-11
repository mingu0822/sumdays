package com.example.sumdays.shop

import android.content.Context
import com.example.sumdays.auth.AccountStorage

object OwnedPrefs {

    private const val PREF_NAME = "owned_items"

    fun saveOwned(context: Context, id: Int) {
        val prefs = AccountStorage.preferences(context, PREF_NAME)
        prefs.edit().putBoolean(id.toString(), true).apply()
    }

    fun isOwned(context: Context, id: Int): Boolean {
        val prefs = AccountStorage.preferences(context, PREF_NAME)
        return id == 1 || prefs.getBoolean(id.toString(), false)
    }

    fun clear(context: Context) {
        val prefs = AccountStorage.preferences(context, PREF_NAME)
        prefs.edit().clear().apply()
    }
}
