package com.example.sumdays.alchemy

import android.content.Context
import com.example.sumdays.auth.AccountStorage
import com.example.sumdays.shop.AllItemMap
import com.example.sumdays.shop.FoxShopItem
import com.example.sumdays.shop.ItemCategory
import com.example.sumdays.shop.ItemPrefs

/** Selection is UI state, not inventory. Cancel/rotation never refunds server items. */
object AlchemySelectionManager {
    private const val PREF_NAME = "alchemy_selection"
    private const val KEY_SELECTED_IDS = "selected_item_ids"

    private var owner: String? = null
    private val selected = mutableMapOf<ItemCategory, FoxShopItem>()

    private fun checkOwner() {
        val current = AccountStorage.accountKey()
        if (current != owner) {
            selected.clear()
            owner = current
        }
    }

    /** Drop the previous account's in-memory draft after a session change. */
    fun resetMemory() {
        selected.clear()
        owner = null
    }

    fun toggle(context: Context, item: FoxShopItem): List<FoxShopItem> {
        checkOwner()
        if (selected[item.itemCategory]?.id == item.id) selected.remove(item.itemCategory)
        else if (ItemPrefs.getCount(context, item.id) > 0) selected[item.itemCategory] = item
        saveSelection(context)
        return getSelectedItems()
    }

    /** Restore draft selection after recreation without reserving or decrementing inventory. */
    fun resumePendingSelection(context: Context) {
        checkOwner()
        removeLegacyDraft(context)
        val ids = AccountStorage.preferences(context, PREF_NAME)
            .getStringSet(KEY_SELECTED_IDS, emptySet()).orEmpty()
        selected.clear()
        ids.mapNotNull { it.toIntOrNull()?.let(AllItemMap.allItemMap::get) }
            .forEach { selected[it.itemCategory] = it }
    }

    fun getSelectedItems(): List<FoxShopItem> {
        checkOwner()
        return selected.values.toList()
    }

    fun isSelected(item: FoxShopItem): Boolean {
        checkOwner()
        return selected[item.itemCategory]?.id == item.id
    }

    fun getSelected(category: ItemCategory): FoxShopItem? {
        checkOwner()
        return selected[category]
    }

    fun commit(context: Context) = clear(context)

    fun clear(context: Context) {
        checkOwner()
        selected.clear()
        AccountStorage.preferences(context, PREF_NAME).edit().remove(KEY_SELECTED_IDS).apply()
        removeLegacyDraft(context)
    }

    fun restorePendingSelection(context: Context) {
        // Old builds reserved locally. Never add that amount to authoritative server inventory.
        clear(context)
    }

    private fun saveSelection(context: Context) {
        AccountStorage.preferences(context, PREF_NAME)
            .edit()
            .putStringSet(KEY_SELECTED_IDS, selected.values.map { it.id.toString() }.toSet())
            .apply()
    }

    /** Drafts from builds before per-account files lived in one shared file. */
    private fun removeLegacyDraft(context: Context) {
        val legacy = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (legacy.all.isNotEmpty()) legacy.edit().clear().apply()
    }
}
