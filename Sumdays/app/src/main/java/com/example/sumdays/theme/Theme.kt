package com.example.sumdays.theme

data class Theme(
    val name: String,

    val id: Int,

    val description: String,

    val price: Int,

    // 테마 미리보기 이미지
    val themePreviewImage: Int,

    // 주요 멘트 색상
    val primaryColor: Int,

    // 버튼 색상
    val buttonColor: Int,

    // 배경 색상 (배경 이미지가 존재하지 않을 때)
    val backgroundColor: Int,

    // 블럭 색상
    val blockColor: Int,

    // 캘린더 배경 이미지
    val calendarBackgroundImage: Int,

    // 메모 이미지
    val memoImage: Int,

    // 여우 캐릭터
    val foxIcon: FoxChar,

    var isOwned: Boolean,
)