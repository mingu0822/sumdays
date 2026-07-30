package com.example.sumdays

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.sumdays.settings.prefs.DiaryPermissionPrefs
import com.example.sumdays.databinding.ActivitySettingMainBinding
import com.example.sumdays.settings.AccountSettingsActivity
import com.example.sumdays.settings.DiaryStyleSettingsActivity
import com.example.sumdays.settings.LabsSettingsActivity
import com.example.sumdays.settings.NotificationSettingsActivity
// 주간통계 수동 추가 (기능 보류, 추후 복구)
// import android.widget.Toast
// import androidx.work.OneTimeWorkRequestBuilder
// import androidx.work.WorkManager
// import androidx.work.workDataOf
// import com.example.sumdays.statistics.WeekSummaryWorker
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.utils.setupEdgeToEdge

class SettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingMainBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSettingsBtnListener()
        applyThemeModeSettings()

        // 다른 화면들과 동일하게 시스템 바 투명 처리 (edge-to-edge)
        setupEdgeToEdge(binding.settingMainRoot)
    }

    private fun setSettingsBtnListener() = with(binding) {
        // 세부 설정 페이지
        binding.diaryStyleBlock.setOnClickListener {
            startActivity(Intent(this@SettingActivity, DiaryStyleSettingsActivity::class.java))
        }

        binding.accountBlock.setOnClickListener {
            startActivity(Intent(this@SettingActivity, AccountSettingsActivity::class.java))
        }

        binding.labsBlock.setOnClickListener {
            startActivity(Intent(this@SettingActivity, LabsSettingsActivity::class.java))
        }

        binding.notificationBlock.setOnClickListener {
            startActivity(Intent(this@SettingActivity, NotificationSettingsActivity::class.java))
        }

        binding.diaryPermissionBlock.setOnClickListener {
            showDiaryPermissionDialog()
        }

        binding.tutorialBlock.setOnClickListener {
            startActivity(Intent(this@SettingActivity, TutorialActivity::class.java))
        }

        // 주간통계 수동 추가 (기능 보류, 추후 복구)
        // binding.summaryBlock.setOnClickListener {
        //     val inputData = workDataOf("IS_TEST_MODE" to false) // true로 설정하면 더미 데이터 생성
        //
        //     // 2. OneTimeWorkRequest 생성 (즉시 실행)
        //     val workRequest = OneTimeWorkRequestBuilder<WeekSummaryWorker>()
        //         .setInputData(inputData)
        //         .build()
        //
        //     // 3. WorkManager에 큐 삽입
        //     WorkManager.getInstance(applicationContext).enqueue(workRequest)
        //
        //     Toast.makeText(this@SettingActivity, "주간 통계 생성 요청됨", Toast.LENGTH_SHORT).show()
        // }

        binding.btnBack.setOnClickListener {
            finish() // 가장 직관적이고 확실한 방법입니다.
        }
    }

    // 일기 생성 시 소셜 공개 기본값 선택
    private fun showDiaryPermissionDialog() {
        val options = arrayOf("매번 물어보기", "기본 비공개", "기본 공개")
        val modes = intArrayOf(
            DiaryPermissionPrefs.MODE_ASK,
            DiaryPermissionPrefs.MODE_DEFAULT_FALSE,
            DiaryPermissionPrefs.MODE_DEFAULT_TRUE
        )
        val checkedIndex = modes.indexOf(DiaryPermissionPrefs.getMode(this))

        AlertDialog.Builder(this)
            .setTitle("일기 공개 설정")
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                DiaryPermissionPrefs.setMode(this, modes[which])
                dialog.dismiss()
            }
            .setNegativeButton("취소") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun applyThemeModeSettings() {
        val themeRepo = ThemeRepository
        val themeKey = ThemePrefs.getTheme(this)
        val currentTheme = themeRepo.ownedThemes.get(themeKey)

        val themePreviewImage = currentTheme!!.themePreviewImage
        val primaryColor = currentTheme!!.themeTextColorSpecialA
        val buttonColor = currentTheme!!.themeColorA
        val backgroundColor = currentTheme!!.backgroundColor
        val blockShape = currentTheme!!.blockStyleA
        val basicColor = currentTheme!!.themeTextColorBasic
        val calendarBackgroundImage = currentTheme!!.calendarBackgroundImage
        val memoImage = currentTheme!!.memoImage
//        val foxIcon = currentTheme!!.foxIcon
        binding.root.setBackgroundResource(backgroundColor)

        binding.diaryStyleBlock.setBackgroundResource(blockShape)
        binding.diaryPermissionBlock.setBackgroundResource(blockShape)
        binding.notificationBlock.setBackgroundResource(blockShape)
        binding.accountBlock.setBackgroundResource(blockShape)
        binding.labsBlock.setBackgroundResource(blockShape)
        binding.tutorialBlock.setBackgroundResource(blockShape)
        // binding.summaryBlock.setBackgroundResource(blockShape)

        binding.diaryStyleBlockText.setTextColor(getColor(basicColor))
        binding.diaryPermissionBlockText.setTextColor(getColor(basicColor))
        binding.accountBlockText.setTextColor(getColor(basicColor))
        binding.labsBlockText.setTextColor(getColor(basicColor))
        binding.notificationBlockText.setTextColor(getColor(basicColor))
        binding.tutorialBlockText.setTextColor(getColor(basicColor))
        // binding.summaryBlockText.setTextColor(getColor(basicColor))
        binding.btnBack.setImageResource(R.drawable.ic_left)
    }
}
