package com.example.sumdays.daily.memo

import android.util.Log
import com.example.sumdays.daily.memo.MemoMergeUtils.convertStylePromptToMap
import com.example.sumdays.data.dao.UserStyleDao
import com.example.sumdays.network.ApiClient
import com.example.sumdays.settings.prefs.UserStatsPrefs

object MoodRepository {

    private val defaultStylePrompt: Map<String, Any> = mapOf(
        "character_concept" to "일상적인 삶을 살아가는 평범한 사람. 소소한 일상을 관찰하고 기록하는 성향을 가진 인물.",
        "emotional_tone" to "감정이 드러나지 않고 중립적인 톤으로, 일상적인 사건을 기록하는 데 집중한다.",
        "formality" to "비격식적인 대화체로, 자연스러운 흐름을 유지하며 친근한 느낌을 준다.",
        "lexical_choice" to "일상적인 단어와 표현을 사용하여 친근함을 느끼게 한다.",
        "pacing" to "느긋하고 여유로운 흐름, 빠르지 않게 사건을 나열.",
        "punctuation_style" to "기본적인 문장 부호 사용, 복잡한 구두점은 없다.",
        "sentence_endings" to listOf("~었다.", "~했다.", "~었다고 생각했다."),
        "sentence_length" to "중간 길이의 문장들이 많으며, 간결하게 표현되어 있다.",
        "sentence_structure" to "주어-서술어 구조가 명확하며, 문장이 단순하고 직관적이다.",
        "special_syntax" to "일상적인 표현을 그대로 사용하며, 특별한 구문은 없음.",
        "speech_quirks" to "특별한 말투의 버릇은 없으며, 대화체적인 표현이 자연스럽다.",
        "tone" to "담담하고 차분한 어조로 일상의 소소한 사건들을 서술."
    )

    private val defaultStyleExamples: List<String> = listOf(
        "일어나서 물을 한 잔 마셨다",
        "조용히 하루가 지나갔다",
        "일기를 쓰고 자야겠다고 생각했다",
        "창문을 여니 바깥 공기가 들어왔다"
    )

    suspend fun generateMood(
        diary: String,
        userStatsPrefs: UserStatsPrefs,
        userStyleDao: UserStyleDao
    ): String? {
        return try {
            val activeStyleId = userStatsPrefs.getActiveStyleId()
            val stored = userStyleDao.getStyleById(activeStyleId)
            val stylePrompt: Map<String, Any>
            val styleExamples: List<String>
            if (stored != null) {
                stylePrompt = convertStylePromptToMap(stored.stylePrompt)
                styleExamples = stored.styleExamples
            } else {
                stylePrompt = defaultStylePrompt
                styleExamples = defaultStyleExamples
            }
            val request = MoodRequest(
                diary = diary,
                stylePrompt = stylePrompt,
                styleExamples = styleExamples
            )
            val response = ApiClient.api.generateMood(request)
            if (!response.isSuccessful) return null
            val json = response.body() ?: return null
            json.getAsJsonObject("result")
                ?.get("mood")?.takeIf { !it.isJsonNull }?.asString
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("MoodRepository", "generateMood failed", e)
            null
        }
    }
}
