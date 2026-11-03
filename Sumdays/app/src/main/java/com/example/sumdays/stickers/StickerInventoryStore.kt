package com.example.sumdays.stickers

import android.content.Context
import com.example.sumdays.auth.AccountStorage
import com.example.sumdays.shop.StickerCatalog
import com.google.gson.Gson
import java.io.IOException
import kotlinx.coroutines.sync.Mutex

/** A reservation and its debit share one durable preferences transaction. Captures the account. */
class StickerInventoryStore(context: Context, account: String = AccountStorage.accountKey()) {
    private val prefs = context.getSharedPreferences("sticker_inventory__$account", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun count(id: Int): Int = synchronized(lock) { prefs.getInt(id.toString(), 0) }

    fun add(id: Int) = synchronized(lock) {
        prefs.edit().putInt(id.toString(), count(id) + 1).apply()
    }

    fun setCount(id: Int, quantity: Int) = synchronized(lock) {
        require(StickerCatalog.contains(id) && quantity >= 0)
        check(pending().isEmpty()) { "이전 스티커 저장을 확인하고 있어요." }
        if (!prefs.edit().putInt(id.toString(), quantity).commit()) {
            throw IOException("스티커 보관함을 저장하지 못했어요.")
        }
    }

    fun replaceCounts(counts: Map<Int, Int>) = synchronized(lock) {
        check(pending().isEmpty()) { "이전 스티커 저장을 확인하고 있어요." }
        require(counts.values.all { it >= 0 })
        val edit = prefs.edit()
        StickerCatalog.items.forEach { edit.putInt(it.id.toString(), counts[it.id] ?: 0) }
        if (!edit.commit()) throw IOException("스티커 보관함을 저장하지 못했어요.")
    }

    fun pending(): List<PendingSticker> = synchronized(lock) {
        prefs.all.filterKeys { it.startsWith(PREFIX) }.values.map {
            gson.fromJson(it as String, PendingSticker::class.java)
        }
    }

    fun reserve(request: PendingSticker): Boolean = synchronized(lock) {
        require(request.geometry.isValid() && StickerCatalog.items.any { it.id == request.stickerId })
        val existing = prefs.getString(PREFIX + request.id, null)
        if (existing != null) {
            require(gson.fromJson(existing, PendingSticker::class.java) == request)
            return@synchronized true
        }
        val available = count(request.stickerId)
        if (available <= 0) return@synchronized false
        if (!prefs.edit().putInt(request.stickerId.toString(), available - 1)
                .putString(PREFIX + request.id, gson.toJson(request)).commit()) {
            throw IOException("스티커 보관함을 저장하지 못했어요.")
        }
        true
    }

    fun finish(id: String, accepted: Boolean) = synchronized(lock) {
        val json = prefs.getString(PREFIX + id, null) ?: return@synchronized
        val request = gson.fromJson(json, PendingSticker::class.java)
        val edit = prefs.edit().remove(PREFIX + id)
        if (!accepted) edit.putInt(request.stickerId.toString(), count(request.stickerId) + 1)
        if (!edit.commit()) throw IOException("스티커 보관함을 저장하지 못했어요.")
    }

    companion object {
        // Serialize network snapshots, purchases and placement reservations across repositories.
        val operationMutex = Mutex()
        private val lock = Any()
        private const val PREFIX = "pending:"
    }
}
