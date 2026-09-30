package com.example.sumdays.theme

import com.example.sumdays.data.prefs.userPrefs.shop.AllItemMap
import com.example.sumdays.data.prefs.userPrefs.shop.FoxShopItem

object FoxRepository {
    val ownedFoxes: MutableMap<Int, FoxShopItem> = mutableMapOf()
    val allFoxMap: MutableMap<Int, FoxShopItem> = AllItemMap.allItemMap

    fun updateOwned() {

        ownedFoxes.clear()

        ownedFoxes.putAll(
            allFoxMap.filterValues { fox ->
                fox.isOwned
            }
        )
    }
}