package com.example.sumdays.settings

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.sumdays.R
import com.example.sumdays.databinding.ActivityProfileLabsBinding
import com.example.sumdays.settings.prefs.LabsPrefs
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
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

    private fun applyThemeModeSettings() {
        val themeKey = ThemePrefs.getTheme(this)
        val currentTheme = ThemeRepository.ownedThemes[themeKey] ?: return

        val primaryColor = currentTheme.textPrimaryColor
        val buttonColor = currentTheme.buttonColor
        val backgroundColor = currentTheme.backgroundColor
        val blockColor = currentTheme.blockColor

        // 전체 배경
        binding.root.setBackgroundResource(backgroundColor)

        // 헤더
        binding.header.headerTitle.setTextColor(getColor(primaryColor))
        binding.header.headerBackIcon.setColorFilter(getColor(primaryColor))

        // 텍스트
        binding.descText.setTextColor(getColor(primaryColor))
        binding.lengthLevelExampleText.setTextColor(getColor(primaryColor))

        // 슬라이더
        binding.lengthLevelSlider.trackActiveTintList =
            android.content.res.ColorStateList.valueOf(getColor(buttonColor))
        binding.lengthLevelSlider.trackInactiveTintList =
            android.content.res.ColorStateList.valueOf(getColor(blockColor))
        binding.lengthLevelSlider.thumbTintList =
            android.content.res.ColorStateList.valueOf(getColor(primaryColor))
        binding.lengthLevelSlider.haloTintList =
            android.content.res.ColorStateList.valueOf(getColor(buttonColor))

        // 카드뷰들 배경색 변경
        val scrollChild = (binding.root.getChildAt(1) as? android.widget.ScrollView)?.getChildAt(0)
                as? android.widget.LinearLayout

        scrollChild?.let { container ->
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child is androidx.cardview.widget.CardView) {
                    child.setCardBackgroundColor(getColor(blockColor))
                }
            }
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