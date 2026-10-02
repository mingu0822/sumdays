package com.example.sumdays.shop

import android.content.Context
import com.example.sumdays.auth.SessionManager

object OwnedPrefs {

    private const val PREF_NAME = "owned_items"

    fun saveOwned(context: Context, id: Int) {
        saveOwned(context, currentUserId(), id)
    }

    fun saveOwned(context: Context, userId: Int, id: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(itemKey(userId, id), true).apply()
    }

    fun isOwned(context: Context, id: Int): Boolean {
        return isOwned(context, currentUserId(), id)
    }

    fun isOwned(context: Context, userId: Int, id: Int): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return id == DEFAULT_THEME_ID || prefs.getBoolean(itemKey(userId, id), false)
    }

    fun clear(context: Context) {
        clear(context, currentUserId())
    }

    fun clear(context: Context, userId: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val prefix = userPrefix(userId)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun replaceOwned(context: Context, ownedIds: Set<Int>) {
        replaceOwned(context, currentUserId(), ownedIds)
    }

    fun replaceOwned(context: Context, userId: Int, ownedIds: Set<Int>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val prefix = userPrefix(userId)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        ownedIds.forEach { editor.putBoolean(itemKey(userId, it), true) }
        editor.apply()
    }

    private fun itemKey(userId: Int, id: Int): String = "${userPrefix(userId)}item_$id"

    private fun userPrefix(userId: Int): String = "user_${userId}_"

    private fun currentUserId(): Int = SessionManager.getUserId()

    private const val DEFAULT_THEME_ID = 1
}
