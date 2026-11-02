package com.example.sumdays.daily.memo

import android.content.Context
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.daily.memo.MemoMergeUtils.extractMergedText
import com.example.sumdays.daily.memo.MemoMergeUtils.extractMood
import com.example.sumdays.data.dao.UserStyleDao
import com.example.sumdays.network.ApiClient
import com.example.sumdays.settings.prefs.LabsPrefs
import com.example.sumdays.settings.prefs.UserStatsPrefs
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

data class MergeResult(val text: String, val mood: String? = null)

interface MemoMergeGateway {
    suspend fun merge(payloads: List<MemoPayload>, endFlag: Boolean, onPartial: suspend (String) -> Unit = {}): MergeResult
    suspend fun generateMood(diary: String): String?
}

sealed class MergeException(message: String) : Exception(message) {
    class NetworkException : MergeException("네트워크 연결을 확인해주세요.")
    class ServerError(code: Int) : MergeException("서버 오류가 발생했습니다. (코드: $code)")
    class TimeoutException : MergeException("서버 응답 시간이 초과되었습니다.")
    class EmptyBodyException : MergeException("서버로부터 받은 데이터가 없습니다.")
    class UnknownException(e: Throwable) : MergeException("알 수 없는 오류: ${e.message}")
    class RequestRejected(message: String) : MergeException(message)
}


/** Owns request IDs, account checks and streaming. No View or adapter dependencies. */
class MemoMergeRepository(
    context: Context,
    private val userStatsPrefs: UserStatsPrefs,
    private val userStyleDao: UserStyleDao,
) : MemoMergeGateway {
    private val context = context.applicationContext
    private val styleProvider = MergeStyleProvider(userStatsPrefs, userStyleDao)

    override suspend fun generateMood(diary: String): String? =
        MoodRepository.generateMood(diary, userStatsPrefs, userStyleDao)

    @Throws(MergeException::class)
    override suspend fun merge(
        payloads: List<MemoPayload>,
        endFlag: Boolean,
        onPartial: suspend (String) -> Unit
    ): MergeResult = withContext(Dispatchers.IO) {

        val userId = SessionManager.getUserId()
        val token = SessionManager.getToken()
            ?: throw MergeException.RequestRejected("로그인 후 합치기를 사용할 수 있어요.")

        val style = styleProvider.load()
        val stylePrompt = style.stylePrompt
        val styleExample = style.styleExample
        val styleVector = style.styleVector

        val baseRequest = MergeRequest(
            memos = payloads,
            endFlag = endFlag,
            stylePrompt = stylePrompt,
            styleExamples = styleExample,
            styleVector = styleVector,
            advancedFlag = LabsPrefs.getAdvancedFlag(context),
            temperature = LabsPrefs.getTemperature(context),
            lengthLevel = LabsPrefs.getLengthLevel(context),
        )
        val request = baseRequest.copy(requestId = PendingMergeStore.getOrCreate(context, userId, baseRequest))

        fun checkAccount() {
            if (SessionManager.getUserId() != userId) throw MergeException.RequestRejected("로그인 계정이 변경되었습니다.")
        }
        fun reject(code: Int, body: String?): Nothing {
            val json = runCatching { Gson().fromJson(body, JsonObject::class.java) }.getOrNull()
            val reason = json?.get("code")?.asString
            if ((code in 400..499 && code !in listOf(408, 429) && reason != "MERGE_IN_PROGRESS") ||
                reason in listOf("MERGE_FAILED", "MERGE_EXPIRED")) {
                PendingMergeStore.clear(context, userId, request)
            }
            throw MergeException.RequestRejected(json?.get("message")?.asString ?: "합치기 요청을 처리하지 못했습니다. ($code)")
        }

        try {
            // 최종 일기
            if (endFlag) {
                val response = ApiClient.api.mergeMemos("Bearer $token", request)
                checkAccount()
                if (!response.isSuccessful) {
                    reject(response.code(), response.errorBody()?.string())
                }
                val json = response.body() ?: throw MergeException.EmptyBodyException()
                val mood = extractMood(json)
                val text = extractMergedText(json)
                if (text.isBlank()) throw MergeException.EmptyBodyException()
                PendingMergeStore.clear(context, userId, request)
                return@withContext MergeResult(text, mood)
            }

            // 스트리밍 처리
            val call = ApiClient.api.mergeMemosStream("Bearer $token", request)
            val response = call.execute()

            if (!response.isSuccessful) {
                reject(response.code(), response.errorBody()?.string())
            }

            val stream = response.body()?.byteStream()
                ?: throw MergeException.EmptyBodyException()

            val sb = StringBuilder()
            val charBuffer = CharArray(64)
            InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    checkAccount()
                    val read = reader.read(charBuffer)
                    if (read == -1) break
                    currentCoroutineContext().ensureActive()
                    checkAccount()
                    sb.append(String(charBuffer, 0, read))
                    onPartial(sb.toString())
                }
            }
            checkAccount()
            if (sb.isBlank()) throw MergeException.EmptyBodyException()
            PendingMergeStore.clear(context, userId, request)
            MergeResult(sb.toString())

        } catch (e: java.net.SocketTimeoutException) {
            throw MergeException.TimeoutException()
        } catch (e: java.net.ConnectException) {
            throw MergeException.NetworkException()
        } catch (e: java.net.UnknownHostException) {
            throw MergeException.NetworkException()
        } catch (e: MergeException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw MergeException.UnknownException(e)
        }
    }


}
