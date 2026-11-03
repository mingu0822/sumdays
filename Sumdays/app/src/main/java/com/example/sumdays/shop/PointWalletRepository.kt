package com.example.sumdays.shop

import android.content.Context
import com.example.sumdays.ShopItem
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.auth.AccountStorage
import com.example.sumdays.stickers.StickerInventoryStore
import com.example.sumdays.stickers.RetrofitStickerRemote
import com.example.sumdays.stickers.recoverPendingStickers
import com.example.sumdays.mission.LegacyPointBalanceRequest
import com.example.sumdays.mission.MissionConfig
import com.example.sumdays.mission.ShopCatalogResponse
import com.example.sumdays.mission.ShopPurchaseRequest
import com.example.sumdays.mission.ShopPurchaseResponse
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.ApiService
import kotlinx.coroutines.sync.withLock
import java.util.UUID

sealed interface ShopPurchaseResult {
    data class Purchased(
        val pointBalance: Int,
        val quantity: Int,
        val alreadyOwned: Boolean,
    ) : ShopPurchaseResult
    data class InsufficientPoints(val pointBalance: Int?) : ShopPurchaseResult
}

class PointWalletRepository(context: Context, private val api: ApiService = ApiClient.api) {
    private val appContext = context.applicationContext
    private val operationMutex = StickerInventoryStore.operationMutex

    suspend fun refreshBalance(): Int = operationMutex.withLock {
        if (!MissionConfig.USE_SERVER_MISSIONS) return@withLock PointPrefs.getPoint(appContext)
        val session = requireSession()
        recoverStickerPlacements(session)
        recoverPendingPurchase(session)
        initializeServerWallet(session)
        val response = api.getShopCatalog(session.authorization)
        if (!response.isSuccessful) error("포인트 조회 실패: ${response.code()}")
        val catalog = response.body()?.data ?: error("상점 응답 데이터가 없습니다.")
        ensureCurrent(session)
        applyServerCatalog(session.userId, catalog)
        catalog.pointBalance
    }

    suspend fun refreshShopCatalog(): ShopCatalogResponse? = operationMutex.withLock {
        if (!MissionConfig.USE_SERVER_MISSIONS) return@withLock null
        val session = requireSession()
        recoverStickerPlacements(session)
        recoverPendingPurchase(session)
        initializeServerWallet(session)
        val response = api.getShopCatalog(session.authorization)
        if (!response.isSuccessful) error("상점 조회 실패: ${response.code()}")
        val catalog = response.body()?.data ?: error("상점 응답 데이터가 없습니다.")
        ensureCurrent(session)
        applyServerCatalog(session.userId, catalog)
        catalog
    }

    suspend fun purchase(item: ShopItem): ShopPurchaseResult = operationMutex.withLock {
        if (!MissionConfig.USE_SERVER_MISSIONS) {
            val current = PointPrefs.getPoint(appContext)
            if (current < item.price) {
                return@withLock ShopPurchaseResult.InsufficientPoints(current)
            }
            val balance = current - item.price
            PointPrefs.savePoint(appContext, balance)
            val quantity = if (item is StickerShopItem) {
                StickerPrefs.addSticker(appContext, item.id)
                StickerPrefs.getCount(appContext, item.id)
            } else if (item is FoxShopItem) {
                val nextCount = ItemPrefs.getCount(appContext, item.id) + 1
                ItemPrefs.setCount(appContext, item.id, nextCount)
                nextCount
            } else {
                OwnedPrefs.saveOwned(appContext, item.id)
                1
            }
            return@withLock ShopPurchaseResult.Purchased(
                pointBalance = balance,
                quantity = quantity,
                alreadyOwned = false,
            )
        }

        val session = requireSession()
        recoverStickerPlacements(session)
        initializeServerWallet(session)
        val productType = if (item is ThemeShopItem) "THEME" else "ITEM"
        val previousPending = PendingShopPurchaseStore.get(appContext, session.userId)
        if (previousPending != null) {
            val recovered = submitPurchase(session, previousPending)
            if (
                previousPending.productType == productType &&
                previousPending.itemId == item.id
            ) {
                return@withLock recovered
            }
        }

        val pending = PendingShopPurchase(
            productType = productType,
            itemId = item.id,
            idempotencyKey = UUID.randomUUID().toString(),
        )
        PendingShopPurchaseStore.save(appContext, session.userId, pending)
        submitPurchase(session, pending)
    }

    private suspend fun submitPurchase(
        session: AuthSession,
        pending: PendingShopPurchase,
    ): ShopPurchaseResult {
        val response = api.purchaseShopProduct(
            session.authorization,
            ShopPurchaseRequest(
                productType = pending.productType,
                itemId = pending.itemId,
                idempotencyKey = pending.idempotencyKey,
            )
        )
        ensureCurrent(session)
        if (response.code() == 409) {
            PendingShopPurchaseStore.clear(appContext, session.userId, pending)
            return ShopPurchaseResult.InsufficientPoints(null)
        }
        if (!response.isSuccessful) {
            if (response.code() in 400..499 && response.code() != 408 && response.code() != 429) {
                PendingShopPurchaseStore.clear(appContext, session.userId, pending)
            }
            error("상품 구매 실패: ${response.code()}")
        }
        val purchase = response.body()?.data ?: error("상품 구매 응답 데이터가 없습니다.")
        applyPurchase(session.userId, pending, purchase)
        PendingShopPurchaseStore.clear(appContext, session.userId, pending)
        return ShopPurchaseResult.Purchased(
            pointBalance = purchase.pointBalance,
            quantity = purchase.quantity,
            alreadyOwned = purchase.alreadyOwned,
        )
    }

    private suspend fun recoverPendingPurchase(session: AuthSession) {
        val pending = PendingShopPurchaseStore.get(appContext, session.userId) ?: return
        submitPurchase(session, pending)
    }

    private fun applyPurchase(
        userId: Int,
        pending: PendingShopPurchase,
        purchase: ShopPurchaseResponse,
    ) {
        PointPrefs.savePoint(appContext, userId, purchase.pointBalance)
        if (pending.productType == "THEME") {
            if (purchase.owned) OwnedPrefs.saveOwned(appContext, userId, pending.itemId)
        } else if (StickerCatalog.contains(pending.itemId)) {
            StickerInventoryStore(appContext, AccountStorage.accountKey(userId))
                .setCount(pending.itemId, purchase.quantity)
        } else {
            ItemPrefs.setCount(appContext, userId, pending.itemId, purchase.quantity)
        }
    }

    private fun applyServerCatalog(userId: Int, catalog: ShopCatalogResponse) {
        PointPrefs.savePoint(appContext, userId, catalog.pointBalance)
        OwnedPrefs.replaceOwned(
            appContext,
            userId,
            catalog.products
                .filter { it.productType == "THEME" && it.owned }
                .mapTo(mutableSetOf()) { it.itemId },
        )
        ItemPrefs.replaceCounts(
            appContext,
            userId,
            catalog.products
                .filter { it.productType == "ITEM" && !StickerCatalog.contains(it.itemId) }
                .associate { it.itemId to it.quantity },
        )
        StickerInventoryStore(appContext, AccountStorage.accountKey(userId)).replaceCounts(
            catalog.products.filter { it.productType == "ITEM" && StickerCatalog.contains(it.itemId) }
                .associate { it.itemId to it.quantity },
        )
    }

    private suspend fun recoverStickerPlacements(session: AuthSession) {
        recoverPendingStickers(
            StickerInventoryStore(appContext, AccountStorage.accountKey(session.userId)),
            RetrofitStickerRemote(session.authorization.removePrefix("Bearer ")),
        ) { SessionManager.getUserId() == session.userId }
        ensureCurrent(session)
    }

    private suspend fun initializeServerWallet(session: AuthSession): Int {
        val response = api.initializePointWallet(
            session.authorization,
            LegacyPointBalanceRequest(
                PointPrefs.getLegacyPointForMigration(appContext, session.userId)
            ),
        )
        if (!response.isSuccessful) error("포인트 지갑 준비 실패: ${response.code()}")
        val balance = response.body()?.data?.pointBalance
            ?: error("포인트 지갑 응답 데이터가 없습니다.")
        ensureCurrent(session)
        PointPrefs.savePoint(appContext, session.userId, balance)
        PointPrefs.markLegacyPointHandled(appContext, session.userId)
        return balance
    }

    private fun requireSession(): AuthSession {
        val userId = SessionManager.getUserId()
        val token = SessionManager.getToken()
        if (userId < 0 || token == null) error("로그인이 필요합니다.")
        return AuthSession(userId, "Bearer $token")
    }

    private fun ensureCurrent(session: AuthSession) {
        if (SessionManager.getUserId() != session.userId) {
            error("요청 처리 중 로그인 계정이 변경되었습니다.")
        }
    }

    private data class AuthSession(
        val userId: Int,
        val authorization: String,
    )
}
