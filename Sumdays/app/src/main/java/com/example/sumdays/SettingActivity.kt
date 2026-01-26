package com.example.sumdays

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

class SettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingMainBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSettingsBtnListener()

    }
    private fun setSettingsBtnListener() = with(binding) {
        // 세부 설정 페이지
        binding.notificationBlock.setOnClickListener {
            startActivity(Intent(this@SettingActivity, NotificationSettingsActivity::class.java))
        }

        binding.tutorialBlock.setOnClickListener {
            startActivity(Intent(this@SettingActivity, TutorialActivity::class.java))
        }

        binding.summaryBlock.setOnClickListener {
            val inputData = workDataOf("IS_TEST_MODE" to false) // true로 설정하면 더미 데이터 생성

            // 2. OneTimeWorkRequest 생성 (즉시 실행)
            val workRequest = OneTimeWorkRequestBuilder<WeekSummaryWorker>()
                .setInputData(inputData)
                .build()

            // 3. WorkManager에 큐 삽입
            WorkManager.getInstance(applicationContext).enqueue(workRequest)

            Toast.makeText(this@SettingActivity, "주간 통계 생성 요청됨", Toast.LENGTH_SHORT).show()
        }

        binding.btnBack.setOnClickListener {
            finish() // 가장 직관적이고 확실한 방법입니다.
        }
    }
}