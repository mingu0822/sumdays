package com.example.sumdays.settings.profileimage

enum class ProfileImageItemType { FACE, EYES, MOUTH, ACC }

data class ProfileImageItem(
    val id: Int,
    val type: ProfileImageItemType,
    val resId: Int
)

data class ProfileImageCategory(
    val title: String,
    val type: ProfileImageItemType,
    val parts: List<ProfileImageItem>
)