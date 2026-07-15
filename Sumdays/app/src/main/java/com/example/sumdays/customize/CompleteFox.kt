package com.example.sumdays.customize

data class CompleteFox(
    val id: Int,
    val name: String,

    // 미리보기 이미지
    val previewImage: Int,

    // 구성 요소
    val face: Int,
    val hat: Int?,
    val glasses: Int?,
    val neck: Int?,

    var isSelected: Boolean = false
)