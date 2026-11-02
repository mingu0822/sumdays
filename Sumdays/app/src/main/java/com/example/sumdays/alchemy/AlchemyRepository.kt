package com.example.sumdays.alchemy

import android.content.Context
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.customize.AllFoxMap
import com.example.sumdays.customize.CompleteFox
import com.example.sumdays.customize.FoxPrefs
import com.example.sumdays.network.ApiClient
import com.example.sumdays.shop.ItemPrefs
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

data class ConsumeItemsRequest(val requestId: String, val itemIds: List<Int>)
data class InventoryCount(val itemId: Int, val quantity: Int)
data class ConsumeItemsResponse(val duplicate: Boolean, val items: List<InventoryCount>)
private data class PendingAlchemy(val request: ConsumeItemsRequest, val fox: CompleteFox)

/** Persist the recipe AND its resulting fox before spending materials.
 * Recovery uses the same request/fox ID, including after process death.
 */
class AlchemyRepository(context: Context) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("pending_alchemy", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun hasPending(): Boolean = prefs.contains("user_${SessionManager.getUserId()}")

    suspend fun complete(createDraft: ((Int) -> CompleteFox)? = null): CompleteFox = operationMutex.withLock {
        val userId = SessionManager.getUserId()
        val token = SessionManager.getToken()
        check(userId >= 0 && token != null) { "로그인이 필요합니다." }
        val key = "user_$userId"
        val pending = prefs.getString(key, null)?.let { gson.fromJson(it, PendingAlchemy::class.java) }
            ?: run {
                val fox = withContext(Dispatchers.Main.immediate) {
                    checkAccount(userId)
                    // Reserve an ID across unfinished drafts too (including another test account).
                    val reservedIds = prefs.all.values.filterIsInstance<String>().mapNotNull {
                        runCatching { gson.fromJson(it, PendingAlchemy::class.java).fox.id }.getOrNull()
                    }
                    val nextId = Math.addExact((AllFoxMap.allFoxMap.keys + reservedIds).maxOrNull() ?: 0, 1)
                    checkNotNull(createDraft) { "확인할 조합이 없습니다." }.invoke(nextId)
                }
                val ids = listOfNotNull(fox.glasses, fox.hat, fox.scarf, fox.accessory).sorted()
                check(ids.isNotEmpty()) { "아이템을 선택해 주세요." }
                PendingAlchemy(ConsumeItemsRequest(UUID.randomUUID().toString(), ids), fox).also {
                    withContext(Dispatchers.IO) {
                        check(prefs.edit().putString(key, gson.toJson(it)).commit()) { "조합 요청을 저장하지 못했습니다." }
                    }
                }
            }
        checkAccount(userId)
        val response = ApiClient.api.consumeShopItems("Bearer $token", pending.request)
        checkAccount(userId)
        if (!response.isSuccessful) {
            val body = runCatching { gson.fromJson(response.errorBody()?.string(), JsonObject::class.java) }.getOrNull()
            val code = body?.get("code")?.asString
            // Only an explicit final rejection permits a new request. Timeouts/auth/5xx keep the draft.
            if (code in listOf("INSUFFICIENT_ITEMS", "INVALID_CONSUMPTION", "IDEMPOTENCY_KEY_CONFLICT")) {
                withContext(Dispatchers.IO) {
                    check(prefs.edit().remove(key).commit()) { "조합 결과를 저장하지 못했습니다." }
                }
            }
            error(body?.get("message")?.asString ?: "조합 결과를 확인하지 못했어요. 솥을 눌러 다시 확인해 주세요.")
        }
        val result = response.body()?.data ?: error("조합 결과가 없습니다. 다시 확인해 주세요.")
        withContext(Dispatchers.Main.immediate) {
            checkAccount(userId)
            ItemPrefs.replaceCounts(context, userId, result.items.associate { it.itemId to it.quantity })
            // Durable local save first; reapplying this fox after a crash replaces the same ID.
            FoxPrefs.save(context, pending.fox, durable = true)
        }
        withContext(Dispatchers.IO) {
            check(prefs.edit().remove(key).commit()) { "조합 완료 기록을 저장하지 못했습니다. 다시 확인해 주세요." }
        }
        checkAccount(userId)
        pending.fox
    }

    private fun checkAccount(userId: Int) {
        check(SessionManager.getUserId() == userId) { "로그인 계정이 변경되었습니다." }
    }

    private companion object { val operationMutex = Mutex() }
}
