package com.example.sumdays.theme

data class FoxChar (
    val name: String,

    val id: Int,

    val description: String,

    val price: Int,

    // SUM 버튼 여우
    val sumFoxIcon: Int,

    // 평가해주는 여우
    val commentFoxIcon: Int,

    var isOwned: Boolean,
)