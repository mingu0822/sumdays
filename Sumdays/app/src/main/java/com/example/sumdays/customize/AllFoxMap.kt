package com.example.sumdays.customize

import com.example.sumdays.R


object AllFoxMap {
    val allFoxMap: MutableMap<String, CompleteFox> = mutableMapOf(
        "angry" to CompleteFox(
            name = "angry",
            id = 3,

            previewImage = R.drawable.dailyread_fox_face_level_1,
            face = R.drawable.dailyread_fox_face_level_1,
            hat = null,
            glasses = null,
            neck = null,

            isSelected = true,
        ),

        "happy" to CompleteFox(
            name = "happy",
            id = 4,

            previewImage = R.drawable.dailyread_fox_face_level_5,
            face = R.drawable.dailyread_fox_face_level_5,
            hat = null,
            glasses = null,
            neck = null,

            isSelected = false,
        ),
    )
}