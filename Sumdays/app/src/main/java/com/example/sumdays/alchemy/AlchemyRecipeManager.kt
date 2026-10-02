package com.example.sumdays.alchemy

import com.example.sumdays.R
import com.example.sumdays.customize.CompleteFox
import com.example.sumdays.customize.FoxItemPlacement
import com.example.sumdays.shop.FoxShopItem
import com.example.sumdays.shop.ItemCategory

object AlchemyRecipeManager {

    fun createFox(
        id: Int,
        name: String,
        items: List<FoxShopItem>,
        placements: List<FoxItemPlacement> = emptyList()
    ): CompleteFox {
        val face = items.find { it.itemCategory == ItemCategory.FOXFACE }

        return CompleteFox(

            id = id,

            name = name,

            previewImage =
                face?.imageRes ?: R.drawable.dailyread_fox_face_level_3,

            foxFace = face?.id,

            previewPath = null,

            placements = placements.filter { placement ->
                items.any { it.id == placement.itemId && it.itemCategory != ItemCategory.FOXFACE }
            },

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
