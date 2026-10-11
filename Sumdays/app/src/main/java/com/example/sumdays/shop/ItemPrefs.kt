package com.example.sumdays.shop

import android.content.Context
import com.example.sumdays.auth.AccountStorage

object ItemPrefs {

    private const val PREF_NAME = "item_inventory"

    /**
     * 아이템 1개 추가
     */
    fun addItem(context: Context, itemId: Int) {
        val prefs = AccountStorage.preferences(context, PREF_NAME)

        val current = prefs.getInt(itemId.toString(), 0)

        prefs.edit()
            .putInt(itemId.toString(), current + 1)
            .apply()
    }

    /**
     * 현재 보유 개수
     */
    fun getCount(context: Context, itemId: Int): Int {
        val prefs = AccountStorage.preferences(context, PREF_NAME)
        return prefs.getInt(itemId.toString(), 0)
    }

    /**
     * 개수 설정 (필요하면 사용)
     */
    fun setCount(context: Context, itemId: Int, count: Int) {
        val prefs = AccountStorage.preferences(context, PREF_NAME)

        prefs.edit()
            .putInt(itemId.toString(), count)
            .apply()
    }

    /**
     * 아이템 하나 사용
     * 성공하면 true
     * 부족하면 false
     */
    fun useItem(context: Context, itemId: Int): Boolean {
        val prefs = AccountStorage.preferences(context, PREF_NAME)

        val current = prefs.getInt(itemId.toString(), 0)

        if (current <= 0) return false

        prefs.edit()
            .putInt(itemId.toString(), current - 1)
            .apply()

        return true
    }
    /**
     * 모든 아이템 초기화
     */
    fun clear(context: Context) {
        val prefs = AccountStorage.preferences(context, PREF_NAME)
        prefs.edit().clear().apply()
    }
}