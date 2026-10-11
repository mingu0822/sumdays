package com.example.sumdays.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.sumdays.customize.ThemeAdapter
import com.example.sumdays.auth.AccountInventory
import com.example.sumdays.auth.InventoryOwner
import com.example.sumdays.databinding.ActivityThemeSettingsBinding
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository

class ThemeSettingsActivity : AppCompatActivity() {
    private lateinit var inventoryOwner: InventoryOwner

    private lateinit var binding: ActivityThemeSettingsBinding
    private lateinit var adapter: ThemeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        inventoryOwner = InventoryOwner(savedInstanceState)
        super.onCreate(savedInstanceState)
        if (!inventoryOwner.requireCurrent(this)) return

        binding = ActivityThemeSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 보유한 테마 목록 갱신
        AccountInventory.refreshOwnership(this)

        setupHeaderClickListener()
        setupThemeToggle()
        setupThemeList()
        applyThemeModeSettings()
    }

    /**
     * 현재 테마 적용
     */
    private fun applyThemeModeSettings() {

        val themeId = ThemePrefs.getTheme(this)

        val currentTheme =
            ThemeRepository.ownedThemes[themeId] ?: return

        val primaryColor =
            ContextCompat.getColor(
                this,
                currentTheme.themeTextColorSpecialA
            )

        binding.root.setBackgroundResource(
            currentTheme.backgroundColor
        )

        binding.header.headerTitle.setTextColor(
            primaryColor
        )

        binding.header.headerBackIcon.setColorFilter(
            primaryColor
        )

        binding.themeToggle.setTextColor(
            primaryColor
        )

        binding.themeList.setBackgroundResource(
            currentTheme.themeColorA
        )
    }

    /**
     * 뒤로가기
     */
    private fun setupHeaderClickListener() {

        binding.header.headerBackIcon.setOnClickListener {
            finish()
        }
    }

    /**
     * 펼치기 / 접기
     */
    private fun setupThemeToggle() {

        binding.themeToggle.setOnClickListener {

            binding.themeList.visibility =
                if (binding.themeList.visibility == View.VISIBLE)
                    View.GONE
                else
                    View.VISIBLE
        }
    }

    /**
     * 테마 목록
     */
    private fun setupThemeList() {

        adapter = ThemeAdapter(

            items = ThemeRepository
                .ownedThemes
                .values
                .toList(),

            appliedTheme = ThemePrefs.getTheme(this),

            onClick = { theme ->
                if (!inventoryOwner.requireCurrent(this)) return@ThemeAdapter

                ThemePrefs.saveTheme(
                    this,
                    theme.id
                )

                adapter.setAppliedTheme(
                    theme.id
                )

                applyThemeModeSettings()
            }
        )

        binding.themeList.layoutManager =
            GridLayoutManager(this, 2)

        binding.themeList.adapter = adapter

        binding.themeList.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        inventoryOwner.requireCurrent(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        inventoryOwner.saveState(outState)
        super.onSaveInstanceState(outState)
    }
}
