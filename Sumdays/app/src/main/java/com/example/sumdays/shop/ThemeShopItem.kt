package com.example.sumdays.shop

import com.example.sumdays.ShopItem
import com.example.sumdays.theme.Theme

data class ThemeShopItem(
    override val id: Int,
    override val name: String,
    override val description: String,
    override val price: Int,
    override var isOwned: Boolean = false,

    val theme: Theme

) : ShopItem {

    override val category: String = "theme"
}