package com.example.sumdays.shop

import com.example.sumdays.R
import com.example.sumdays.theme.FoxChar
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
            textPrimaryColor = R.color.theme_default_primary,
            buttonColor = R.color.theme_default_primary,
            backgroundColor = R.color.theme_default_background,
            blockColor = R.color.theme_default_block,
            calendarBackgroundImage = R.drawable.theme_default_background,
            memoImage = R.drawable.memo_fox_bubble,
            backIcon = R.drawable.ic_arrow_back_white,
            forwardIcon = R.drawable.ic_arrow_forward_white,
            searchIcon = R.drawable.ic_search_white,
            sendIcon = R.drawable.ic_send_white,
            recordIcon = R.drawable.ic_mic_white,
            addImageIcon = R.drawable.ic_image_white,

            isOwned = true
        ),

        "forest" to Theme(
            name = "forest",
            id = 2,
            description = "숲 테마",
            price = 280,
            themePreviewImage = R.drawable.nav_fox_button,
            textPrimaryColor = R.color.theme_forest_green,
            buttonColor = R.color.theme_forest_green,
            backgroundColor = R.color.theme_forest_bg,
            blockColor = R.color.theme_forest_block,
            calendarBackgroundImage = R.drawable.statistics_background_morning,
            memoImage = R.drawable.memo_fox_bubble,
            backIcon = R.drawable.ic_arrow_back_black,
            forwardIcon = R.drawable.ic_arrow_forward_black,
            searchIcon = R.drawable.ic_search_gray,
            sendIcon = R.drawable.ic_send_black,
            recordIcon = R.drawable.ic_mic_black,
            addImageIcon = R.drawable.ic_image_black,
            isOwned = false,
        )
    )
}