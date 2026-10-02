package com.example.sumdays.shop

import com.example.sumdays.R
import com.example.sumdays.ShopItem

data class MergePassShopItem(
    val active: Boolean,
    val expiresAt: String?,
) : ShopItem {
    override val id: Int = -10
    override val category: String = "merge_ticket"
    override val name: String = "합치기 무제한 패스"
    override val description: String = if (active) {
        "만료 전까지 합치기를 무제한으로 이용할 수 있어요"
    } else {
        "자동 갱신 없이 1개월 동안 합치기를 무제한으로 이용해요"
    }
    override val price: Int = 3000
    override var isOwned: Boolean = active
    override val imageRes: Int = R.drawable.ic_shop_point
}
