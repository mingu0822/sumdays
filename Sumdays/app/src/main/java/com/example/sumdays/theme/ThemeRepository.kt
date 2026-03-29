package com.example.sumdays.theme

import com.example.sumdays.R
import com.example.sumdays.shop.AllThemeMap

object ThemeRepository {
    val ownedThemes: MutableMap<String, Theme> = mutableMapOf()
    val allThemeMap: MutableMap<String, Theme> = AllThemeMap.allThemeMap

    public fun updateOwned() {

        ownedThemes.clear()

        ownedThemes.putAll(
            allThemeMap.filterValues { theme ->
                theme.isOwned
            }
        )
    }
}