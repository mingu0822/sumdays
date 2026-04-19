package com.example.sumdays.social

data class SocialUser(
    val name: String,
    val id: Int,
    val profileEmoji: String,
    val summary: String,
    val isFavorite: Boolean
)
