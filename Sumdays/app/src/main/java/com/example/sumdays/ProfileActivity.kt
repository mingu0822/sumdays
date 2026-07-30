package com.example.sumdays

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.sumdays.databinding.ActivityProfileMainBinding
import com.example.sumdays.utils.setupEdgeToEdge
import com.example.sumdays.theme.FoxRepository
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileMainBinding
    private lateinit var navBarController: NavBarController

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyThemeModeSettings()

        navBarController = NavBarController(this)
        navBarController.setNavigationBar(NavSource.PROFILE)

        // 상태바, 네비게이션바 같은 색으로
        val rootView = findViewById<View>(R.id.setting_main_root)
        setupEdgeToEdge(rootView)
    }

    fun updateOwned(){
        ThemeRepository.updateOwned()
        FoxRepository.updateOwned()
    }

    override fun onResume() {
        super.onResume()
        updateOwned()
        applyThemeModeSettings()
    }

    private fun applyThemeModeSettings(){
        val themeKey = ThemePrefs.getTheme(this)
        val currentTheme = ThemeRepository.ownedThemes[themeKey] ?: return

        binding.root.setBackgroundColor(getColor(currentTheme.backgroundColor))
    }
}
