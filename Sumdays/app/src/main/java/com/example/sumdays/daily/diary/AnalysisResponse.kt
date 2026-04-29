package com.example.sumdays.daily.diary

import com.google.gson.annotations.SerializedName

// Api 응답
data class AnalysisResponse(
    @SerializedName("mood")        val mood: String?,
    @SerializedName("analysis")    val analysis: AnalysisBlock?,
    @SerializedName("diary")       val diary: String?,
    @SerializedName("entry_date")  val entryDate: String?,
    @SerializedName("icon")        val icon: String?,
    @SerializedName("user_id")     val userId: Int?
)

data class AnalysisBlock(
    @SerializedName("emotion_score") val emotionScore: Double?,
    @SerializedName("keywords")      val keywords: List<String>?
)
