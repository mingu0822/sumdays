package com.example.sumdays.theme

data class Theme(
    val name: String,
    val id: Int,
    val description: String,
    val price: Int,

    // 테마 글씨색
    val themeTextColorBasic: Int,
    val themeTextColorSpecialA: Int,
    val themeTextColorSpecialB: Int,

    // 테마 색상
    val themeColorA: Int,
    val themeColorB: Int,
    val themeColorC: Int,
    val themeColorD: Int,


    // 블럭 모양
    val blockStyleA: Int,
    val blockStyleB: Int,
    val blockStyleC: Int,
    val blockStyleD: Int,


    // 배경 색상 (배경 이미지가 존재하지 않을 때)
    val backgroundColor: Int,

    // 테마 미리보기 이미지 - 뭐지
    val themePreviewImage: Int,

    // 캘린더 배경 이미지 - 쓰이나?
    val calendarBackgroundImage: Int,

    // 메모 이미지
    val memoImage: Int,

    var isOwned: Boolean,


    //버튼 아이콘

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
    val addImageIcon: Int,
    // 메모 보기 버튼
    val seeMemo: Int,
    // 일기 보기 버튼
    val seeDiary: Int,
    //캘린더 이아콘
    val calendarIcon: Int
)
