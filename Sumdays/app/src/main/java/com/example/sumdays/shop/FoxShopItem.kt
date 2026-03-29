package com.example.sumdays.shop

import com.example.sumdays.ShopItem
import com.example.sumdays.theme.FoxChar

data class FoxShopItem(
    override val id: Int,
    override val name: String,
    override val description: String,
    override val price: Int,
    override var isOwned: Boolean = false,

    val fox: FoxChar

) : ShopItem {

    override val category: String = "fox"
}