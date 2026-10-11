package com.example.sumdays.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sumdays.FoxAlchemyActivity
import com.example.sumdays.R
import com.example.sumdays.ShopActivity
import com.example.sumdays.ShopAdapter
import com.example.sumdays.alchemy.AlchemyInventoryBottomSheet
import com.example.sumdays.alchemy.AlchemyRecipeManager
import com.example.sumdays.alchemy.AlchemySelectionManager
import com.example.sumdays.customize.AllFoxMap
import com.example.sumdays.customize.FoxBitmapRenderer
import com.example.sumdays.customize.FoxPrefs
import com.example.sumdays.shop.AllItemMap
import com.example.sumdays.shop.ItemPrefs
import com.example.sumdays.shop.OwnedPrefs
import com.example.sumdays.shop.PointPrefs
import com.example.sumdays.theme.ThemePrefs
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AccountInventoryDeviceTest {
    private lateinit var context: Context
    private val hat get() = AllItemMap.allItemMap.getValue(101)

    @Before fun resetTestAccounts() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Never write test accounts into the user's installed app.
        assumeTrue("Use gradle/inventory-device-tests.init.gradle for isolated device tests",
            context.packageName.endsWith(".inventorycheck"))
        for (account in listOf(101, 202, -1)) {
            if (account < 0) SessionManager.clearSession() else login(account)
            AlchemySelectionManager.clear(context)
            ItemPrefs.clear(context)
            OwnedPrefs.clear(context)
            PointPrefs.reset(context)
            FoxPrefs.clear(context)
            ThemePrefs.saveTheme(context, 1)
            ThemePrefs.saveFoxItem(context, 1)
        }
    }

    private fun login(id: Int) {
        assertTrue(SessionManager.saveSession(id, "inventory-device-test-$id"))
        assertEquals(id, SessionManager.getUserId())
        assertEquals("inventory-device-test-$id", SessionManager.getToken())
    }

    @Test fun accountsAndGuestRetainTheirInventoryWithRealKeystoreAndFiles() {
        PointPrefs.savePoint(context, 4321)
        login(101)
        ItemPrefs.setCount(context, hat.id, 3)
        PointPrefs.savePoint(context, 5700)
        OwnedPrefs.saveOwned(context, 2)
        ThemePrefs.saveTheme(context, 2)
        ThemePrefs.saveFoxItem(context, 3)
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val pathA = FoxBitmapRenderer.savePreview(context, 3, bitmap)
        val fox = AlchemyRecipeManager.createFox(3, "Account A", listOf(hat))
        FoxPrefs.save(context, fox.copy(previewPath = pathA))

        login(202)
        assertEquals(9000, PointPrefs.getPoint(context))
        assertEquals(0, ItemPrefs.getCount(context, hat.id))
        assertFalse(OwnedPrefs.isOwned(context, 2))
        assertEquals(setOf(1, 2), AllFoxMap.allFoxMap.keys)
        assertNull(FoxBitmapRenderer.loadPreview(context, pathA))
        bitmap.eraseColor(Color.BLUE)
        val pathB = FoxBitmapRenderer.savePreview(context, 3, bitmap)
        assertNotEquals(pathA, pathB)
        FoxPrefs.save(context, fox.copy(name = "Account B", previewPath = pathB))
        FoxPrefs.delete(context, 3)
        assertFalse(File(pathB).exists())
        assertTrue(File(pathA).exists())

        login(101)
        SessionManager.init(context)
        assertEquals("inventory-device-test-101", SessionManager.getToken())
        FoxPrefs.loadAll(context)
        assertEquals(5700, PointPrefs.getPoint(context))
        assertEquals(3, ItemPrefs.getCount(context, hat.id))
        assertEquals(2, ThemePrefs.getTheme(context))
        assertEquals(3, ThemePrefs.getFoxItem(context))
        assertEquals("Account A", AllFoxMap.allFoxMap.getValue(3).name)
        assertEquals(Color.RED, FoxBitmapRenderer.loadPreview(context, pathA)!!.getPixel(0, 0))
        SessionManager.clearSession()
        assertEquals(4321, PointPrefs.getPoint(context))
        assertEquals(0, ItemPrefs.getCount(context, hat.id))
    }

    @Test fun staleShopButtonCannotChargeTheNextAccount() {
        login(101)
        ActivityScenario.launch(ShopActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.chipItem).performClick()
                val list = activity.findViewById<RecyclerView>(R.id.rvShopItems)
                val adapter = list.adapter as ShopAdapter
                val row = adapter.onCreateViewHolder(list, 0)
                adapter.onBindViewHolder(row, 0)
                row.itemView.findViewById<View>(R.id.btnShopItemAction).performClick()
                assertEquals(1, ItemPrefs.getCount(context, 401))
                assertEquals(9000 - AllItemMap.allItemMap.getValue(401).price, PointPrefs.getPoint(context))

                login(202)
                row.itemView.findViewById<View>(R.id.btnShopItemAction).performClick()
                assertTrue(activity.isFinishing)
                assertEquals(9000, PointPrefs.getPoint(context))
                assertEquals(0, ItemPrefs.getCount(context, 401))
            }
        }
        login(101)
        assertEquals(1, ItemPrefs.getCount(context, 401))
    }

    @Test fun dismissingOldAlchemySheetDoesNotRefundTheNewAccount() {
        login(101)
        ActivityScenario.launch(FoxAlchemyActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ItemPrefs.setCount(context, hat.id, 2)
                val sheet = AlchemyInventoryBottomSheet()
                sheet.showNow(activity.supportFragmentManager, AlchemyInventoryBottomSheet.TAG)
                AlchemySelectionManager.toggle(context, hat)
                login(202)
                ItemPrefs.setCount(context, hat.id, 4)
                AlchemySelectionManager.toggle(context, hat)
                sheet.dismissNow()
                assertEquals(3, ItemPrefs.getCount(context, hat.id))
                assertEquals(listOf(hat.id), AlchemySelectionManager.getSelectedItems().map { it.id })
            }
        }
        login(101)
        AlchemySelectionManager.restorePendingSelection(context)
        AlchemySelectionManager.restorePendingSelection(context)
        assertEquals(2, ItemPrefs.getCount(context, hat.id))
    }
}
