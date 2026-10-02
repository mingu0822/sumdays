package com.example.sumdays.shop

import com.example.sumdays.R


object AllItemMap {
    val allItemMap: MutableMap<Int, FoxShopItem> = mutableMapOf(
        401 to FoxShopItem(
            id = 401,
            name = "화난 표정",
            description = "합성 시 1개 사용해요.",
            price = 200,
            itemCategory = ItemCategory.FOXFACE,
            imageRes = R.drawable.dailyread_fox_face_level_1
        ),
        402 to FoxShopItem(
            id = 402,
            name = "걱정 표정",
            description = "합성 시 1개 사용해요.",
            price = 200,
            itemCategory = ItemCategory.FOXFACE,
            imageRes = R.drawable.dailyread_fox_face_level_2
        ),
        404 to FoxShopItem(
            id = 404,
            name = "미소 표정",
            description = "합성 시 1개 사용해요.",
            price = 200,
            itemCategory = ItemCategory.FOXFACE,
            imageRes = R.drawable.dailyread_fox_face_level_4
        ),
        405 to FoxShopItem(
            id = 405,
            name = "활짝 웃는 표정",
            description = "합성 시 1개 사용해요.",
            price = 200,
            itemCategory = ItemCategory.FOXFACE,
            imageRes = R.drawable.dailyread_fox_face_level_5
        ),
        1 to FoxShopItem(
            name = "안경",
            id = 1,
            description = "귀여운 안경입니다.",
            price = 300,
            isOwned = true,
            itemCategory = ItemCategory.GLASSES,
            imageRes = R.drawable.foxitem_glasses,
            offsetX = 60f,
            offsetY = 350f,
        ),

        2 to FoxShopItem(
            name = "보라 안경",
            id = 2,
            description = "보라색 안경입니다.",
            price = 200,
            isOwned = false,
            itemCategory = ItemCategory.GLASSES,
            imageRes = R.drawable.foxitem_purple_glasses,
            offsetX = 0f,
            offsetY = 350f,
        ),

        101 to FoxShopItem(
            name = "모자",
            id = 101,
            description = "귀여운 모자입니다.",
            price = 400,
            isOwned = false,
            itemCategory = ItemCategory.HAT,
            imageRes = R.drawable.foxitem_magic_hat,
            offsetX = 0f,
            offsetY = -750f,
        ),

        102 to FoxShopItem(
            name = "보라 모자",
            id = 102,
            description = "보라색 모자입니다.",
            price = 200,
            isOwned = false,
            itemCategory = ItemCategory.HAT,
            imageRes = R.drawable.foxitem_purple_hat,
            offsetX = 200f,
            offsetY = -300f,
        ),

        201 to FoxShopItem(
            name = "주황 스카프",
            id = 201,
            description = "주황색 스카프입니다.",
            price = 200,
            isOwned = false,
            itemCategory = ItemCategory.SCARF,
            imageRes = R.drawable.foxitem_scarf,
            offsetX = 0f,
            offsetY = 700f,
        ),

        301 to FoxShopItem(
            name = "광대 코",
            id = 301,
            description = "광대 코입니다.",
            price = 200,
            isOwned = false,
            itemCategory = ItemCategory.ACCESSORY,
            imageRes = R.drawable.foxitem_crown_nose,
            offsetX = 450f,
            offsetY = 530f,
        ),
    )
}
