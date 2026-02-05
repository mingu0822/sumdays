package com.example.sumdays

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.sumdays.daily.memo.MemoRepository
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.data.sync.BackupScheduler
import com.example.sumdays.data.repository.WeekSummaryRepository
import com.example.sumdays.data.repository.DailyEntryRepository
import com.example.sumdays.statistics.WeekSummaryScheduler
import com.jakewharton.threetenabp.AndroidThreeTen

//데이터베이스와 저장소(Repository)를 초기화, 싱글톤 패턴을 위한 애플리케이션 클래스
open class MyApplication : Application() {
    // Lazy를 사용하여 데이터베이스와 리포지토리를 필요할 때만 초기화
    private val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { MemoRepository(database.memoDao()) }

    // 통계 화면용 Repository 초기화
    val weekSummaryRepository by lazy { WeekSummaryRepository(database.weekSummaryDao()) }

    val dailyEntryRepository by lazy { DailyEntryRepository(database.dailyEntryDao()) }

    override fun onCreate() {
        super.onCreate()
        AndroidThreeTen.init(this)
        BackupScheduler.scheduleAutoBackup(this)
        WeekSummaryScheduler.scheduleWeeklyTask(this)


        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_mode", false)

        val mode = if (isDark) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }

        AppCompatDelegate.setDefaultNightMode(mode)
    }
}