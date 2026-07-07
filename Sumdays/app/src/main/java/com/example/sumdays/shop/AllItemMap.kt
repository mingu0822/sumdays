package com.example.sumdays.shop

object AllItemMap {
    val allItemMap: MutableMap<String, FoxShopItem> = mutableMapOf(
        "glasses" to FoxShopItem(
            name = "glasses",
            id = 3,
            description = "귀여운 안경입니다.",
            price = 300,
            isOwned = true
        ),

        "hat" to FoxShopItem(
            name = "hat",
            id = 4,
            description = "귀여운 모자입니다.",
            price = 400,
            isOwned = false
        ),
    )
}