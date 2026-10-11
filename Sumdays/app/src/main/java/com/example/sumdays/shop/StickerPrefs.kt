package com.example.sumdays.shop

import android.content.Context
import com.example.sumdays.auth.AccountStorage

/** Account-scoped stock; diary placement and consumption are intentionally added later. */
object StickerPrefs {
    private const val PREF_NAME = "sticker_inventory"

    fun getCount(context: Context, stickerId: Int): Int =
        AccountStorage.preferences(context, PREF_NAME).getInt(stickerId.toString(), 0)

    fun addSticker(context: Context, stickerId: Int) {
        val prefs = AccountStorage.preferences(context, PREF_NAME)
        val key = stickerId.toString()
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }
}
