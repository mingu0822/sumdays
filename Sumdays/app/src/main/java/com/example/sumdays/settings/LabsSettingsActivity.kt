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
        setupLengthLevelSlider()
        // setupAdvancedToggle()

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
    private fun setupLengthLevelSlider() {
        val savedLevel = LabsPrefs.getLengthLevel(this)
        binding.lengthLevelSlider.value = savedLevel.toFloat()

        binding.lengthLevelExampleText.text = getExampleDiary(savedLevel)

        binding.lengthLevelSlider.addOnChangeListener { _, value, _ ->
            LabsPrefs.setLengthLevel(this, value.toInt())
            binding.lengthLevelExampleText.text = getExampleDiary(value.toInt())
        }
    }

    private fun getExampleDiary(level: Int): String {
        return when (level) {
            0 -> "메모 내용을 중심으로 핵심만 짧고 간결하게 담아요.\n(간결한 기록)"
            1 -> "메모들을 자연스럽게 이어 일상적인 일기 분량으로 써요.\n(적당한 기록)"
            2 -> "메모 사이를 풍성한 문장으로 채워 길고 자세하게 기록해요.\n(상세한 기록)"
            else -> "오늘의 일상을 자유롭게 기록합니다."
        }
    }

    // ========= ADVANCED STYLE TOGGLE =========
//    private fun setupAdvancedToggle() {
//        val savedFlag = LabsPrefs.getAdvancedFlag(this)
//        binding.accurateStyleSwitch.isChecked = savedFlag
//
//        applySwitchColors(savedFlag)
//
//        binding.accurateStyleSwitch.setOnCheckedChangeListener { _, checked ->
//            LabsPrefs.setAdvancedFlag(this, checked)
//            applySwitchColors(checked)
//        }
//    }
//
//    private fun applySwitchColors(isChecked: Boolean) {
//        val thumbColors = createColorStateList(thumbOn, thumbOff)
//        val trackColors = createColorStateList(trackOn, trackOff)
//
//        binding.accurateStyleSwitch.thumbTintList = thumbColors
//        binding.accurateStyleSwitch.trackTintList = trackColors
//    }
//
//    private fun createColorStateList(onColor: Int, offColor: Int) =
//        android.content.res.ColorStateList(
//            arrayOf(
//                intArrayOf(android.R.attr.state_checked),
//                intArrayOf()
//            ),
//            intArrayOf(onColor, offColor)
//        )
}