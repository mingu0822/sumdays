package com.example.sumdays.daily.diary

import com.google.gson.annotations.SerializedName

// Api 응답
data class AnalysisResponse(
    // @SerializedName("ai_comment") val aiComment: String?,
    val aiComment: String? = null,
    @SerializedName("analysis")   val analysis: AnalysisBlock?,
    @SerializedName("diary")      val diary: String?,        // 병합된 텍스트 여기
    @SerializedName("entry_date") val entryDate: String?,
    @SerializedName("icon")       val icon: String?,
    @SerializedName("user_id")    val userId: Int?
)

data class AnalysisBlock(
    @SerializedName("emotion_score") val emotionScore: Double?,
    @SerializedName("keywords")      val keywords: List<String>?
)
