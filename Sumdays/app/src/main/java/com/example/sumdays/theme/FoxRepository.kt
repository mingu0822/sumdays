package com.example.sumdays.theme

import com.example.sumdays.shop.AllFoxMap

object FoxRepository {
    val ownedFoxes: MutableMap<String, FoxChar> = mutableMapOf()
    val allFoxMap: MutableMap<String, FoxChar> = AllFoxMap.allFoxMap

    fun updateOwned() {

        ownedFoxes.clear()

        ownedFoxes.putAll(
            allFoxMap.filterValues { fox ->
                fox.isOwned
            }
        )
    }
}