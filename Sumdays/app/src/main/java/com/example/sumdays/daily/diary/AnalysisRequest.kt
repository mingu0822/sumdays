package com.example.sumdays.daily.diary
import com.google.gson.annotations.SerializedName

data class AnalysisRequest(
    @SerializedName("diary") val diary: String?,
    // @SerializedName("persona") val persona: Persona
)
