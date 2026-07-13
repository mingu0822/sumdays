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
            themeTextColorBasic = R.color.theme_default_textcolor_basic,
            themeTextColorSpecialA = R.color.theme_default_textcolor_special,
            themeTextColorSpecialB = R.color.theme_default_primary,
            themeColorA = R.color.foxrange,
            themeColorB = R.color.dark_foxrange,
            themeColorC = R.color.background,
            themeColorD = R.color.black, //아무거나
            blockStyleA = R.drawable.bg_image_drawer_rounded_light,
            blockStyleB = R.drawable.bg_image_drawer_rounded_light_alpha80, //여우귀
            blockStyleC = R.drawable.blockstyle_c_basic,
            blockStyleD = R.drawable.blockstyle_d_basic,
            blockStyleFox = R.drawable.blockstyle_b_basic_nine,
            themePreviewImage = R.drawable.login_fox_login_logo,
            backgroundColor = R.color.theme_default_background,
            calendarBackgroundImage = R.drawable.theme_default_background,
            memoImage = R.drawable.memo_fox_bubble,
            backIcon = R.drawable.ic_arrow_back_default,
            forwardIcon = R.drawable.ic_arrow_forward_default,
            searchIcon = R.drawable.ic_search_white,
            sendIcon = R.drawable.ic_send_white,
            recordIcon = R.drawable.ic_mic_white,
            addImageIcon = R.drawable.ic_image_white,
            seeMemo = R.drawable.calendar_shape_fox_today,
            seeDiary = R.drawable.ic_setting_menu_gray,
            calendarIcon = R.drawable.ic_calendar,

            previewImage = R.drawable.dailyread_fox_face_level_5,

            isOwned = true
        ),

        "forest" to Theme(
            name = "forest",
            id = 2,
            description = "숲 테마",
            price = 280,
            themeTextColorBasic = R.color.theme_forest_textcolor_basic,
            themeTextColorSpecialA = R.color.theme_forest_textcolor_special,
            themeTextColorSpecialB = R.color.theme_forest_green,
            themeColorA = R.color.theme_forest_green,
            themeColorB = R.color.theme_forest_block,
            themeColorC = R.color.white,
            themeColorD = R.color.black, //아무거나
            blockStyleA = R.drawable.bg_image_drawer_rounded_light,
            blockStyleB = R.drawable.bg_image_drawer_rounded_light_alpha80,
            blockStyleC = R.drawable.theme_forest_block,
            blockStyleD = R.drawable.theme_default_background,
            blockStyleFox = R.drawable.blockstyle_b_basic_nine,
            themePreviewImage = R.drawable.nav_fox_button,
            backgroundColor = R.color.theme_forest_bg,
            calendarBackgroundImage = R.drawable.statistics_background_morning,
            memoImage = R.drawable.memo_fox_bubble,
            backIcon = R.drawable.ic_arrow_back_black,
            forwardIcon = R.drawable.ic_arrow_forward_black,
            searchIcon = R.drawable.ic_search_gray,
            sendIcon = R.drawable.ic_send_black,
            recordIcon = R.drawable.ic_mic_black,
            addImageIcon = R.drawable.ic_image_black,
            seeMemo = R.drawable.calendar_shape_fox_today,
            seeDiary = R.drawable.ic_setting_menu_gray,
            calendarIcon = R.drawable.ic_calendar,

            previewImage = R.drawable.dailyread_fox_face_level_4,
            isOwned = false,
        )
    )
}