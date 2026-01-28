package com.example.sumdays.daily.memo

import com.google.gson.annotations.SerializedName

data class MemoPayload(val content: String, val order: Int)

data class MergeRequest(
    val memos: List<MemoPayload>,
    @SerializedName("end_flag") val endFlag: Boolean,
    @SerializedName("style_prompt") val stylePrompt: Map<String, Any>,
    @SerializedName("style_examples") val styleExamples: List<String>,
    @SerializedName("style_vector") val styleVector: List<Float>,
    @SerializedName("advanced_flag") val advancedFlag: Boolean,
    @SerializedName("temperature") val temperature: Float,
    @SerializedName("length_level") val lengthLevel: Int,
)
