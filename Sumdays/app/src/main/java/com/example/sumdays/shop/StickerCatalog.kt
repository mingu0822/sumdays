package com.example.sumdays.shop

import com.example.sumdays.R

object StickerCatalog {
    // Stable IDs for purchased inventory and future diary placements.
    val items: List<StickerShopItem> = listOf(
        StickerShopItem(1001, "마음 한 조각", "따뜻한 마음을 담은 하트", 100, R.drawable.sticker_heart),
        StickerShopItem(1002, "반짝이는 하루", "오늘도 반짝반짝 빛나길", 100, R.drawable.sticker_star),
        StickerShopItem(1003, "행운 한 잎", "작은 행운을 전하는 네잎클로버", 150, R.drawable.sticker_clover),
        StickerShopItem(1004, "꽃처럼 활짝", "활짝 피어난 오늘의 기분", 150, R.drawable.sticker_flower),
        StickerShopItem(1005, "살포시 발도장", "귀여운 발자국으로 남기는 응원", 150, R.drawable.sticker_paw),
        StickerShopItem(1006, "비 온 뒤 무지개", "내일은 더 다채로운 하루", 200, R.drawable.sticker_rainbow)
    )
}
