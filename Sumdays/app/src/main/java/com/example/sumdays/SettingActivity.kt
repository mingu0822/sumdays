package com.example.sumdays

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.sumdays.databinding.ActivitySettingMainBinding
import com.example.sumdays.settings.NotificationSettingsActivity
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.statistics.WeekSummaryWorker
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

    private fun applyThemeModeSettings(){
        // Apply dark mode
        ThemeState.isDarkMode = (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)

        if (ThemeState.isDarkMode){
            binding.notificationBlockText.setTextColor(getColor(R.color.white))
            binding.tutorialBlockText.setTextColor(getColor(R.color.white))
            binding.themeBlockText.setTextColor(getColor(R.color.white))
            binding.btnBack.setImageResource(R.drawable.ic_arrow_back_white)
        }
        else{
            binding.notificationBlockText.setTextColor(getColor(R.color.white))
            binding.tutorialBlockText.setTextColor(getColor(R.color.white))
            binding.themeBlockText.setTextColor(getColor(R.color.white))
            binding.btnBack.setImageResource(R.drawable.ic_arrow_back_black)
        }
    }
}