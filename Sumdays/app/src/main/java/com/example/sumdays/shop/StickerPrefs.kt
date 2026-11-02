package com.example.sumdays.shop

import android.content.Context
import com.example.sumdays.stickers.StickerInventoryStore

/** Account-scoped stock shared by the shop and diary placement reservations. */
object StickerPrefs {
    fun getCount(context: Context, stickerId: Int): Int =
        StickerInventoryStore(context).count(stickerId)

    fun addSticker(context: Context, stickerId: Int) {
        StickerInventoryStore(context).add(stickerId)
    }
}
