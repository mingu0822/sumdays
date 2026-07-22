package com.example.sumdays.social.diary

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.sumdays.R
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import android.util.Log
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.sumdays.data.repository.AnalysisRepository
import com.example.sumdays.daily.memo.MoodRepository
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.data.DailyEntry
import com.example.sumdays.data.viewModel.DailyEntryViewModel
import com.example.sumdays.settings.prefs.UserStatsPrefs
import com.example.sumdays.databinding.ActivityDailyReadBinding
import com.example.sumdays.databinding.ActivitySocialDailyReadBinding
import com.example.sumdays.image.GalleryItem
import com.example.sumdays.image.PhotoGalleryAdapter
import com.example.sumdays.theme.FoxRepository
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource
import com.example.sumdays.utils.setupEdgeToEdge
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.core.content.IntentCompat

class SocialDailyReadActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySocialDailyReadBinding
    private lateinit var btnBack: ImageButton
    private var dailyEntry: DailyEntry? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySocialDailyReadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dailyEntry = IntentCompat.getParcelableExtra(
            intent,
            "dailyEntry",
            DailyEntry::class.java
        )

        if (dailyEntry == null) {
            Toast.makeText(this, "일기 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initUi()
        applyThemeModeSettings()

        val rootView = findViewById<View>(R.id.main)
        setupEdgeToEdge(rootView)

        btnBack = findViewById(R.id.read_back_button)
        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun initUi(){
        binding.dateText.text = dailyEntry?.date
        binding.diaryContentTextView.text = dailyEntry?.diary
    }

    private fun applyThemeModeSettings() {

        val themeRepo = ThemeRepository
        val foxRepo = FoxRepository

        // ⭐ owned 목록 갱신 (이거 매우 중요)
        themeRepo.updateOwned()
        foxRepo.updateOwned()

        val themeKey = ThemePrefs.getTheme(this)
        val foxKey = ThemePrefs.getFox(this)

        val currentTheme =
            themeRepo.ownedThemes[themeKey]
                ?: themeRepo.allThemeMap[themeKey]

        val currentFox =
            foxRepo.ownedFoxes[foxKey]
                ?: foxRepo.allFoxMap[foxKey]

        // ⭐ null 방어
        if (currentTheme == null || currentFox == null) {
            Toast.makeText(this, "기본 테마를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val backgroundColor = currentTheme.backgroundColor

        binding.root.setBackgroundResource(backgroundColor)

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
}
