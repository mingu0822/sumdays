package com.example.sumdays.daily.diary

import android.util.Log
import com.example.sumdays.data.viewModel.DailyEntryViewModel
import com.example.sumdays.network.ApiClient
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AnalysisRepository {

    // 요청 후 DB에 저장
    suspend fun requestAnalysis(
        date: String,
        diary: String?,
        viewModel: DailyEntryViewModel,
        precomputedMood: String? = null
    ): AnalysisResponse? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("AnalysisRepository", "서버에 '$date' 분석 결과 요청...")

                if (diary == null) {
                    Log.e("AnalysisRepository", "Date cannot be null for analysis request")
                    return@withContext null
                }

                val request = AnalysisRequest(diary = diary)
                val response = ApiClient.api.diaryAnalyze(request)

                val json = response.body() ?: throw IllegalStateException("Empty body")
                val analysis = extractAnalysis(json)

                viewModel.updateEntry(
                    date = date,
                    diary = analysis.diary,
                    keywords = analysis.analysis?.keywords?.joinToString(";"),
                    aiComment = precomputedMood ?: analysis.mood,
                    emotionScore = analysis.analysis?.emotionScore,
                    emotionIcon = null,
                    themeIcon = analysis.icon
                )
                return@withContext analysis
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AnalysisRepository", "'$date' 분석 결과 요청 중 예외 발생", e)
                null
            } as AnalysisResponse?
        }
    }

    private fun extractAnalysis(json: JsonObject): AnalysisResponse {
        var mood: String? = null
        var analysisBlock: AnalysisBlock? = null
        var diary: String? = null
        var entryDate: String? = null
        var icon: String? = null
        var userId: Int? = null

        if (json.has("result") && json.get("result").isJsonObject) {
            val resultObj = json.getAsJsonObject("result")

            mood = resultObj.get("mood")?.takeIf { !it.isJsonNull }?.asString
            diary = resultObj.get("diary")?.takeIf { !it.isJsonNull }?.asString
            entryDate = resultObj.get("entry_date")?.takeIf { !it.isJsonNull }?.asString
            icon = resultObj.get("icon")?.takeIf { !it.isJsonNull }?.asString
            userId = resultObj.get("user_id")?.takeIf { !it.isJsonNull }?.asInt

            if (resultObj.has("analysis") && resultObj.get("analysis").isJsonObject) {
                val analysisObj = resultObj.getAsJsonObject("analysis")
                val emotionScore = analysisObj.get("emotion_score")?.takeIf { !it.isJsonNull }?.asDouble
                val keywords = analysisObj.get("keywords")?.takeIf { it.isJsonArray }
                    ?.asJsonArray?.mapNotNull { it?.takeIf { !it.isJsonNull }?.asString }

                analysisBlock = AnalysisBlock(emotionScore, keywords)
            }
        } else {
            println("Warning: 'result' field not found or not an object in the JSON.")
        }

        return AnalysisResponse(
            mood = mood,
            analysis = analysisBlock,
            diary = diary,
            entryDate = entryDate,
            icon = icon,
            userId = userId
        )
    }
}
