package com.example.sumdays.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.sumdays.databinding.ActivityThemeSettingsBinding
import com.example.sumdays.theme.ThemeAdapter
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository

class ThemeSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThemeSettingsBinding
    private lateinit var adapter: ThemeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityThemeSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeaderClickListener()
        setupThemeToggle()
        setupThemeList()
        applyThemeModeSettings()
    }

    private fun applyThemeModeSettings() {
        val themeKey = ThemePrefs.getTheme(this)
        val currentTheme = ThemeRepository.ownedThemes[themeKey] ?: return

        val primaryColor = ContextCompat.getColor(this, currentTheme.textPrimaryColor)
        val backgroundColor = currentTheme.backgroundColor
        val blockColor = currentTheme.blockColor

        // 전체 배경
        binding.root.setBackgroundResource(backgroundColor)

        // 헤더
        binding.header.headerTitle.setTextColor(primaryColor)
        binding.header.headerBackIcon.setColorFilter(primaryColor)

        // 토글 텍스트
        binding.themeToggle.setTextColor(primaryColor)

        // 테마 리스트 배경
        binding.themeList.setBackgroundResource(blockColor)
    }

    private fun setupHeaderClickListener() {
        binding.header.headerBackIcon.setOnClickListener {
            finish()
        }
    }

    // 테마 목록 토글
    private fun setupThemeToggle() {
        binding.themeToggle.setOnClickListener {
            binding.themeList.visibility =
                if (binding.themeList.visibility == View.VISIBLE) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
        }
    }

    // 테마 목록
    private fun setupThemeList() {
        adapter = ThemeAdapter(
            themes = ThemeRepository.ownedThemes,
            selectedThemeKey = ThemePrefs.getTheme(this)
        ) { themeKey, _ ->
            ThemePrefs.saveTheme(this, themeKey)
            recreate()
        }

        binding.themeList.layoutManager = GridLayoutManager(this, 2)
        binding.themeList.adapter = adapter
        binding.themeList.visibility = View.VISIBLE
    }
}