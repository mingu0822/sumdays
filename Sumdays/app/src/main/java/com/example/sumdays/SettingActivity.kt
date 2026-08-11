package com.example.sumdays

// 주간통계 수동 추가 (기능 보류, 추후 복구)
// import android.widget.Toast
// import androidx.work.OneTimeWorkRequestBuilder
// import androidx.work.WorkManager
// import androidx.work.workDataOf
// import com.example.sumdays.statistics.WeekSummaryWorker
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.databinding.ActivitySettingMainBinding
import com.example.sumdays.settings.AccountSettingsActivity
import com.example.sumdays.settings.DiaryStyleSettingsActivity
import com.example.sumdays.settings.EditProfileActivity
import com.example.sumdays.settings.LabsSettingsActivity
import com.example.sumdays.settings.NotificationSettingsActivity
import com.example.sumdays.settings.prefs.DiaryPermissionPrefs
import com.example.sumdays.settings.prefs.ProfileImagePrefs
import com.example.sumdays.settings.prefs.UserStatsPrefs
import com.example.sumdays.settings.profileimage.ProfileImageItem
import com.example.sumdays.settings.profileimage.ProfileImageItemType
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.utils.setupEdgeToEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingMainBinding
    private lateinit var userStatsPrefs: UserStatsPrefs


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SessionManager.init(applicationContext)
        userStatsPrefs = UserStatsPrefs(this)

        setSettingsBtnListener()
        updateAuthUI()
        applyThemeModeSettings()

        // 다른 화면들과 동일하게 시스템 바 투명 처리 (edge-to-edge)
        setupEdgeToEdge(binding.settingMainRoot)
    }

    override fun onResume() {
        super.onResume()
        // 프로필 편집 후 돌아왔을 때 최신 상태로 갱신
        updateAuthUI()
        updateProfileImagePreview()
    }

    private fun setSettingsBtnListener() = with(binding) {
        // 프로필 블록
        profileImageContainer.setOnClickListener {
            startActivity(Intent(this@SettingActivity, EditProfileActivity::class.java))
        }

        loginButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(applicationContext)

                db.memoDao().clearAll()
                db.dailyEntryDao().clearAll()
                db.userStyleDao().clearAll()
                db.weekSummaryDao().clearAll()
            }

            SessionManager.clearSession()
            // 로그인 화면으로 이동
            val intent = Intent(this@SettingActivity, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

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

    private fun updateAuthUI() {
        if (SessionManager.isLoggedIn()) {
            binding.nickname.text = userStatsPrefs.getNickname()
            binding.loginButton.text = "로그아웃"
        } else {
            binding.nickname.text = "닉네임"
            binding.loginButton.text = "로그인 / 회원가입"
        }
    }

    private fun updateProfileImagePreview() {
        val mode = ProfileImagePrefs.getMode(this)

        if (mode == "PHOTO") {
            showPhotoMode()
        } else {
            showAvatarMode()
        }
    }

    private fun showPhotoMode() {
        // 사진 레이어 표시
        binding.imgPhoto.visibility = View.VISIBLE
        // 아바타 레이어 숨김
        binding.imgBase.visibility = View.GONE
        binding.imgMouth.visibility = View.GONE
        binding.imgEyes.visibility = View.GONE
        binding.imgAccessory.visibility = View.GONE

        val path = ProfileImagePrefs.getPhotoUri(this)
        if (path != null) {
            val bitmap: Bitmap? = BitmapFactory.decodeFile(path)
            binding.imgPhoto.setImageBitmap(bitmap)
        }
    }

    private fun showAvatarMode() {
        // 사진 레이어 숨김
        binding.imgPhoto.visibility = View.GONE
        // 아바타 레이어 표시
        binding.imgBase.visibility = View.VISIBLE
        binding.imgMouth.visibility = View.VISIBLE
        binding.imgEyes.visibility = View.VISIBLE
        binding.imgAccessory.visibility = View.VISIBLE

        val faceId = ProfileImagePrefs.getFaceId(this)
        val eyesId = ProfileImagePrefs.getEyesId(this)
        val mouthId = ProfileImagePrefs.getMouthId(this)
        val accId = ProfileImagePrefs.getAccId(this)

        // TODO: 더미 데이터 에셋과 json으로 바꾸기
        val items = listOf(
            ProfileImageItem(1, ProfileImageItemType.FACE, R.drawable.nav_fox_button),
            ProfileImageItem(2, ProfileImageItemType.FACE, R.drawable.dailyread_fox_face_level_5),
            ProfileImageItem(3, ProfileImageItemType.FACE, 0),
            ProfileImageItem(4, ProfileImageItemType.EYES, R.drawable.loading_animation),
            ProfileImageItem(5, ProfileImageItemType.EYES, 0)
        )

        binding.imgBase.setImageResource(items.find { it.id == faceId }?.resId ?: 0)
        binding.imgBase.setColorFilter("#FFE0BD".toColorInt())
        binding.imgEyes.setImageResource(items.find { it.id == eyesId }?.resId ?: 0)
        binding.imgEyes.setColorFilter(Color.BLACK)
        binding.imgMouth.setImageResource(items.find { it.id == mouthId }?.resId ?: 0)
        binding.imgMouth.setColorFilter(Color.CYAN)
        binding.imgAccessory.setImageResource(items.find { it.id == accId }?.resId ?: 0)
        binding.imgAccessory.setColorFilter(Color.YELLOW)
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

        binding.userBlock.setBackgroundResource(blockShape)
        binding.loginButton.setBackgroundColor(getColor(primaryColor))
        binding.nickname.setTextColor(getColor(basicColor))

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
