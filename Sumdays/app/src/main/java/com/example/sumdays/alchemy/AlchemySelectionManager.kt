package com.example.sumdays.alchemy

import android.content.Context
import com.example.sumdays.shop.FoxShopItem
import com.example.sumdays.shop.ItemCategory
import com.example.sumdays.shop.ItemPrefs

object AlchemySelectionManager {

    private const val PREF_NAME = "alchemy_selection"
    private const val KEY_SELECTED_IDS = "selected_item_ids"

    // 카테고리별 선택된 아이템
    private val selectedMap =
        mutableMapOf<ItemCategory, FoxShopItem>()

    /**
     * 아이템 선택 / 해제
     *
     * 선택하는 순간 인벤토리에서 1개를 예약 차감한다.
     */
    fun toggle(
        context: Context,
        item: FoxShopItem
    ): List<FoxShopItem> {

        val current =
            selectedMap[item.itemCategory]

        // 같은 아이템을 다시 클릭 → 선택 취소
        if (current?.id == item.id) {

            restoreItem(
                context,
                item.id
            )

            selectedMap.remove(
                item.itemCategory
            )

            saveSelection(
                context
            )

            return getSelectedItems()
        }

        // 같은 카테고리에 기존 아이템이 있다면 복원
        if (current != null) {

            restoreItem(
                context,
                current.id
            )

            selectedMap.remove(
                item.itemCategory
            )
        }

        // 현재 재고 확인
        val count =
            ItemPrefs.getCount(
                context,
                item.id
            )

        if (count <= 0) {
            saveSelection(context)
            return getSelectedItems()
        }

        // 새 아이템 1개 예약 차감
        ItemPrefs.setCount(
            context,
            item.id,
            count - 1
        )

        selectedMap[
            item.itemCategory
        ] = item

        saveSelection(context)

        return getSelectedItems()
    }

    /**
     * 현재 선택된 아이템
     */
    fun getSelectedItems(): List<FoxShopItem> {
        return selectedMap.values.toList()
    }

    /**
     * 선택 여부
     */
    fun isSelected(
        item: FoxShopItem
    ): Boolean {

        return selectedMap[
            item.itemCategory
        ]?.id == item.id
    }

    /**
     * 특정 카테고리 선택 아이템
     */
    fun getSelected(
        category: ItemCategory
    ): FoxShopItem? {

        return selectedMap[category]
    }

    /**
     * 조합 성공
     *
     * 이미 선택할 때 차감했으므로
     * 여기서는 예약 정보만 삭제한다.
     */
    fun commit(
        context: Context
    ) {

        selectedMap.clear()

        clearSavedSelection(
            context
        )
    }

    /**
     * 조합 취소 / BottomSheet 닫기
     *
     * 예약 차감했던 아이템을 전부 복구한다.
     */
    fun clear(
        context: Context
    ) {

        selectedMap.values.forEach { item ->

            restoreItem(
                context,
                item.id
            )
        }

        selectedMap.clear()

        clearSavedSelection(
            context
        )
    }

    /**
     * 앱이 다시 시작됐을 때
     *
     * 이전에 예약 차감된 아이템을 복구한다.
     *
     * 강제 종료 대비용.
     */
    fun restorePendingSelection(
        context: Context
    ) {

        val prefs =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        val ids =
            prefs.getStringSet(
                KEY_SELECTED_IDS,
                emptySet()
            ) ?: emptySet()

        ids.forEach { idString ->

            val id =
                idString.toIntOrNull()
                    ?: return@forEach

            restoreItem(
                context,
                id
            )
        }

        clearSavedSelection(
            context
        )

        selectedMap.clear()
    }

    /**
     * 현재 예약 상태 저장
     */
    private fun saveSelection(
        context: Context
    ) {

        val ids =
            selectedMap.values
                .map { it.id.toString() }
                .toSet()

        context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putStringSet(
                KEY_SELECTED_IDS,
                ids
            )
            .apply()
    }

    /**
     * 저장된 예약 상태 삭제
     */
    private fun clearSavedSelection(
        context: Context
    ) {

        context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(
                KEY_SELECTED_IDS
            )
            .apply()
    }

    /**
     * 아이템 1개 복구
     */
    private fun restoreItem(
        context: Context,
        itemId: Int
    ) {

        val count =
            ItemPrefs.getCount(
                context,
                itemId
            )

        ItemPrefs.setCount(
            context,
            itemId,
            count + 1
        )
    }
}