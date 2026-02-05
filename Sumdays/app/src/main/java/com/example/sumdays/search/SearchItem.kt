package com.example.sumdays.search

/** 아주 간단한 모델(나중에 네 Memo/Diary 모델로 바꾸면 됨) */
data class SearchItem(
    val id: Long,
    val title: String,
    val preview: String
)
