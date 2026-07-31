package com.example.sumdays.alchemy

import android.content.Context
import com.example.sumdays.shop.FoxShopItem
import com.example.sumdays.shop.ItemCategory
import com.example.sumdays.shop.ItemPrefs

object AlchemySelectionManager {

    // 카테고리별 선택된 아이템
    private val selectedMap =
        mutableMapOf<ItemCategory, FoxShopItem>()

    /**
     * 아이템 선택 / 해제
     *
     * - 같은 아이템 다시 클릭 → 선택 해제
     * - 같은 카테고리 다른 아이템 클릭 → 기존 복원 후 교체
     * - 선택 즉시 인벤토리 수량 1 감소
     */
    fun toggle(
        context: Context,
        item: FoxShopItem
    ): List<FoxShopItem> {

        val current = selectedMap[item.itemCategory]

        // 같은 아이템 다시 누름 → 선택 취소
        if (current?.id == item.id) {

            ItemPrefs.setCount(
                context,
                item.id,
                ItemPrefs.getCount(context, item.id) + 1
            )

            selectedMap.remove(item.itemCategory)

            return getSelectedItems()
        }

        // 기존 같은 카테고리 아이템 복원
        if (current != null) {

            ItemPrefs.setCount(
                context,
                current.id,
                ItemPrefs.getCount(context, current.id) + 1
            )
        }

        // 재고 없으면 선택 불가
        val count = ItemPrefs.getCount(context, item.id)

        if (count <= 0) {
            return getSelectedItems()
        }

        // 새 아이템 예약(미리 차감)
        ItemPrefs.setCount(
            context,
            item.id,
            count - 1
        )

        selectedMap[item.itemCategory] = item

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
    fun isSelected(item: FoxShopItem): Boolean {

        return selectedMap[item.itemCategory]?.id == item.id
    }

    /**
     * 특정 카테고리에 선택된 아이템
     */
    fun getSelected(
        category: ItemCategory
    ): FoxShopItem? {

        return selectedMap[category]
    }

    /**
     * 조합 완료
     *
     * 이미 선택하면서 차감했으므로
     * 선택 정보만 제거
     */
    fun commit() {

        selectedMap.clear()
    }

    /**
     * 조합 취소
     *
     * 미리 차감했던 개수를 모두 복원
     */
    fun clear(
        context: Context
    ) {

        selectedMap.values.forEach { item ->

            ItemPrefs.setCount(
                context,
                item.id,
                ItemPrefs.getCount(context, item.id) + 1
            )
        }

        selectedMap.clear()
    }
}