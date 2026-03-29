package com.example.sumdays.shop

import com.example.sumdays.R
import com.example.sumdays.theme.FoxChar

object AllFoxMap {
    val allFoxMap: MutableMap<String, FoxChar> = mutableMapOf(
        "default" to FoxChar(
            name = "default",
            id = 3,
            description = "기본 여우",
            price = 300,
            sumFoxIcon = R.drawable.dailyread_fox_face_level_3,
            commentFoxIcon = R.drawable.dailyread_fox_face_level_3,
            isOwned = false
        ),

        "angry" to FoxChar(
            name = "angry",
            id = 4,
            description = "화난 여우",
            price = 400,
            sumFoxIcon = R.drawable.dailyread_fox_face_level_1,
            commentFoxIcon = R.drawable.dailyread_fox_face_level_5,
            isOwned = true
        ),
    )
}