package com.example.sumdays.shop

import android.content.Context
import com.example.sumdays.auth.SessionManager

object ItemPrefs {

    private const val PREF_NAME = "item_inventory"

    /**
     * 아이템 1개 추가
     */
    fun addItem(context: Context, itemId: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val current = prefs.getInt(itemKey(itemId), 0)

        prefs.edit()
            .putInt(itemKey(itemId), current + 1)
            .apply()
    }

    /**
     * 현재 보유 개수
     */
    fun getCount(context: Context, itemId: Int): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(itemKey(itemId), 0)
    }

    /**
     * 개수 설정 (필요하면 사용)
     */
    fun setCount(context: Context, itemId: Int, count: Int) {
        setCount(context, currentUserId(), itemId, count)
    }

    fun setCount(context: Context, userId: Int, itemId: Int, count: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        prefs.edit()
            .putInt(itemKey(userId, itemId), count)
            .apply()
    }

    /**
     * 아이템 하나 사용
     * 성공하면 true
     * 부족하면 false
     */
    fun useItem(context: Context, itemId: Int): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val current = prefs.getInt(itemKey(itemId), 0)

        if (current <= 0) return false

        prefs.edit()
            .putInt(itemKey(itemId), current - 1)
            .apply()

        return true
    }
    /**
     * 모든 아이템 초기화
     */
    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val prefix = userPrefix()
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun replaceCounts(context: Context, counts: Map<Int, Int>) {
        replaceCounts(context, currentUserId(), counts)
    }

    fun replaceCounts(context: Context, userId: Int, counts: Map<Int, Int>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val prefix = userPrefix(userId)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        counts.forEach { (itemId, count) -> editor.putInt(itemKey(userId, itemId), count) }
        editor.apply()
    }

    private fun itemKey(itemId: Int): String = itemKey(currentUserId(), itemId)

    private fun itemKey(userId: Int, itemId: Int): String = "${userPrefix(userId)}item_$itemId"

    private fun userPrefix(): String = userPrefix(currentUserId())

    private fun userPrefix(userId: Int): String = "user_${userId}_"

    private fun currentUserId(): Int = SessionManager.getUserId()
}
