package com.example.sumdays.customize

data class CompleteFox(
    val id: Int,
    val name: String,

    // 미리보기 이미지
    var previewImage: Int,
    // 실제 조합된 이미지
    val previewPath: String?,

    // 구성 요소
    val glasses: Int?,
    val hat: Int?,
    val scarf: Int?,
    val accessory: Int?,

    var isSelected: Boolean = false
)