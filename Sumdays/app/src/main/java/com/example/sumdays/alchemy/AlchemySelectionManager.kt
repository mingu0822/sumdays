package com.example.sumdays.alchemy

import android.content.Context
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.shop.AllItemMap
import com.example.sumdays.shop.FoxShopItem
import com.example.sumdays.shop.ItemCategory
import com.example.sumdays.shop.ItemPrefs

/** Selection is UI state, not inventory. Cancel/rotation never refunds server items. */
object AlchemySelectionManager {
    private var owner = Int.MIN_VALUE
    private val selected = mutableMapOf<ItemCategory, FoxShopItem>()

    private fun checkOwner() {
        val current = SessionManager.getUserId()
        if (current != owner) {
            selected.clear()
            owner = current
        }
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
        val prefs = context.getSharedPreferences("alchemy_selection", Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(selectionKey(owner), emptySet()).orEmpty()
        // A previous build used one unscoped key, so it cannot be safely assigned to an account.
        prefs.edit().remove("selected_item_ids").apply()
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

    @Suppress("UNUSED_PARAMETER")
    fun clear(context: Context) {
        checkOwner()
        selected.clear()
        context.getSharedPreferences("alchemy_selection", Context.MODE_PRIVATE)
            .edit()
            .remove(selectionKey(owner))
            .remove("selected_item_ids") // Remove the pre-account-scoping draft key.
            .apply()
    }

    fun restorePendingSelection(context: Context) {
        // Old builds reserved locally. Never add that amount to authoritative server inventory.
        context.getSharedPreferences("alchemy_selection", Context.MODE_PRIVATE)
            .edit()
            .remove(selectionKey(SessionManager.getUserId()))
            .remove("selected_item_ids")
            .apply()
        clear(context)
    }

    private fun saveSelection(context: Context) {
        context.getSharedPreferences("alchemy_selection", Context.MODE_PRIVATE)
            .edit().putStringSet(selectionKey(owner), selected.values.map { it.id.toString() }.toSet()).apply()
    }

    private fun selectionKey(userId: Int): String = "user_${userId}_selected_item_ids"
}
