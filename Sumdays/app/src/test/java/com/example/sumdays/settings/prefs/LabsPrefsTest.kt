package com.example.sumdays.settings.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.sumdays.TestApplication
import com.example.sumdays.data.prefs.devicePrefs.LabsPrefs
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [33],
    application = TestApplication::class
)
class LabsPrefsTest {

    private lateinit var context: Context
    private val prefName = "labs_settings"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<TestApplication>()

        // SharedPreferences 초기화
        context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `기본 temperature는 0_5이다`() {
        val temp = LabsPrefs.getTemperature(context)
        assertEquals(0.5f, temp)
    }

    @Test
    fun `temperature set 후 get 하면 값이 일치한다`() {
        LabsPrefs.setTemperature(context, 0.8f)
        val temp = LabsPrefs.getTemperature(context)
        assertEquals(0.8f, temp)
    }

    @Test
    fun `기본 advancedFlag는 false이다`() {
        val flag = LabsPrefs.getAdvancedFlag(context)
        assertFalse(flag)
    }

    @Test
    fun `advancedFlag set 후 get 하면 값이 일치한다`() {
        LabsPrefs.setAdvancedFlag(context, true)
        val flag = LabsPrefs.getAdvancedFlag(context)
        assertTrue(flag)
    }

    @Test
    fun `SharedPreferences에 temperature가 실제로 저장된다`() {
        LabsPrefs.setTemperature(context, 0.33f)
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        assertEquals(0.33f, prefs.getFloat("temperature_value", -1f))
    }

    @Test
    fun `SharedPreferences에 advancedFlag가 실제로 저장된다`() {
        LabsPrefs.setAdvancedFlag(context, true)
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        assertEquals(true, prefs.getBoolean("advanced_style_flag", false))
    }
}
