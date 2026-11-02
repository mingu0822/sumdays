package com.example.sumdays.shop

import android.content.Context
import com.example.sumdays.auth.SessionManager

data class PendingShopPurchase(
    val productType: String,
    val itemId: Int,
    val idempotencyKey: String,
)

object PendingShopPurchaseStore {
    private const val PREF_NAME = "pending_shop_purchase"

    fun save(context: Context, purchase: PendingShopPurchase) {
        save(context, SessionManager.getUserId(), purchase)
    }

    fun save(context: Context, userId: Int, purchase: PendingShopPurchase) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(userId), encode(purchase))
            .commit()
    }

    fun get(context: Context): PendingShopPurchase? {
        return get(context, SessionManager.getUserId())
    }

    fun get(context: Context, userId: Int): PendingShopPurchase? {
        val encoded = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(key(userId), null)
            ?: return null
        val parts = encoded.split(':', limit = 3)
        if (parts.size != 3) return null
        val itemId = parts[1].toIntOrNull() ?: return null
        return PendingShopPurchase(parts[0], itemId, parts[2])
    }

    fun clear(context: Context, purchase: PendingShopPurchase) {
        clear(context, SessionManager.getUserId(), purchase)
    }

    fun clear(context: Context, userId: Int, purchase: PendingShopPurchase) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(key(userId), null) == encode(purchase)) {
            prefs.edit().remove(key(userId)).apply()
        }
    }

    private fun key(userId: Int): String = "user_$userId"

    private fun encode(purchase: PendingShopPurchase): String =
        "${purchase.productType}:${purchase.itemId}:${purchase.idempotencyKey}"
}
