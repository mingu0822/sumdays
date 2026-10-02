package com.example.sumdays.shop

import com.example.sumdays.R
import com.example.sumdays.ShopItem
import com.example.sumdays.daily.memo.MergeTicketProduct

data class MergeTicketShopItem(
    val product: MergeTicketProduct,
    val ownedTicketCount: Int,
    val purchaseEnabled: Boolean = true,
    val disabledActionLabel: String = "이용 중",
) : ShopItem {
    override val id: Int = product.ticketCount
    override val category: String = "merge_ticket"
    override val name: String = "합치기 횟수권 ${product.ticketCount}회"
    override val description: String = "무료 횟수를 모두 사용한 뒤 자동으로 사용돼요"
    override val price: Int = product.pointPrice
    override var isOwned: Boolean = ownedTicketCount > 0
    override val imageRes: Int = R.drawable.ic_shop_point
}
