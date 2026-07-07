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

class SocialDailyReadActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySocialDailyReadBinding
    private lateinit var btnBack: ImageButton

    private var friendId: Int = -1
    private var targetDate: String? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySocialDailyReadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        friendId = intent.getIntExtra("friendId", -1)
        targetDate = intent.getStringExtra("date")
        Log.d("SocialDailyRead", "전달된 데이터 -> friendId: $friendId, targetDate: $targetDate")
        if (friendId == -1 || targetDate.isNullOrEmpty()) {
            Toast.makeText(this, "친구 일기 정보가 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        fetchFriendDiaryFromServer(friendId, targetDate!!)


        applyThemeModeSettings()

        val rootView = findViewById<View>(R.id.main)
        setupEdgeToEdge(rootView)

        btnBack = findViewById(R.id.read_back_button)
        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun applyThemeModeSettings() {

        val themeRepo = ThemeRepository
        val foxRepo = FoxRepository

        // ⭐ owned 목록 갱신 (이거 매우 중요)
        themeRepo.updateOwned()
        foxRepo.updateOwned()

        val themeKey = ThemePrefs.getTheme(this)
//        val foxKey = ThemePrefs.getFox(this)

        val currentTheme =
            themeRepo.ownedThemes[themeKey]
                ?: themeRepo.allThemeMap[themeKey]

//        val currentFox =
//            foxRepo.ownedFoxes[foxKey]
//                ?: foxRepo.allFoxMap[foxKey]

        // ⭐ null 방어
        if (currentTheme == null) {
            Toast.makeText(this, "기본 테마를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val backgroundColor = currentTheme.backgroundColor

        binding.root.setBackgroundResource(backgroundColor)

    }

    // ──────────────────────────────────
    // DB 관찰 & UI 갱신
    // ──────────────────────────────────
    private fun fetchFriendDiaryFromServer(id: Int, date: String) {
        // 상단 날짜 텍스트 렌더링
        binding.dateText.text = date
        binding.dateText.isClickable = false

        // 더미 데이터 (나중에 Retrofit 통신으로 교체될 라인)
        var diaryContent = "더미 일기입니다"

        // 텍스트뷰와 에디트텍스트에 꽂아넣기
        binding.diaryContentTextView.text = diaryContent
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