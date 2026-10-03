package com.example.sumdays.theme

import com.example.sumdays.shop.AllThemeMap

object ThemeRepository {
    val ownedThemes: MutableMap<Int, Theme> = mutableMapOf()
    val allThemeMap: MutableMap<Int, Theme> = AllThemeMap.allThemeMap

    fun updateOwned() {

        ownedThemes.clear()

        ownedThemes.putAll(
            allThemeMap.filterValues { theme ->
                theme.isOwned
            }
        )
    }
}