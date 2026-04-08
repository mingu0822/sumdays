package com.example.sumdays.theme

data class Theme(
    val name: String,

    val id: Int,

    val description: String,

    val price: Int,

    // 테마 미리보기 이미지
    val themePreviewImage: Int,

    // 주요 멘트 색상
    val textPrimaryColor: Int,

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

    var isOwned: Boolean,

    // 뒤로가기 버튼
    val backIcon: Int,
    // 앞으로 가기 버튼
    val forwardIcon: Int,
    // 검색 버튼
    val searchIcon: Int,
    // 전송 버튼
    val sendIcon: Int,
    // 녹음 버튼
    val recordIcon: Int,
    // 사진 추가 버튼
    val addImageIcon: Int
)
