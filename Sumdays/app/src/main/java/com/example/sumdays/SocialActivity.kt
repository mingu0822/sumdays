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

class SocialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileMainBinding

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 상태바, 네비게이션바 같은 색으로
        val rootView = findViewById<View>(R.id.setting_main_root)
        setupEdgeToEdge(rootView)
    }


}