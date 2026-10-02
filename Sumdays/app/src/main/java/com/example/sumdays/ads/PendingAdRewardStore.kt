package com.example.sumdays.ads

import android.content.Context
import com.example.sumdays.auth.SessionManager

object PendingAdRewardStore {
    private const val PREF_NAME = "pending_ad_reward"

    fun save(context: Context, rewardId: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(), rewardId)
            .apply()
    }

    fun get(context: Context): String? =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(key(), null)

    fun clear(context: Context, rewardId: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(key(), null) == rewardId) {
            prefs.edit().remove(key()).apply()
        }
    }

    private fun key(): String = "user_${SessionManager.getUserId()}"
}
