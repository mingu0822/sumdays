package com.example.sumdays.auth

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.CustomizeActivity
import com.example.sumdays.FoxAlchemyActivity
import com.example.sumdays.ShopActivity
import com.example.sumdays.ShopAdapter
import com.example.sumdays.R
import com.example.sumdays.alchemy.AlchemyInventoryBottomSheet
import com.example.sumdays.alchemy.FoxPlacementDialog
import com.example.sumdays.customize.FoxAdapter
import com.example.sumdays.customize.FoxImageStorage
import androidx.test.core.app.ApplicationProvider
import com.example.sumdays.alchemy.AlchemyRecipeManager
import com.example.sumdays.alchemy.AlchemySelectionManager
import com.example.sumdays.customize.AllFoxMap
import com.example.sumdays.customize.FoxBitmapRenderer
import com.example.sumdays.customize.FoxPrefs
import com.example.sumdays.shop.AllItemMap
import com.example.sumdays.shop.AllThemeMap
import com.example.sumdays.shop.ItemPrefs
import com.example.sumdays.shop.OwnedPrefs
import com.example.sumdays.shop.PointPrefs
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.KeyStore
import java.time.Duration
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountInventoryTest {
    private lateinit var context: Context
    private val hat get() = AllItemMap.allItemMap.getValue(101)

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val keyStore = Mockito.mock(KeyStore::class.java)
        Mockito.`when`(keyStore.getKey("SumdaysKeyAlias", null))
            .thenReturn(SecretKeySpec(ByteArray(32) { 7 }, "AES"))
        val prefs = context.getSharedPreferences("sumdays_keystore_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        setSessionField("sharedPreferences", prefs)
        setSessionField("keyStore", keyStore)
        setSessionField("applicationContext", context)
        for (scope in listOf("user_101", "user_202", "guest")) {
            for (name in listOf("owned_items", "item_inventory", "shop_point_prefs",
                "theme_settings", "fox_settings", "fox_prefs", "alchemy_selection")) {
                context.getSharedPreferences("${name}__$scope", Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
        SessionManager.clearSession()
        FoxPrefs.loadAll(context)
        AlchemySelectionManager.resetMemory()
    }

    private fun setSessionField(name: String, value: Any) {
        SessionManager::class.java.getDeclaredField(name).apply { isAccessible = true }
            .set(SessionManager, value)
    }

    private fun login(userId: Int) {
        SessionManager.saveSession(userId, "local-test-token")
        assertEquals(userId, SessionManager.getUserId())
    }

    @Test fun `purchases foxes points and applied choices survive A B A switching without leaking`() {
        login(101)
        OwnedPrefs.saveOwned(context, 2)
        ItemPrefs.setCount(context, hat.id, 3)
        PointPrefs.savePoint(context, 5700)
        ThemePrefs.saveTheme(context, 2)
        val fox = AlchemyRecipeManager.createFox(3, "A's fox", listOf(hat))
        FoxPrefs.save(context, fox)
        ThemePrefs.saveFoxItem(context, 3)
        AccountInventory.refreshOwnership(context)
        assertTrue(ThemeRepository.ownedThemes.containsKey(2))

        login(202)
        assertFalse(OwnedPrefs.isOwned(context, 2))
        assertFalse(AllThemeMap.allThemeMap.getValue(2).isOwned)
        assertFalse(ThemeRepository.ownedThemes.containsKey(2))
        assertEquals(0, ItemPrefs.getCount(context, hat.id))
        assertEquals(9000, PointPrefs.getPoint(context))
        assertEquals(1, ThemePrefs.getTheme(context))
        assertEquals(1, ThemePrefs.getFoxItem(context))
        assertEquals(setOf(1, 2), AllFoxMap.allFoxMap.keys)
        assertFalse(hat.isOwned)
        ItemPrefs.setCount(context, hat.id, 1)
        PointPrefs.savePoint(context, 8600)
        FoxPrefs.save(context, fox.copy(name = "B's fox"))

        login(101)
        assertTrue(OwnedPrefs.isOwned(context, 2))
        assertTrue(ThemeRepository.ownedThemes.containsKey(2))
        assertEquals(3, ItemPrefs.getCount(context, hat.id))
        assertEquals(5700, PointPrefs.getPoint(context))
        assertEquals(2, ThemePrefs.getTheme(context))
        assertEquals(3, ThemePrefs.getFoxItem(context))
        assertEquals("A's fox", AllFoxMap.allFoxMap.getValue(3).name)
        FoxPrefs.loadAll(context)
        assertEquals("A's fox", AllFoxMap.allFoxMap.getValue(3).name)

        SessionManager.clearSession()
        assertEquals(setOf(1, 2), AllFoxMap.allFoxMap.keys)
        assertFalse(OwnedPrefs.isOwned(context, 2))
        assertEquals(0, ItemPrefs.getCount(context, hat.id))
        ItemPrefs.addItem(context, hat.id)
        login(202)
        assertEquals("B's fox", AllFoxMap.allFoxMap.getValue(3).name)
        assertEquals(1, ItemPrefs.getCount(context, hat.id))
        assertEquals(8600, PointPrefs.getPoint(context))
    }

    @Test fun `pending reservations refund only their owner and only once`() {
        login(101)
        ItemPrefs.setCount(context, hat.id, 2)
        AlchemySelectionManager.toggle(context, hat)
        assertEquals(1, ItemPrefs.getCount(context, hat.id))

        login(202)
        assertTrue(AlchemySelectionManager.getSelectedItems().isEmpty())
        AlchemySelectionManager.clear(context)
        AlchemySelectionManager.restorePendingSelection(context)
        assertEquals(0, ItemPrefs.getCount(context, hat.id))

        login(101)
        AlchemySelectionManager.resumePendingSelection(context)
        assertEquals(listOf(hat.id), AlchemySelectionManager.getSelectedItems().map { it.id })
        assertEquals(1, ItemPrefs.getCount(context, hat.id))
        AlchemySelectionManager.restorePendingSelection(context)
        AlchemySelectionManager.restorePendingSelection(context)
        assertEquals(2, ItemPrefs.getCount(context, hat.id))
        assertTrue(AlchemySelectionManager.getSelectedItems().isEmpty())
    }

    @Test fun `same fox ID uses separate image files and deleting B cannot remove A preview`() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        login(101)
        bitmap.eraseColor(Color.RED)
        val pathA = FoxBitmapRenderer.savePreview(context, 3, bitmap)
        val fox = AlchemyRecipeManager.createFox(3, "A", listOf(hat))
        FoxPrefs.save(context, fox.copy(previewPath = pathA))

        login(202)
        assertNull(FoxBitmapRenderer.loadPreview(context, pathA))
        bitmap.eraseColor(Color.BLUE)
        val pathB = FoxBitmapRenderer.savePreview(context, 3, bitmap)
        assertNotEquals(pathA, pathB)
        FoxPrefs.save(context, fox.copy(name = "B", previewPath = pathB))
        assertEquals(Color.BLUE, FoxBitmapRenderer.loadPreview(context, pathB)!!.getPixel(0, 0))
        FoxPrefs.delete(context, 3)
        assertFalse(File(pathB).exists())
        assertTrue(File(pathA).exists())

        login(101)
        assertEquals(Color.RED, FoxBitmapRenderer.loadPreview(context, pathA)!!.getPixel(0, 0))
        assertEquals(pathA, AllFoxMap.allFoxMap.getValue(3).previewPath)
    }

    @Test fun `unowned legacy data and corrupt saves never become the next account's inventory`() {
        context.getSharedPreferences("owned_items", Context.MODE_PRIVATE).edit().putBoolean("2", true).commit()
        context.getSharedPreferences("item_inventory", Context.MODE_PRIVATE).edit().putInt("101", 99).commit()
        login(101)
        assertFalse(OwnedPrefs.isOwned(context, 2))
        assertEquals(0, ItemPrefs.getCount(context, hat.id))
        FoxPrefs.save(context, AlchemyRecipeManager.createFox(3, "A", listOf(hat)))
        context.getSharedPreferences("fox_prefs__user_202", Context.MODE_PRIVATE).edit()
            .putString("foxes", "invalid JSON").commit()
        login(202)
        assertEquals(setOf(1, 2), AllFoxMap.allFoxMap.keys)
        assertTrue(OwnedPrefs.isOwned(context, 1))
    }

    @Test fun `guest inventory returns after login logout and a fresh memory load`() {
        PointPrefs.savePoint(context, 4567)
        ItemPrefs.setCount(context, hat.id, 3)
        FoxPrefs.save(context, AlchemyRecipeManager.createFox(3, "Guest", listOf(hat)))
        ThemePrefs.saveFoxItem(context, 3)
        login(101)
        assertEquals(9000, PointPrefs.getPoint(context))
        assertEquals(0, ItemPrefs.getCount(context, hat.id))
        assertFalse(AllFoxMap.allFoxMap.containsKey(3))

        SessionManager.clearSession()
        AllFoxMap.reset()
        FoxPrefs.loadAll(context)
        assertEquals(4567, PointPrefs.getPoint(context))
        assertEquals(3, ItemPrefs.getCount(context, hat.id))
        assertEquals("Guest", AllFoxMap.allFoxMap.getValue(3).name)
        assertEquals(3, ThemePrefs.getFoxItem(context))
    }

    @Test fun `late inventory dismissal leaves new account reservations intact`() {
        login(101)
        ItemPrefs.setCount(context, hat.id, 2)
        val controller = Robolectric.buildActivity(FoxAlchemyActivity::class.java).setup()
        val activity = controller.get()
        val sheet = AlchemyInventoryBottomSheet()
        sheet.showNow(activity.supportFragmentManager, AlchemyInventoryBottomSheet.TAG)
        AlchemySelectionManager.toggle(context, hat)

        login(202)
        ItemPrefs.setCount(context, hat.id, 4)
        AlchemySelectionManager.toggle(context, hat)
        sheet.dismissNow()
        activity.finish()
        controller.pause().stop().destroy()

        assertEquals(3, ItemPrefs.getCount(context, hat.id))
        assertEquals(listOf(hat.id), AlchemySelectionManager.getSelectedItems().map { it.id })
        login(101)
        AlchemySelectionManager.restorePendingSelection(context)
        assertEquals(2, ItemPrefs.getCount(context, hat.id))
    }

    @Test fun `saved editor from A cannot resume or refund B reservations`() {
        login(101)
        val controller = Robolectric.buildActivity(FoxAlchemyActivity::class.java).setup()
        ItemPrefs.setCount(context, hat.id, 2)
        AlchemySelectionManager.toggle(context, hat)
        val state = Bundle()
        controller.pause().saveInstanceState(state).stop().destroy()

        login(202)
        ItemPrefs.setCount(context, hat.id, 4)
        AlchemySelectionManager.toggle(context, hat)
        val restored = Robolectric.buildActivity(FoxAlchemyActivity::class.java).create(state)
        assertTrue(restored.get().isFinishing)
        restored.destroy()
        assertEquals(3, ItemPrefs.getCount(context, hat.id))
        assertEquals(listOf(hat.id), AlchemySelectionManager.getSelectedItems().map { it.id })
    }

    @Test fun `naming dialog from A cannot save a fox into B`() {
        login(101)
        val controller = Robolectric.buildActivity(FoxAlchemyActivity::class.java).setup().visible()
        val activity = controller.get()
        ItemPrefs.setCount(context, hat.id, 2)
        AlchemySelectionManager.toggle(context, hat)
        val editor = FoxPlacementDialog.newInstance(listOf(hat.id), emptyList())
        editor.showNow(activity.supportFragmentManager, FoxPlacementDialog.TAG)
        editor.requireView().findViewById<View>(R.id.btnPlacementDone).performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue(dialog.isShowing)
        dialog.findViewById<EditText>(R.id.editFoxName).setText("A's unfinished fox")

        login(202)
        ItemPrefs.setCount(context, hat.id, 4)
        AlchemySelectionManager.toggle(context, hat)
        dialog.findViewById<View>(R.id.btnCloseAfterNaming).performClick()
        assertTrue(activity.isFinishing)
        assertEquals(setOf(1, 2), AllFoxMap.allFoxMap.keys)
        assertEquals(3, ItemPrefs.getCount(context, hat.id))
        controller.pause().stop().destroy()
        login(101)
        AlchemySelectionManager.restorePendingSelection(context)
        assertEquals(2, ItemPrefs.getCount(context, hat.id))
    }

    @Test fun `old closet delete confirmation cannot delete same ID in B`() {
        login(101)
        FoxPrefs.save(context, AlchemyRecipeManager.createFox(3, "A", listOf(hat)))
        val controller = Robolectric.buildActivity(CustomizeActivity::class.java).setup()
        val activity = controller.get()
        val list = activity.findViewById<RecyclerView>(R.id.rvFox)
        val adapter = list.adapter as FoxAdapter
        val row = adapter.onCreateViewHolder(list, 0)
        adapter.onBindViewHolder(row, 2)
        row.btnDelete.performClick()
        val dialog = ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog

        login(202)
        FoxPrefs.save(context, AlchemyRecipeManager.createFox(3, "B", listOf(hat)))
        ThemePrefs.saveFoxItem(context, 3)
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.isFinishing)
        assertEquals("B", AllFoxMap.allFoxMap.getValue(3).name)
        assertEquals(3, ThemePrefs.getFoxItem(context))
        controller.pause().stop().destroy()
        login(101)
        assertEquals("A", AllFoxMap.allFoxMap.getValue(3).name)
    }

    @Test fun `old shop action cannot spend the next accounts balance`() {
        login(101)
        val controller = Robolectric.buildActivity(ShopActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<View>(R.id.chipItem).performClick()
        val list = activity.findViewById<RecyclerView>(R.id.rvShopItems)
        val adapter = list.adapter as ShopAdapter
        val row = adapter.onCreateViewHolder(list, 0)
        adapter.onBindViewHolder(row, 0)

        login(202)
        PointPrefs.savePoint(context, 3456)
        row.itemView.findViewById<View>(R.id.btnShopItemAction).performClick()
        assertTrue(activity.isFinishing)
        assertEquals(3456, PointPrefs.getPoint(context))
        assertEquals(0, ItemPrefs.getCount(context, 401))
        controller.pause().stop().destroy()
    }

    @Test fun `returning to shop refreshes balance and stock before the next purchase`() {
        login(101)
        val controller = Robolectric.buildActivity(ShopActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<View>(R.id.chipItem).performClick()
        controller.pause().stop()
        PointPrefs.savePoint(context, 199)
        ItemPrefs.setCount(context, 401, 2)
        controller.restart().start().resume()
        assertEquals("199", activity.findViewById<TextView>(R.id.tvCurrencyValue).text.toString())
        val list = activity.findViewById<RecyclerView>(R.id.rvShopItems)
        val adapter = list.adapter as ShopAdapter
        val row = adapter.onCreateViewHolder(list, 0)
        adapter.onBindViewHolder(row, 0)
        assertEquals("보유 2개", row.itemView.findViewById<TextView>(R.id.tvPurchasedBadge).text.toString())
        row.itemView.findViewById<View>(R.id.btnShopItemAction).performClick()
        assertEquals(199, PointPrefs.getPoint(context))
        assertEquals(2, ItemPrefs.getCount(context, 401))
        activity.finish()
        controller.pause().stop().destroy()
    }

    @Test fun `image helpers reject another account and lookalike directories`() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        login(101)
        val path = FoxImageStorage.save(context, bitmap, 3)
        assertNotNull(FoxImageStorage.load(context, path))
        login(202)
        FoxImageStorage.delete(context, path)
        assertNull(FoxImageStorage.load(context, path))
        assertTrue(File(path).exists())

        val lookalike = File(context.cacheDir, "app_fox_previews__user_202/fox_3.png")
        lookalike.parentFile!!.mkdirs()
        File(path).copyTo(lookalike, overwrite = true)
        assertNull(FoxBitmapRenderer.loadPreview(context, lookalike.path))
        FoxBitmapRenderer.deletePreview(context, lookalike.path)
        assertTrue(lookalike.exists())
    }
}
