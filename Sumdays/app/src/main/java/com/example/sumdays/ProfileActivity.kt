package com.example.sumdays

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.data.viewModel.DailyEntryViewModel
import com.example.sumdays.databinding.ActivityProfileMainBinding
import com.example.sumdays.settings.AccountSettingsActivity
import com.example.sumdays.settings.DiaryStyleSettingsActivity
import com.example.sumdays.settings.EditProfileActivity
import com.example.sumdays.settings.LabsSettingsActivity
import com.example.sumdays.settings.ThemeSettingsActivity
import com.example.sumdays.settings.prefs.ProfileImagePrefs
import com.example.sumdays.settings.prefs.UserStatsPrefs
import com.example.sumdays.settings.profileimage.ProfileImageItem
import com.example.sumdays.settings.profileimage.ProfileImageItemType
import com.example.sumdays.theme.FoxRepository
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource
import com.example.sumdays.utils.setupEdgeToEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    fun updateOwned(){
        ThemeRepository.updateOwned()
        FoxRepository.updateOwned()
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보일 때마다 UI를 최신 상태로 갱신합니다.
        updateAuthUI()
        updateOwned()
        applyThemeModeSettings()
        updateProfileImagePreview()
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
        val themeKey = ThemePrefs.getTheme(this)
        val currentTheme = ThemeRepository.ownedThemes[themeKey] ?: return

        val backgroundColor = currentTheme.backgroundColor
        val blockShape = currentTheme.blockStyle
        val textPrimaryColor = currentTheme.textPrimaryColor
        val basicColor = currentTheme.themeTextColor_basic




        binding.root.setBackgroundColor(getColor(backgroundColor))

        binding.diaryStyleBlock.setBackgroundResource(blockShape)
        binding.labsBlock.setBackgroundResource(blockShape)
        binding.accountBlock.setBackgroundResource(blockShape)
        binding.themeBlock.setBackgroundResource(blockShape)
        binding.userBlock.setBackgroundResource(blockShape)

        binding.loginButton.setBackgroundColor(getColor(textPrimaryColor))




        binding.nickname.setTextColor(getColor(basicColor))
        binding.diaryStyleBlockText.setTextColor(getColor(basicColor))
        binding.labsBlockText.setTextColor(getColor(basicColor))
        binding.accountBlockText.setTextColor(getColor(basicColor))
        binding.themeBlockText.setTextColor(getColor(basicColor))
    }


    private fun setSettingsBtnListener() = with(binding) {
        profileImageContainer.setOnClickListener {
            startActivity(Intent(this@ProfileActivity, EditProfileActivity::class.java))
        }

        diaryStyleBlock.setOnClickListener {
            startActivity(Intent(this@ProfileActivity, DiaryStyleSettingsActivity::class.java))
        }

        accountBlock.setOnClickListener {
            startActivity(Intent(this@ProfileActivity, AccountSettingsActivity::class.java))
        }

        labsBlock.setOnClickListener {
            startActivity(Intent(this@ProfileActivity, LabsSettingsActivity::class.java))
        }

        themeBlock.setOnClickListener {
            startActivity(Intent(this@ProfileActivity, ThemeSettingsActivity::class.java))
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
}