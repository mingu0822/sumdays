package com.example.sumdays.settings

import android.content.Context
import android.os.Build
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.sumdays.R
import com.example.sumdays.TestApplication
import com.example.sumdays.settings.prefs.LabsPrefs
import com.google.android.material.slider.Slider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE],
    application = TestApplication::class,
    manifest = Config.NONE
)
class LabsSettingsActivityTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("labs_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun activity_loads() {
        val act = Robolectric.buildActivity(LabsSettingsActivity::class.java)
            .setup().get()

        val headerText = act.findViewById<TextView>(R.id.headerTitle)
        assertEquals("Labs", headerText.text.toString())
    }

    @Test
    fun slider_loadsSavedTemperature() {
        LabsPrefs.setTemperature(context, 0.5f)

        val act = Robolectric.buildActivity(LabsSettingsActivity::class.java)
            .setup().get()

        val slider = act.findViewById<Slider>(R.id.length_level_slider)

        assertEquals(0.5f, slider.value)
    }

    @Test
    fun slider_updatesPrefsAndText() {
        val activity = Robolectric.buildActivity(LabsSettingsActivity::class.java)
            .setup().get()

        val slider = activity.findViewById<Slider>(R.id.length_level_slider)
        val example = activity.findViewById<TextView>(R.id.length_level_example_text)

        slider.value = 0.2f

        assertEquals(0.2f, LabsPrefs.getTemperature(context))
        assertTrue(example.text.contains("0.2"))
    }

    @Test
    fun toggle_loadsSavedFlag() {
        LabsPrefs.setAdvancedFlag(context, true)

        val act = Robolectric.buildActivity(LabsSettingsActivity::class.java)
            .setup().get()

        val switch = act.findViewById<CompoundButton>(R.id.accurate_style_switch)

        assertTrue(switch.isChecked)
    }

    @Test
    fun toggle_savesFlag() {
        val activity = Robolectric.buildActivity(LabsSettingsActivity::class.java)
            .setup().get()
        val switch = activity.findViewById<CompoundButton>(R.id.accurate_style_switch)

        switch.isChecked = true

        assertTrue(LabsPrefs.getAdvancedFlag(context))
    }

    @Test
    fun header_back_finishes() {
        val activity = Robolectric.buildActivity(LabsSettingsActivity::class.java)
            .setup().get()

        activity.findViewById<View>(R.id.headerBackIcon).performClick()

        assertTrue(activity.isFinishing)
    }

    @Test
    fun toggle_savesFlag_andDoesNotCrashWhenApplyingColors() {
        val act = Robolectric.buildActivity(LabsSettingsActivity::class.java)
            .setup().get()

        val switch = act.findViewById<CompoundButton>(R.id.accurate_style_switch)

        switch.isChecked = true

        assertTrue(LabsPrefs.getAdvancedFlag(context))
    }
}
