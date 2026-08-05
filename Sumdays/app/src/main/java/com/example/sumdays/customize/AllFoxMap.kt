package com.example.sumdays.customize

import com.example.sumdays.R


object AllFoxMap {
    val allFoxMap: MutableMap<Int, CompleteFox> = mutableMapOf(
        1 to CompleteFox(
            name = "angry",
            id = 1,

            previewImage = R.drawable.dailyread_fox_face_level_1,
            hat = null,
            glasses = null,
            scarf = null,
            accessory = null,

            isSelected = true,
        ),

        2 to CompleteFox(
            name = "happy",
            id = 2,

            previewImage = R.drawable.dailyread_fox_face_level_5,
            hat = null,
            glasses = null,
            scarf = null,
            accessory = null,

            isSelected = false,
        ),
    )
}