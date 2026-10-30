package com.example.sumdays.shop

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sumdays.R
import com.example.sumdays.ShopActivity
import com.example.sumdays.auth.AccountStorage
import com.example.sumdays.auth.SessionManager
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StickerShopDeviceTest {
    @Test fun buyRestoreAndPreviewStickerShop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Use gradle/sticker-shop-device-tests.init.gradle for an isolated app",
            context.packageName.endsWith(".inventorycheck"))
        assertTrue(SessionManager.saveSession(303, "sticker-device-test"))
        AccountStorage.preferences(context, "sticker_inventory").edit().clear().commit()
        PointPrefs.savePoint(context, 1000)
        val sticker = StickerCatalog.items.first()

        ActivityScenario.launch(ShopActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.chipSticker).performClick()
            }
            instrumentation.waitForIdleSync()
            capture(context, "sticker-shop-before")
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rvShopItems)
                assertEquals(StickerCatalog.items.size, list.adapter!!.itemCount)
                val row = list.findViewHolderForAdapterPosition(0)!!.itemView
                assertEquals("구매", row.findViewById<TextView>(R.id.btnShopItemAction).text.toString())
                row.findViewById<View>(R.id.btnShopItemAction).performClick()
                assertEquals(1, StickerPrefs.getCount(context, sticker.id))
                assertEquals(1000 - sticker.price, PointPrefs.getPoint(context))
            }
            scenario.recreate()
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<View>(R.id.chipSticker).isSelected)
                val list = activity.findViewById<RecyclerView>(R.id.rvShopItems)
                val row = list.findViewHolderForAdapterPosition(0)!!.itemView
                assertEquals("보유 1개", row.findViewById<TextView>(R.id.tvPurchasedBadge).text.toString())
                assertEquals("900", activity.findViewById<TextView>(R.id.tvCurrencyValue).text.toString())
            }
            capture(context, "sticker-shop-owned")
            scenario.onActivity { activity ->
                activity.findViewById<RecyclerView>(R.id.rvShopItems)
                    .scrollToPosition(StickerCatalog.items.lastIndex)
            }
            instrumentation.waitForIdleSync()
            capture(context, "sticker-shop-more")
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rvShopItems)
                val lastRow = list.findViewHolderForAdapterPosition(StickerCatalog.items.lastIndex)!!.itemView
                assertEquals(StickerCatalog.items.last().name,
                    lastRow.findViewById<TextView>(R.id.tvShopItemName).text.toString())
            }
        }
    }

    private fun capture(context: Context, name: String) {
        // Let pending list layout, tab animation and the purchase toast settle for visual review.
        SystemClock.sleep(2300)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        val outputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: context.cacheDir
        outputDir.mkdirs()
        File(outputDir, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
