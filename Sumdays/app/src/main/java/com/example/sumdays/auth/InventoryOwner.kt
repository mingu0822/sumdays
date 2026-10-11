package com.example.sumdays.auth

import android.app.Activity
import android.os.Bundle

/** Keeps restored screens and delayed dialog callbacks attached to their original account. */
class InventoryOwner(state: Bundle? = null) {
    private val account = state?.getString(STATE_KEY) ?: AccountStorage.accountKey()

    val isCurrent: Boolean
        get() = account == AccountStorage.accountKey()

    fun requireCurrent(activity: Activity): Boolean {
        if (!isCurrent) activity.finish()
        return isCurrent && !activity.isFinishing && !activity.isDestroyed
    }

    fun saveState(state: Bundle) {
        state.putString(STATE_KEY, account)
    }

    private companion object {
        const val STATE_KEY = "inventory_owner"
    }
}
