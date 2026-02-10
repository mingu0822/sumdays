package com.example.sumdays.settings

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.sumdays.R
import com.example.sumdays.databinding.ActivityProfileLabsBinding
import com.example.sumdays.settings.prefs.LabsPrefs
import com.example.sumdays.settings.prefs.ThemeState
import com.example.sumdays.utils.setupEdgeToEdge

class LabsSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileLabsBinding

    // 색상 설정
    private val thumbOn = Color.parseColor("#FFFFFF")
    private val thumbOff = Color.parseColor("#FFFFFF")

    private val trackOn = Color.parseColor("#C62FE0")
    private val trackOff = Color.parseColor("#33777777")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileLabsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeader()
        setupTemperatureSlider()
        setupAdvancedToggle()

        applyThemeModeSettings()

        // 상태바, 네비게이션바 같은 색으로
        val rootView = findViewById<View>(R.id.setting_labs_root)
        setupEdgeToEdge(rootView)
    }

    private fun applyThemeModeSettings(){
        // Apply dark mode
        ThemeState.isDarkMode = (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)

        if (ThemeState.isDarkMode){
            binding.header.headerBackIcon.setImageResource(R.drawable.ic_arrow_back_white)
        }
        else{
            binding.header.headerBackIcon.setImageResource(R.drawable.ic_arrow_back_black)
        }
    }

    private fun setupHeader() {
        binding.header.headerTitle.text = "Labs"
        binding.header.headerBackIcon.setOnClickListener { finish() }
    }

    // ========= TEMPERATURE SLIDER =========
    private fun setupTemperatureSlider() {
        val savedTemp = LabsPrefs.getTemperature(this)
        binding.temperatureSlider.value = savedTemp

        binding.temperatureExampleText.text = getExampleDiary(savedTemp)

        binding.temperatureSlider.addOnChangeListener { _, value, _ ->
            LabsPrefs.setTemperature(this, value)
            binding.temperatureExampleText.text = getExampleDiary(value)
        }
    }

    private fun getExampleDiary(temp: Float): String {
        return when (temp) {
            in 0.2f..0.3f -> "차분하고 정돈된 일기 예시입니다.\n(Temperature: 0.2~0.3)"
            in 0.3f..0.5f -> "기본적이고 자연스러운 흐름의 일기입니다.\n(Temperature: 0.3~0.5)"
            in 0.5f..0.7f -> "조금 더 풍부한 묘사가 포함된 일기입니다.\n(Temperature: 0.5~0.7)"
            else -> "감정과 창의성이 자유롭게 표현된 일기입니다.\n(Temperature: 0.7~0.9)"
        }
    }

    // ========= ADVANCED STYLE TOGGLE =========
    private fun setupAdvancedToggle() {
        val savedFlag = LabsPrefs.getAdvancedFlag(this)
        binding.accurateStyleSwitch.isChecked = savedFlag

        applySwitchColors(savedFlag)

        binding.accurateStyleSwitch.setOnCheckedChangeListener { _, checked ->
            LabsPrefs.setAdvancedFlag(this, checked)
            applySwitchColors(checked)
        }
    }

    private fun applySwitchColors(isChecked: Boolean) {
        val thumbColors = createColorStateList(thumbOn, thumbOff)
        val trackColors = createColorStateList(trackOn, trackOff)

        binding.accurateStyleSwitch.thumbTintList = thumbColors
        binding.accurateStyleSwitch.trackTintList = trackColors
    }

    private fun createColorStateList(onColor: Int, offColor: Int) =
        android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(onColor, offColor)
        )
}