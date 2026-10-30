package com.example.sumdays.shop

import com.example.sumdays.ShopItem

/** A single sticker sold by quantity, separately from fox customization items. */
data class StickerShopItem(
    override val id: Int,
    override val name: String,
    override val description: String,
    override val price: Int,
    override val imageRes: Int,
    override var isOwned: Boolean = false
) : ShopItem {
    override val category: String = "sticker"
}
