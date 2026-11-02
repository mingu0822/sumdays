package com.example.sumdays.daily.memo

import android.content.Context
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.network.ApiClient
import com.example.sumdays.shop.PointPrefs
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

data class MergeTicketProduct(
    val packId: String,
    val ticketCount: Int,
    val pointPrice: Int,
)

data class MergeMarbleState(
    val date: String,
    val freeLimit: Int,
    val usedCount: Int,
    val freeRemainingCount: Int,
    val ticketBalance: Int,
    val remainingCount: Int,
    val ticketProducts: List<MergeTicketProduct>,
    val passActive: Boolean,
    val passExpiresAt: String? = null,
    val pointBalance: Int,
    val duplicate: Boolean = false,
)

data class MergeTicketPurchase(
    val requestId: String,
    val packId: String,
    val expectedPrice: Int,
)

data class MergePassPurchase(
    val productId: String,
    val purchaseToken: String,
)

class MergeMarbleRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("merge_ticket_purchase", Context.MODE_PRIVATE)
    private val gson = Gson()

    suspend fun refresh(): MergeMarbleState {
        val userId = SessionManager.getUserId()
        val token = SessionManager.getToken() ?: error("로그인이 필요합니다.")
        val response = ApiClient.api.getMergeMarble("Bearer $token")
        if (!response.isSuccessful) error("합치기 이용권을 불러오지 못했습니다. 연결을 확인해 주세요.")
        val state = response.body()?.data ?: error("합치기 이용권 응답이 없습니다.")
        checkUser(userId)
        PointPrefs.savePoint(appContext, userId, state.pointBalance)
        return state
    }

    /**
     * 응답 유실 뒤에는 동일한 구매 요청만 다시 보낸다. 다른 횟수권을 고르면 먼저
     * 이전 요청을 확인하므로 포인트가 중복 차감되지 않는다.
     */
    suspend fun purchaseTicket(product: MergeTicketProduct): MergeMarbleState = purchaseMutex.withLock {
        val userId = SessionManager.getUserId()
        val token = SessionManager.getToken() ?: error("로그인이 필요합니다.")
        val key = "user_$userId"
        val pending = prefs.getString(key, null)?.let { gson.fromJson(it, MergeTicketPurchase::class.java) }
            ?: MergeTicketPurchase(
                requestId = UUID.randomUUID().toString(),
                packId = product.packId,
                expectedPrice = product.pointPrice,
            ).also {
                withContext(Dispatchers.IO) {
                    check(prefs.edit().putString(key, gson.toJson(it)).commit()) { "횟수권 구매 요청을 저장하지 못했습니다." }
                }
            }
        val response = ApiClient.api.purchaseMergeTicket("Bearer $token", pending)
        checkUser(userId)
        if (!response.isSuccessful) {
            if (response.code() in 400..499 && response.code() !in listOf(408, 429)) {
                prefs.edit().remove(key).apply()
            }
            val message = runCatching {
                gson.fromJson(response.errorBody()?.string(), JsonObject::class.java).get("message").asString
            }.getOrNull() ?: "횟수권 구매 결과를 확인하지 못했습니다. 다시 확인해 주세요."
            error(message)
        }
        val state = response.body()?.data ?: error("횟수권 구매 결과를 확인하지 못했습니다.")
        PointPrefs.savePoint(appContext, userId, state.pointBalance)
        prefs.edit().remove(key).apply()
        state
    }

    suspend fun redeemPass(productId: String, purchaseToken: String): MergeMarbleState = purchaseMutex.withLock {
        val userId = SessionManager.getUserId()
        val token = SessionManager.getToken() ?: error("로그인이 필요합니다.")
        val response = ApiClient.api.redeemMergePass(
            "Bearer $token",
            MergePassPurchase(productId, purchaseToken),
        )
        checkUser(userId)
        if (!response.isSuccessful) {
            val message = runCatching {
                gson.fromJson(response.errorBody()?.string(), JsonObject::class.java).get("message").asString
            }.getOrNull() ?: "무제한 패스 구매 결과를 확인하지 못했습니다. 다시 확인해 주세요."
            error(message)
        }
        val state = response.body()?.data ?: error("무제한 패스 구매 결과를 확인하지 못했습니다.")
        PointPrefs.savePoint(appContext, userId, state.pointBalance)
        state
    }

    private fun checkUser(userId: Int) {
        check(userId >= 0 && SessionManager.getUserId() == userId) { "로그인 계정이 변경되었습니다." }
    }

    private companion object { val purchaseMutex = Mutex() }
}
