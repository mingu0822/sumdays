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

        binding.summaryBlock.setOnClickListener {
            val inputData = workDataOf("IS_TEST_MODE" to false) // true로 설정하면 더미 데이터 생성

            // 2. OneTimeWorkRequest 생성 (즉시 실행)
            val workRequest = OneTimeWorkRequestBuilder<WeekSummaryWorker>()
                .setInputData(inputData)
                .build()

            // 3. WorkManager에 큐 삽입
            WorkManager.getInstance(applicationContext).enqueue(workRequest)

            Toast.makeText(this@SettingActivity, "주간 통계 생성 요청됨", Toast.LENGTH_SHORT).show()
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
        val primaryColor = currentTheme!!.primaryColor
        val buttonColor = currentTheme!!.buttonColor
        val backgroundColor = currentTheme!!.backgroundColor
        val blockColor = currentTheme!!.blockColor
        val calendarBackgroundImage = currentTheme!!.calendarBackgroundImage
        val memoImage = currentTheme!!.memoImage
//        val foxIcon = currentTheme!!.foxIcon
        binding.root.setBackgroundResource(backgroundColor)
        binding.notificationBlockText.setTextColor(getColor(R.color.white))
        binding.tutorialBlockText.setTextColor(getColor(R.color.white))
        binding.summaryBlockText.setTextColor(getColor(R.color.white))
        binding.btnBack.setImageResource(R.drawable.ic_arrow_back_black)
    }
}