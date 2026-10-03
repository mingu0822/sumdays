package com.example.sumdays.shop

import com.example.sumdays.ShopItem

data class FoxShopItem(
    override val id: Int,
    override val name: String,
    override val description: String,
    override val price: Int,
    override var isOwned: Boolean = false,
    override val imageRes: Int,

    var count: Int = 0,
    val itemCategory: ItemCategory,

    // 여우 이미지 위에 합성할 위치
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,

) : ShopItem {

    override val category: String = "item"
}