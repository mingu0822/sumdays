package com.example.sumdays.shop

import com.example.sumdays.R


object AllItemMap {
    val allItemMap: MutableMap<Int, FoxShopItem> = mutableMapOf(
        1 to FoxShopItem(
            name = "안경",
            id = 1,
            description = "귀여운 안경입니다.",
            price = 300,
            isOwned = true,
            itemCategory = ItemCategory.GLASSES,
            imageRes = R.drawable.foxitem_glasses
        ),

        3 to FoxShopItem(
            name = "선글라스",
            id = 3,
            description = "선글라스입니다.",
            price = 200,
            isOwned = false,
            itemCategory = ItemCategory.GLASSES,
            imageRes = R.drawable.foxitem_glasses
        ),

        2 to FoxShopItem(
            name = "모자",
            id = 2,
            description = "귀여운 모자입니다.",
            price = 400,
            isOwned = false,
            itemCategory = ItemCategory.HAT,
            imageRes = R.drawable.foxitem_magic_hat
        ),
    )
}