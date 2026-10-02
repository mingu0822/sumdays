package com.example.sumdays.alchemy

import android.content.Context
import com.example.sumdays.auth.SessionManager
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
        return getSelectedItems()
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
    }

    fun restorePendingSelection(context: Context) {
        // Old builds reserved locally. Never add that amount to authoritative server inventory.
        context.getSharedPreferences("alchemy_selection", Context.MODE_PRIVATE)
            .edit().remove("selected_item_ids").apply()
        clear(context)
    }
}
