package com.example.sumdays.alchemy

import com.example.sumdays.R
import com.example.sumdays.customize.CompleteFox
import com.example.sumdays.shop.FoxShopItem
import com.example.sumdays.shop.ItemCategory

object AlchemyRecipeManager {

    fun createFox(
        id: Int,
        name: String,
        items: List<FoxShopItem>
    ): CompleteFox {

        return CompleteFox(

            id = id,

            name = name,

            previewImage = R.drawable.dailyread_fox_face_level_3,

            glasses = items.find {
                it.itemCategory == ItemCategory.GLASSES
            }?.id,

            hat = items.find {
                it.itemCategory == ItemCategory.HAT
            }?.id,

            scarf = items.find {
                it.itemCategory == ItemCategory.SCARF
            }?.id,

            accessory = items.find {
                it.itemCategory == ItemCategory.ACCESSORY
            }?.id
        )
    }
}