package com.example.sumdays.shop

import android.content.Context

object ItemPrefs {

    private const val PREF_NAME = "item_inventory"

    /**
     * 아이템 1개 추가
     */
    fun addItem(context: Context, itemName: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val current = prefs.getInt(itemName, 0)

        prefs.edit()
            .putInt(itemName, current + 1)
            .apply()
    }

    /**
     * 현재 보유 개수
     */
    fun getCount(context: Context, itemName: String): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(itemName, 0)
    }

    /**
     * 개수 설정 (필요하면 사용)
     */
    fun setCount(context: Context, itemName: String, count: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        prefs.edit()
            .putInt(itemName, count)
            .apply()
    }

    /**
     * 아이템 하나 사용
     * 성공하면 true
     * 부족하면 false
     */
    fun useItem(context: Context, itemName: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val current = prefs.getInt(itemName, 0)

        if (current <= 0) return false

        prefs.edit()
            .putInt(itemName, current - 1)
            .apply()

        return true
    }

    /**
     * 모든 아이템 초기화
     */
    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}