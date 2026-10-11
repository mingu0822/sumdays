package com.example.sumdays.auth

import android.content.Context
import com.example.sumdays.alchemy.AlchemySelectionManager
import com.example.sumdays.customize.FoxPrefs
import com.example.sumdays.shop.AllItemMap
import com.example.sumdays.shop.AllThemeMap
import com.example.sumdays.shop.ItemPrefs
import com.example.sumdays.shop.OwnedPrefs
import com.example.sumdays.theme.FoxRepository
import com.example.sumdays.theme.ThemeRepository

/** Replaces the previous user's in-memory inventory after a session change. */
object AccountInventory {
    private var loadedAccount: String? = null

    fun onSessionChanged(context: Context) {
        val account = AccountStorage.accountKey()
        if (loadedAccount == account) return
        loadedAccount = account
        AlchemySelectionManager.resetMemory()
        refreshOwnership(context)
        FoxPrefs.loadAll(context)
    }

    fun refreshOwnership(context: Context) {
        AllThemeMap.allThemeMap.forEach { (id, theme) ->
            theme.isOwned = OwnedPrefs.isOwned(context, id)
        }
        AllItemMap.allItemMap.forEach { (id, item) ->
            item.count = ItemPrefs.getCount(context, id)
            item.isOwned = item.count > 0
        }
        ThemeRepository.updateOwned()
        FoxRepository.updateOwned()
    }
}
