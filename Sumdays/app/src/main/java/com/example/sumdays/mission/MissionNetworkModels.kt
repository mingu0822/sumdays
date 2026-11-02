package com.example.sumdays.mission

data class MissionApiResponse<T>(
    val success: Boolean,
    val code: String,
    val message: String,
    val data: T?,
)

data class MissionDashboardResponse(
    val pointBalance: Int,
    val missions: List<MissionResponse>,
    val adReward: AdRewardResponse,
)

data class MissionResponse(
    val code: String,
    val category: String,
    val title: String,
    val description: String,
    val targetCount: Int,
    val rewardPoints: Int,
    val displayOrder: Int,
    val currentCount: Int,
    val status: String,
)

data class AdRewardResponse(
    val watchedCount: Int,
    val dailyLimit: Int,
    val rewardPoints: Int,
)

data class AdRewardClaimRequest(
    val rewardId: String,
)

data class PointRewardResponse(
    val rewardPoints: Int,
    val pointBalance: Int,
    val watchedCount: Int? = null,
    val dailyLimit: Int? = null,
)

data class WeeklySummaryViewRequest(
    val summaryStartDate: String,
)

data class FriendDiaryViewRequest(
    val friendId: Int,
    val diaryDate: String,
)

data class LegacyPointBalanceRequest(
    val legacyPointBalance: Int,
)

data class PointWalletResponse(
    val pointBalance: Int,
    val importedLegacyBalance: Boolean,
)

data class GooglePlayPointRedeemRequest(
    val productId: String,
    val purchaseToken: String,
)

data class GooglePlayPointRedeemResponse(
    val pointBalance: Int,
    val pointAmount: Int,
    val duplicate: Boolean,
)

data class ShopPurchaseRequest(
    val productType: String,
    val itemId: Int,
    val idempotencyKey: String,
)

data class ShopPurchaseResponse(
    val pointBalance: Int,
    val price: Int,
    val duplicate: Boolean,
    val quantity: Int,
    val owned: Boolean,
    val alreadyOwned: Boolean = false,
)

data class ShopCatalogResponse(
    val pointBalance: Int,
    val products: List<ShopProductResponse>,
)

data class ShopProductResponse(
    val productType: String,
    val itemId: Int,
    val price: Int,
    val quantity: Int,
    val owned: Boolean,
)
