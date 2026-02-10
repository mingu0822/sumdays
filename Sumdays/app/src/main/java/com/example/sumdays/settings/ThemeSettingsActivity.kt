package com.example.sumdays.settings

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.sumdays.R
import com.example.sumdays.databinding.ActivityProfileLabsBinding
import com.example.sumdays.databinding.ActivityThemeSettingsBinding
import com.example.sumdays.settings.prefs.LabsPrefs
import com.example.sumdays.settings.prefs.ThemeState
import com.example.sumdays.utils.setupEdgeToEdge

class ThemeSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityThemeSettingsBinding
    private lateinit var themeSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemeSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySavedTheme()
        themeSwitch = findViewById(R.id.dark_theme_toggle)

        // 현재 모드에 따라 스위치 초기 상태 설정
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        themeSwitch.isChecked = (currentMode == AppCompatDelegate.MODE_NIGHT_YES)
        ThemeState.isDarkMode = themeSwitch.isChecked

        setupHeaderClickListener()
        setupSwitchListener()

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

    private fun saveThemeMode(isDark: Boolean) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        prefs.edit()
            .putBoolean("dark_mode", isDark)
            .apply()
    }
    private fun applySavedTheme() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", true) // 기본값: 다크
        val mode = if (isDark) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun setupHeaderClickListener() {
        binding.header.headerBackIcon.setOnClickListener {
            finish()
        }
    }

    private fun setupSwitchListener() {
        // 스위치 눌렀을 때 모드 변경
        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)   // 다크
                saveThemeMode(true)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)    // 라이트
                saveThemeMode(false)
            }
        }

    }
}

