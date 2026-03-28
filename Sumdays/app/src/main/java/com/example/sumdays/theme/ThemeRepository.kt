package com.example.sumdays.theme

import com.example.sumdays.R

object ThemeRepository {

    val ownedThemes: MutableMap<String, Theme> = mutableMapOf(
        "default" to Theme(
            name = "default",
            themePreviewImage = R.drawable.login_fox_login_logo,
            primaryColor = R.color.theme_default_primary,
            buttonColor = R.color.theme_default_primary,
            backgroundColor = R.color.theme_default_background,
            blockColor = R.color.theme_default_block,
            calendarBackgroundImage = R.drawable.theme_default_background,
            memoImage = R.drawable.memo_fox_bubble,
            foxIcon = FoxChar(
                name = "default_fox",
                sumFoxIcon = R.drawable.dailyread_fox_face_level_3,
                commentFoxIcon = R.drawable.dailyread_fox_face_level_3
            )
        ),

        "forest" to Theme(
            name = "forest",
            themePreviewImage = R.drawable.nav_fox_button,
            primaryColor = R.color.theme_forest_green,
            buttonColor = R.color.theme_forest_green,
            backgroundColor = R.color.theme_forest_bg,
            blockColor = R.color.theme_forest_block,
            calendarBackgroundImage = R.drawable.statistics_background_morning,
            memoImage = R.drawable.memo_fox_bubble,
            foxIcon = FoxChar(
                name = "forest_fox",
                sumFoxIcon = R.drawable.dailyread_fox_face_level_1,
                commentFoxIcon = R.drawable.dailyread_fox_face_level_5
            )
        )
    )
}