package com.example.sumdays

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.sumdays.databinding.ActivitySettingMainBinding
import com.example.sumdays.settings.NotificationSettingsActivity
import com.example.sumdays.statistics.WeekSummaryWorker
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.utils.setupEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.settings.ThemeSettingsActivity
import com.example.sumdays.settings.prefs.ThemeState

class SettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingMainBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSettingsBtnListener()
        applyThemeModeSettings()
    }

    private fun setSettingsBtnListener() = with(binding) {
        // 세부 설정 페이지
        binding.notificationBlock.setOnClickListener {
            startActivity(Intent(this@SettingActivity, NotificationSettingsActivity::class.java))
        }

        binding.tutorialBlock.setOnClickListener {
            startActivity(Intent(this@SettingActivity, TutorialActivity::class.java))
        }

        binding.themeBlock.setOnClickListener {
            startActivity(Intent(this@SettingActivity, ThemeSettingsActivity::class.java))
        }

        binding.btnBack.setOnClickListener {
            finish() // 가장 직관적이고 확실한 방법입니다.
        }
    }

    private fun applyThemeModeSettings() {
        val themeRepo = ThemeRepository
        val themeKey = ThemePrefs.getTheme(this)
        val currentTheme = themeRepo.ownedThemes.get(themeKey)

        val themePreviewImage = currentTheme!!.themePreviewImage
        val primaryColor = currentTheme!!.themeTextColorSpecialA
        val buttonColor = currentTheme!!.themeColorA
        val backgroundColor = currentTheme!!.backgroundColor
        val blockColor = currentTheme!!.themeColorA
        val calendarBackgroundImage = currentTheme!!.calendarBackgroundImage
        val memoImage = currentTheme!!.memoImage
//        val foxIcon = currentTheme!!.foxIcon
        binding.root.setBackgroundResource(backgroundColor)
        binding.notificationBlockText.setTextColor(getColor(R.color.white))
        binding.tutorialBlockText.setTextColor(getColor(R.color.white))
        binding.summaryBlockText.setTextColor(getColor(R.color.white))
        binding.btnBack.setImageResource(currentTheme.backIcon)
    }
}