package com.example.sumdays.shop

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.sumdays.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class PointPrefsTest {

    private lateinit var context: Context
    private lateinit var sessionPrefs: android.content.SharedPreferences
    private lateinit var pointPrefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessionPrefs = context.getSharedPreferences("sumdays_keystore_prefs", Context.MODE_PRIVATE)
        pointPrefs = context.getSharedPreferences("shop_point_prefs", Context.MODE_PRIVATE)
        sessionPrefs.edit().clear().commit()
        pointPrefs.edit().clear().commit()
    }

    @Test
    fun pointsAreIsolatedByUserId() {
        loginAs(1)
        PointPrefs.savePoint(context, 1111)

        loginAs(2)
        assertEquals(9000, PointPrefs.getPoint(context))
        PointPrefs.savePoint(context, 2222)

        loginAs(1)
        assertEquals(1111, PointPrefs.getPoint(context))
        loginAs(2)
        assertEquals(2222, PointPrefs.getPoint(context))
    }

    @Test
    fun legacyPointCanOnlyBeHandledByItsCapturedOwner() {
        pointPrefs.edit().putInt("current_point", 7777).commit()
        loginAs(11)
        PointPrefs.prepareLegacyMigration(context)

        loginAs(22)
        assertEquals(9000, PointPrefs.getLegacyPointForMigration(context))
        PointPrefs.markLegacyPointHandled(context)
        assertTrue(pointPrefs.contains("current_point"))

        loginAs(11)
        assertEquals(7777, PointPrefs.getLegacyPointForMigration(context))
        PointPrefs.markLegacyPointHandled(context)
        assertFalse(pointPrefs.contains("current_point"))
    }

    private fun loginAs(userId: Int) {
        sessionPrefs.edit().putInt("user_id", userId).commit()
    }
}
