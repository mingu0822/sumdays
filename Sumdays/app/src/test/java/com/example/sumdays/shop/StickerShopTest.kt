package com.example.sumdays.shop

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.sumdays.R
import com.example.sumdays.ShopAdapter
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.mission.*
import com.example.sumdays.network.apiService.ApiService
import com.example.sumdays.stickers.StickerInventoryStore
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException
import java.security.KeyStore
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class StickerShopTest {
    private lateinit var context: Context
    private lateinit var api: ApiService
    private lateinit var repository: PointWalletRepository
    private val sticker get() = StickerCatalog.items.first()
    private fun <T> ok(data: T): Response<MissionApiResponse<T>> =
        Response.success(MissionApiResponse(true, "OK", "", data))

    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        val keyStore = Mockito.mock(KeyStore::class.java)
        whenever(keyStore.getKey("SumdaysKeyAlias", null)).thenReturn(SecretKeySpec(ByteArray(32) { 7 }, "AES"))
        val prefs = context.getSharedPreferences("sumdays_keystore_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        mapOf("sharedPreferences" to prefs, "keyStore" to keyStore, "applicationContext" to context).forEach { (name, value) ->
            SessionManager::class.java.getDeclaredField(name).apply { isAccessible = true }.set(SessionManager, value)
        }
        for (account in listOf("guest", "user_101", "user_202")) {
            for (name in listOf("sticker_inventory", "item_inventory", "shop_point_prefs", "owned_items")) {
                context.getSharedPreferences("${name}__$account", Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
        context.getSharedPreferences("pending_shop_purchase", Context.MODE_PRIVATE).edit().clear().commit()
        assertTrue(SessionManager.saveSession(101, "test-token"))
        api = Mockito.mock(ApiService::class.java)
        whenever(api.initializePointWallet(any(), any())).thenReturn(ok(PointWalletResponse(9000, false)))
        repository = PointWalletRepository(context, api)
    }

    @Test fun `sticker button enables only after server product loads and displays owned count`() {
        val products = mutableListOf(sticker)
        var clicked = 0
        val adapter = ShopAdapter(products, {}, { clicked++ })
        context.setTheme(R.style.Theme_First)
        val parent = FrameLayout(context)
        val row = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(row, 0)
        val button = row.itemView.findViewById<TextView>(R.id.btnShopItemAction)
        assertFalse(button.isEnabled)
        StickerInventoryStore(context).setCount(sticker.id, 2)
        products[0] = sticker.copy(purchaseEnabled = true)
        adapter.onBindViewHolder(row, 0)
        assertTrue(button.isEnabled)
        assertEquals("구매", button.text.toString())
        assertEquals("보유 2개", row.itemView.findViewById<TextView>(R.id.tvPurchasedBadge).text.toString())
        button.performClick()
        assertEquals(1, clicked)
    }

    @Test fun `server sticker purchase updates stock and points without populating fox inventory`() = runBlocking {
        whenever(api.purchaseShopProduct(any(), any())).thenReturn(ok(ShopPurchaseResponse(8900, 100, false, 1, false)))
        val result = repository.purchase(sticker) as ShopPurchaseResult.Purchased
        assertEquals(8900, result.pointBalance)
        assertEquals(8900, PointPrefs.getPoint(context))
        assertEquals(1, StickerPrefs.getCount(context, sticker.id))
        assertEquals(0, ItemPrefs.getCount(context, sticker.id))
        val request = argumentCaptor<ShopPurchaseRequest>()
        verify(api).purchaseShopProduct(any(), request.capture())
        assertEquals("ITEM", request.firstValue.productType)
        assertEquals(sticker.id, request.firstValue.itemId)
    }

    @Test fun `lost purchase response retries same request without adding local stock twice`() = runBlocking {
        whenever(api.purchaseShopProduct(any(), any())).thenAnswer { throw IOException("Lost response") }
            .thenReturn(ok(ShopPurchaseResponse(8800, 100, true, 2, false)))
        try { repository.purchase(sticker); fail() } catch (_: IOException) {}
        assertNotNull(PendingShopPurchaseStore.get(context))
        repository.purchase(sticker)
        val request = argumentCaptor<ShopPurchaseRequest>()
        verify(api, times(2)).purchaseShopProduct(any(), request.capture())
        assertEquals(request.allValues[0].idempotencyKey, request.allValues[1].idempotencyKey)
        assertEquals(2, StickerPrefs.getCount(context, sticker.id))
        assertNull(PendingShopPurchaseStore.get(context))
    }

    @Test fun `insufficient points leaves sticker stock unchanged`() = runBlocking {
        StickerInventoryStore(context).setCount(sticker.id, 1)
        whenever(api.purchaseShopProduct(any(), any())).thenReturn(Response.error(409, "{}".toResponseBody()))
        assertTrue(repository.purchase(sticker) is ShopPurchaseResult.InsufficientPoints)
        assertEquals(1, StickerPrefs.getCount(context, sticker.id))
        assertNull(PendingShopPurchaseStore.get(context))
    }

    @Test fun `catalog restores server quantities separately for stickers and fox items`() = runBlocking {
        StickerInventoryStore(context).setCount(1002, 8)
        whenever(api.getShopCatalog(any())).thenReturn(ok(ShopCatalogResponse(7000, listOf(
            ShopProductResponse("ITEM", sticker.id, 100, 3, false),
            ShopProductResponse("ITEM", 1, 300, 2, false),
        ))))
        repository.refreshShopCatalog()
        assertEquals(7000, PointPrefs.getPoint(context))
        assertEquals(3, StickerPrefs.getCount(context, sticker.id))
        assertEquals(0, StickerPrefs.getCount(context, 1002))
        assertEquals(2, ItemPrefs.getCount(context, 1))
        assertEquals(0, ItemPrefs.getCount(context, sticker.id))
        assertTrue(SessionManager.saveSession(202, "other-token"))
        assertEquals(0, StickerPrefs.getCount(context, sticker.id))
        assertTrue(SessionManager.saveSession(101, "test-token"))
        assertEquals(3, StickerPrefs.getCount(context, sticker.id))
    }
}
