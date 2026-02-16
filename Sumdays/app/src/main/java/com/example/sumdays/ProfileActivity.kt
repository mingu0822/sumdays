package com.example.sumdays

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.sumdays.databinding.ActivityProfileMainBinding
import com.example.sumdays.settings.AccountSettingsActivity
import com.example.sumdays.settings.DiaryStyleSettingsActivity
import com.example.sumdays.settings.NotificationSettingsActivity
import com.example.sumdays.settings.prefs.UserStatsPrefs
import androidx.lifecycle.ViewModelProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.sumdays.data.viewModel.DailyEntryViewModel
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.sync.BackupScheduler
import com.example.sumdays.data.sync.InitialSyncWorker
import com.example.sumdays.settings.LabsSettingsActivity
import com.example.sumdays.statistics.WeekSummaryWorker
import com.example.sumdays.utils.setupEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.settings.ThemeSettingsActivity
import com.example.sumdays.settings.prefs.ThemeState
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileMainBinding
    private lateinit var viewModel: DailyEntryViewModel
    private lateinit var userStatsPrefs: UserStatsPrefs
    private lateinit var navBarController: NavBarController

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 인스턴스 초기화
        userStatsPrefs = UserStatsPrefs(this)
        viewModel = ViewModelProvider(this)[DailyEntryViewModel::class.java]

        updateAuthUI()

        binding.loginButton.setOnClickListener {

            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(applicationContext)

                db.memoDao().clearAll()
                db.dailyEntryDao().clearAll()
                db.userStyleDao().clearAll()
                db.weekSummaryDao().clearAll()
            }



            SessionManager.clearSession()
            // 로그인 화면으로 이동
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        applyThemeModeSettings()

        setSettingsBtnListener()
        navBarController = NavBarController(this)
        navBarController.setNavigationBar(NavSource.PROFILE)

        // 상태바, 네비게이션바 같은 색으로
        val rootView = findViewById<View>(R.id.setting_main_root)
        setupEdgeToEdge(rootView)
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보일 때마다 UI를 최신 상태로 갱신합니다.
        updateAuthUI()
    }
    private fun updateAuthUI() {
        val isLoggedIn = SessionManager.isLoggedIn()

        if (isLoggedIn) {
            // [로그인 된 상태]
            binding.nickname.text = userStatsPrefs.getNickname()
            binding.loginButton.text = "로그아웃"
        } else {
            // [비로그인 상태]
            binding.nickname.text = "닉네임"
            binding.loginButton.text = "로그인 / 회원가입"
        }
    }

    private fun applyThemeModeSettings(){
        // Apply dark mode
        ThemeState.isDarkMode = (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)

        if (ThemeState.isDarkMode){
            binding.nickname.setTextColor(getColor(R.color.white))
            binding.diaryStyleBlockText.setTextColor(getColor(R.color.white))
            binding.labsBlockText.setTextColor(getColor(R.color.white))
            binding.accountBlockText.setTextColor(getColor(R.color.white))
            binding.themeBlockText.setTextColor(getColor(R.color.white))
        }
        else{
            binding.nickname.setTextColor(getColor(R.color.white))
            binding.diaryStyleBlockText.setTextColor(getColor(R.color.white))
            binding.labsBlockText.setTextColor(getColor(R.color.white))
            binding.accountBlockText.setTextColor(getColor(R.color.white))
            binding.themeBlockText.setTextColor(getColor(R.color.white))
        }
    }


    private fun setSettingsBtnListener() = with(binding) {
        binding.diaryStyleBlock.setOnClickListener {
            startActivity(Intent(this@ProfileActivity, DiaryStyleSettingsActivity::class.java))
        }

        binding.accountBlock.setOnClickListener {
            startActivity(Intent(this@ProfileActivity, AccountSettingsActivity::class.java))
        }

        binding.labsBlock.setOnClickListener {
            startActivity(Intent(this@ProfileActivity, LabsSettingsActivity::class.java))
        }

        binding.themeBlock.setOnClickListener {
            startActivity(Intent(this@ProfileActivity, ThemeSettingsActivity::class.java))
        }

    }
}