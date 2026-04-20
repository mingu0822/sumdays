package com.example.sumdays.daily.diary

import android.util.Log
import com.example.sumdays.data.viewModel.DailyEntryViewModel
import com.example.sumdays.network.ApiClient
// import com.example.sumdays.utils.PersonaManager
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AnalysisRepository {

    // 요청 후 DB에 저장
    suspend fun requestAnalysis(
        date: String,
        diary : String?,
        // personaId: Int,
        // context: Context,
        viewModel: DailyEntryViewModel
    ): AnalysisResponse? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("AnalysisRepository", "서버에 '$date' 분석 결과 요청...")

                if (diary == null) {
                    Log.e("AnalysisRepository", "Date cannot be null for analysis request")
                    return@withContext null // Null이면 더 진행하지 않음
                }

                // // personaId를 통해 피드백 페르소나 불러오기
                // val persona = PersonaManager(context).getPersonaById(personaId)
                //     ?: throw IllegalArgumentException("Invalid Persona ID: $personaId")

                // 요청 객체 생성
                val request = AnalysisRequest(diary = diary)

                // API 호출
                val response = ApiClient.api.diaryAnalyze(request)

                val json = response.body() ?: throw IllegalStateException("Empty body")
                val analysis = extractAnalysis(json)

                // update db
                viewModel.updateEntry(
                    date = date,
                    diary = analysis.diary, // ?
                    keywords = analysis.analysis?.keywords?.joinToString(";"),
                    aiComment = null,
                    emotionScore = analysis.analysis?.emotionScore,
                    emotionIcon = null,
                    themeIcon = analysis.icon
                )
                // 서버가 돌려준 병합 결과 반환
                return@withContext analysis
            } catch (e: Exception) {
                Log.e("AnalysisRepository", "'$date' 분석 결과 요청 중 예외 발생", e)
                null // 네트워크 오류 등 예외 발생
            } as AnalysisResponse?
        }
    }

    private fun extractAnalysis(json: JsonObject): AnalysisResponse {
        // 기본값 설정 (파싱 실패 대비)
        var aiComment: String? = null
        var analysisBlock: AnalysisBlock? = null
        var diary: String? = null
        var entryDate: String? = null
        var icon: String? = null
        var userId: Int? = null

        // "result" 필드가 있는지, JsonObject 타입인지 확인
        if (json.has("result") && json.get("result").isJsonObject) {
            val resultObj = json.getAsJsonObject("result")

            // 각 필드를 안전하게 추출 (getAsString 등은 null일 경우 예외 발생 가능하므로 get() 사용 후 타입 확인)
            // aiComment = resultObj.get("ai_comment")?.takeIf { !it.isJsonNull }?.asString
            diary = resultObj.get("diary")?.takeIf { !it.isJsonNull }?.asString
            entryDate = resultObj.get("entry_date")?.takeIf { !it.isJsonNull }?.asString
            icon = resultObj.get("icon")?.takeIf { !it.isJsonNull }?.asString
            userId = resultObj.get("user_id")?.takeIf { !it.isJsonNull }?.asInt

            // "analysis" 블록 파싱
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

        // 최종 AnalysisResponse 객체 생성 및 반환
        return AnalysisResponse(
            aiComment = aiComment,
            analysis = analysisBlock,
            diary = diary,
            entryDate = entryDate,
            icon = icon,
            userId = userId
        )
    }
}
