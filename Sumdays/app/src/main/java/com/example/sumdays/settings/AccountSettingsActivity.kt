package com.example.sumdays.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sumdays.R
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.userInfo.UserInfoRepository
import com.example.sumdays.databinding.ActivityProfileAccountBinding
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.ChangePasswordRequest
import com.example.sumdays.network.ChangePasswordResponse
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.utils.setupEdgeToEdge
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AccountSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileAccountBinding
    private lateinit var userInfoRepository : UserInfoRepository

    // Retrofit API 서비스 객체와 세션 매니저
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userInfoRepository = UserInfoRepository(applicationContext)

        // 초기 닉네임 표시
        displayCurrentNickname()

        setupListeners()
        setupHeaderListener()

        applyThemeModeSettings()

        // 상태바, 네비게이션바 같은 색으로
        val rootView = findViewById<View>(R.id.setting_account_root)
        setupEdgeToEdge(rootView)
    }

    private fun applyThemeModeSettings() {
        val themeKey = ThemePrefs.getTheme(this)
        val currentTheme = ThemeRepository.ownedThemes[themeKey] ?: return

        val primaryColor = currentTheme.themeColorA
        val buttonColor = currentTheme.themeColorA
        val backgroundColor = currentTheme.backgroundColor
        val blockColor = currentTheme.themeColorA
        val blockStyle = currentTheme.blockStyleA
        val textColor = currentTheme.themeColorD

        binding.root.setBackgroundResource(backgroundColor)

        binding.newNicknameInputEditText.setBackgroundResource(blockStyle)
        binding.currentPasswordInputEditText.setBackgroundResource(blockStyle)
        binding.newPasswordInputEditText.setBackgroundResource(blockStyle)
        binding.confirmPasswordInputEditText.setBackgroundResource(blockStyle)

        binding.currentNicknameTextView.setTextColor(getColor(primaryColor))
        binding.header.headerTitle.setTextColor(getColor(textColor))

        binding.updateNicknameButton.setBackgroundColor(getColor(buttonColor))
        binding.changePasswordButton.setBackgroundColor(getColor(buttonColor))

        binding.updateNicknameButton.setTextColor(getColor(R.color.white))
        binding.changePasswordButton.setTextColor(getColor(R.color.white))

        binding.header.headerBackIcon.setColorFilter(getColor(textColor))
    }

    private fun setupHeaderListener() {
        // XML에서 include된 헤더의 뷰에 접근합니다.
        // ActivitySettingsAccountBinding이 포함하고 있는 헤더 바인딩 객체를 사용합니다.

        // headerBackIcon을 클릭하면 Activity를 종료하여 이전 화면(SettingsActivity)으로 돌아갑니다.
        binding.header.headerBackIcon.setOnClickListener {
            finish()
        }

        // 선택 사항: 헤더의 제목 설정 (account settings로)
        binding.header.headerTitle.text = "계정 설정"
    }

    /**
     * 현재 SharedPreferences에 저장된 닉네임을 UI에 표시합니다.
     */
    private fun displayCurrentNickname() {
        // UI의 닉네임 표시 TextView ID를 currentNicknameTextView라고 가정
        binding.currentNicknameTextView.text = SessionManager.getNickname()
    }

    private fun setupListeners() {
        binding.updateNicknameButton.setOnClickListener {
            handleNicknameUpdate()
        }

        binding.changePasswordButton.setOnClickListener {
            handleChangePassword()
        }
    }

    // --- 1. 닉네임 변경 로직 ---

    private fun handleNicknameUpdate() {
        val newNickname = binding.newNicknameInputEditText
            .text.toString().trim()

        if (newNickname.isEmpty()) {
            Toast.makeText(
                this,
                "새로운 닉네임을 입력해주세요.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        lifecycleScope.launch {
            val result = userInfoRepository.updateNickname(newNickname)

            result.onSuccess {
                displayCurrentNickname()

                Toast.makeText(
                    this@AccountSettingsActivity,
                    "닉네임이 변경되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            result.onFailure { error ->
                Toast.makeText(
                    this@AccountSettingsActivity,
                    error.message ?: "닉네임 변경에 실패했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // --- 2. 비밀번호 변경 로직 ---

    private fun handleChangePassword() {
        val currentPassword = binding.currentPasswordInputEditText.text.toString()
        val newPassword = binding.newPasswordInputEditText.text.toString()
        val confirmPassword = binding.confirmPasswordInputEditText.text.toString()
        val token = SessionManager.getToken()

        // 1. 유효성 검사 (클라이언트 측)
        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "모든 비밀번호 필드를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (newPassword != confirmPassword) {
            Toast.makeText(this, "새 비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        if (token.isNullOrEmpty()) {
            Toast.makeText(this, "인증 정보가 없습니다. 다시 로그인 해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        // TODO: 새 비밀번호 복잡성 검사 로직 추가 (예: 8자 이상 등)

        // 2. API 요청 객체 생성
        val request = ChangePasswordRequest(currentPassword, newPassword)

        // 3. 서버 호출 (PUT /api/auth/password)
        ApiClient.api.changePassword("Bearer $token", request).enqueue(object : Callback<ChangePasswordResponse> {
            override fun onResponse(call: Call<ChangePasswordResponse>, response: Response<ChangePasswordResponse>) {
                if (response.isSuccessful) {
                    val changeResponse = response.body()
                    if (changeResponse != null && changeResponse.success) {
                        Toast.makeText(this@AccountSettingsActivity, "비밀번호 변경 성공! 다시 로그인 해주세요.", Toast.LENGTH_LONG).show()
                        // 비밀번호가 바뀌었으므로, 보안을 위해 세션을 삭제하고 로그인 화면으로 이동하는 것이 일반적
                        // sessionManager.clearSession()
                        // startActivity(Intent(this@AccountSettingsActivity, LoginActivity::class.java))
                        finish()
                    } else {
                        val message = changeResponse?.message ?: "비밀번호 변경에 실패했습니다. (현재 비밀번호 불일치 등)"
                        Toast.makeText(this@AccountSettingsActivity, message, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@AccountSettingsActivity, "비밀번호 변경 실패 (에러 코드: ${response.code()})", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ChangePasswordResponse>, t: Throwable) {
                Toast.makeText(this@AccountSettingsActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        })
    }
}