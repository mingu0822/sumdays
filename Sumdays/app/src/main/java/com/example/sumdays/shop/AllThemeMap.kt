package com.example.sumdays.shop

import com.example.sumdays.R
import com.example.sumdays.theme.Theme

// Theme과 테마 이름을 연결짓는 오브젝트
object AllThemeMap {
    val allThemeMap: MutableMap<String, Theme> = mutableMapOf(
        "default" to Theme(
            name = "default",
            id = 1,
            description = "기본 라이트 테마",
            price = 320,
            themePreviewImage = R.drawable.login_fox_login_logo,
            primaryColor = R.color.theme_default_primary,
            buttonColor = R.color.theme_default_primary,
            backgroundColor = R.color.theme_default_background,
            blockColor = R.color.theme_default_block,
            calendarBackgroundImage = R.drawable.theme_default_background,
            memoImage = R.drawable.memo_fox_bubble,
            isOwned = true
        ),

        "forest" to Theme(
            name = "forest",
            id = 2,
            description = "숲 테마",
            price = 280,
            themePreviewImage = R.drawable.nav_fox_button,
            primaryColor = R.color.theme_forest_green,
            buttonColor = R.color.theme_forest_green,
            backgroundColor = R.color.theme_forest_bg,
            blockColor = R.color.theme_forest_block,
            calendarBackgroundImage = R.drawable.statistics_background_morning,
            memoImage = R.drawable.memo_fox_bubble,
            isOwned = false,
        )
    )
}