package com.example.sumdays.shop

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.example.sumdays.R
import com.example.sumdays.ShopActivity
import com.example.sumdays.ShopAdapter
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.theme.ThemePrefs
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.security.KeyStore
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class StickerShopTest {
    private lateinit var context: Context
    private val sticker get() = StickerCatalog.items.first()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val keyStore = Mockito.mock(KeyStore::class.java)
        Mockito.`when`(keyStore.getKey("SumdaysKeyAlias", null))
            .thenReturn(SecretKeySpec(ByteArray(32) { 7 }, "AES"))
        val sessionPrefs = context.getSharedPreferences("sumdays_keystore_prefs", Context.MODE_PRIVATE)
        sessionPrefs.edit().clear().commit()
        mapOf("sharedPreferences" to sessionPrefs, "keyStore" to keyStore,
            "applicationContext" to context).forEach { (name, value) ->
            SessionManager::class.java.getDeclaredField(name).apply { isAccessible = true }
                .set(SessionManager, value)
        }
        SessionManager.clearSession()
        for (scope in listOf("guest", "user_101", "user_202")) {
            for (name in listOf("sticker_inventory", "item_inventory", "shop_point_prefs",
                "owned_items", "theme_settings", "fox_settings")) {
                context.getSharedPreferences("${name}__$scope", Context.MODE_PRIVATE)
                    .edit().clear().commit()
            }
        }
    }

    private fun login(id: Int) {
        assertTrue(SessionManager.saveSession(id, "sticker-test-token"))
    }

    private fun row(activity: ShopActivity, index: Int = 0): ShopAdapter.ShopViewHolder {
        val list = activity.findViewById<RecyclerView>(R.id.rvShopItems)
        val adapter = list.adapter as ShopAdapter
        return adapter.onCreateViewHolder(list, 0).also { adapter.onBindViewHolder(it, index) }
    }

    private fun buy(activity: ShopActivity) {
        row(activity).itemView.findViewById<View>(R.id.btnShopItemAction).performClick()
    }

    @Test fun `sticker tab shows only stickers and clears reused theme action state`() {
        val controller = Robolectric.buildActivity(ShopActivity::class.java).setup()
        val activity = controller.get()
        val list = activity.findViewById<RecyclerView>(R.id.rvShopItems)
        val adapter = list.adapter as ShopAdapter
        val reused = row(activity)
        assertFalse(reused.itemView.findViewById<View>(R.id.btnShopItemAction).isEnabled)

        activity.findViewById<View>(R.id.chipSticker).performClick()
        assertEquals(StickerCatalog.items.size, adapter.itemCount)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.shopItemCategoryScroll).visibility)
        assertTrue(activity.findViewById<View>(R.id.chipSticker).isSelected)
        StickerCatalog.items.forEachIndexed { index, product ->
            adapter.onBindViewHolder(reused, index)
            assertEquals(product.name, reused.itemView.findViewById<TextView>(R.id.tvShopItemName).text.toString())
            assertEquals("구매", reused.itemView.findViewById<TextView>(R.id.btnShopItemAction).text.toString())
            assertTrue(reused.itemView.findViewById<View>(R.id.btnShopItemAction).isEnabled)
            assertEquals(View.GONE, reused.itemView.findViewById<View>(R.id.tvPurchasedBadge).visibility)
            assertNotNull(context.getDrawable(product.imageRes))
        }
        activity.findViewById<View>(R.id.chipItem).performClick()
        assertEquals(AllItemMap.allItemMap.size, adapter.itemCount)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.shopItemCategoryScroll).visibility)
        activity.findViewById<View>(R.id.chipTheme).performClick()
        adapter.onBindViewHolder(reused, 0)
        assertFalse(reused.itemView.findViewById<View>(R.id.btnShopItemAction).isEnabled)
        controller.pause().stop().destroy()
    }

    @Test fun `repeat purchases add stock and deduct exact price without applying anything`() {
        login(101)
        PointPrefs.savePoint(context, sticker.price * 2)
        val themeBefore = ThemePrefs.getTheme(context)
        val foxBefore = ThemePrefs.getFoxItem(context)
        val controller = Robolectric.buildActivity(ShopActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<View>(R.id.chipSticker).performClick()
        buy(activity)
        buy(activity)
        assertEquals(0, PointPrefs.getPoint(context))
        assertEquals(2, StickerPrefs.getCount(context, sticker.id))
        assertEquals(0, ItemPrefs.getCount(context, sticker.id))
        assertEquals(themeBefore, ThemePrefs.getTheme(context))
        assertEquals(foxBefore, ThemePrefs.getFoxItem(context))
        assertEquals("보유 2개", row(activity).itemView.findViewById<TextView>(R.id.tvPurchasedBadge).text.toString())
        assertEquals("0", activity.findViewById<TextView>(R.id.tvCurrencyValue).text.toString())
        buy(activity)
        assertEquals(0, PointPrefs.getPoint(context))
        assertEquals(2, StickerPrefs.getCount(context, sticker.id))
        assertEquals("포인트가 부족합니다.", ShadowToast.getTextOfLatestToast())
        controller.pause().stop().destroy()
    }

    @Test fun `purchase checks fresh balance even when displayed balance is older`() {
        val controller = Robolectric.buildActivity(ShopActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<View>(R.id.chipSticker).performClick()
        PointPrefs.savePoint(context, sticker.price - 1)
        buy(activity)
        assertEquals(sticker.price - 1, PointPrefs.getPoint(context))
        assertEquals(0, StickerPrefs.getCount(context, sticker.id))
        controller.pause().stop().destroy()
    }

    @Test fun `stickers remain isolated across accounts and guest and survive reopening`() {
        StickerPrefs.addSticker(context, sticker.id)
        login(101)
        assertEquals(0, StickerPrefs.getCount(context, sticker.id))
        val controller = Robolectric.buildActivity(ShopActivity::class.java).setup()
        controller.get().findViewById<View>(R.id.chipSticker).performClick()
        buy(controller.get())
        buy(controller.get())
        controller.pause().stop().destroy()
        login(202)
        assertEquals(0, StickerPrefs.getCount(context, sticker.id))
        StickerPrefs.addSticker(context, sticker.id)
        login(101)
        assertEquals(2, StickerPrefs.getCount(context, sticker.id))
        assertEquals(9000 - 2 * sticker.price, PointPrefs.getPoint(context))
        val reopened = Robolectric.buildActivity(ShopActivity::class.java).setup()
        reopened.get().findViewById<View>(R.id.chipSticker).performClick()
        assertEquals("보유 2개", row(reopened.get()).itemView.findViewById<TextView>(R.id.tvPurchasedBadge).text.toString())
        reopened.pause().stop().destroy()
        SessionManager.clearSession()
        assertEquals(1, StickerPrefs.getCount(context, sticker.id))
        login(202)
        assertEquals(1, StickerPrefs.getCount(context, sticker.id))
    }

    @Test fun `stale sticker purchase cannot spend another accounts points`() {
        login(101)
        val controller = Robolectric.buildActivity(ShopActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<View>(R.id.chipSticker).performClick()
        val oldRow = row(activity)
        login(202)
        oldRow.itemView.findViewById<View>(R.id.btnShopItemAction).performClick()
        assertTrue(activity.isFinishing)
        assertEquals(9000, PointPrefs.getPoint(context))
        assertEquals(0, StickerPrefs.getCount(context, sticker.id))
        login(101)
        assertEquals(9000, PointPrefs.getPoint(context))
        assertEquals(0, StickerPrefs.getCount(context, sticker.id))
        controller.pause().stop().destroy()
    }

    @Test fun `sticker tab and stock survive recreation and refresh on return`() {
        val controller = Robolectric.buildActivity(ShopActivity::class.java).setup()
        controller.get().findViewById<View>(R.id.chipSticker).performClick()
        buy(controller.get())
        val state = Bundle()
        controller.pause().saveInstanceState(state).stop().destroy()
        val restored = Robolectric.buildActivity(ShopActivity::class.java).setup(state)
        assertTrue(restored.get().findViewById<View>(R.id.chipSticker).isSelected)
        assertEquals("보유 1개", row(restored.get()).itemView.findViewById<TextView>(R.id.tvPurchasedBadge).text.toString())
        restored.pause().stop()
        StickerPrefs.addSticker(context, sticker.id)
        PointPrefs.savePoint(context, 1234)
        restored.restart().start().resume()
        assertEquals("보유 2개", row(restored.get()).itemView.findViewById<TextView>(R.id.tvPurchasedBadge).text.toString())
        assertEquals("1,234", restored.get().findViewById<TextView>(R.id.tvCurrencyValue).text.toString())
        restored.pause().stop().destroy()
    }
}
