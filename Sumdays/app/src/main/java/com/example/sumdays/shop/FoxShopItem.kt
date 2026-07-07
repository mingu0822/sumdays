package com.example.sumdays.shop

import com.example.sumdays.ShopItem

data class FoxShopItem(
    override val id: Int,
    override val name: String,
    override val description: String,
    override val price: Int,
    override var isOwned: Boolean = false,
    var count: Int = 0,

) : ShopItem {

    override val category: String = "item"
}