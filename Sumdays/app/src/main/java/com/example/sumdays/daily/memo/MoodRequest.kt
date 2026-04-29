package com.example.sumdays.daily.memo

import com.google.gson.annotations.SerializedName

data class MoodRequest(
    @SerializedName("diary")          val diary: String,
    @SerializedName("style_prompt")   val stylePrompt: Map<String, Any>,
    @SerializedName("style_examples") val styleExamples: List<String>
)
