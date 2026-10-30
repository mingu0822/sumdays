package com.example.sumdays

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.sync.InitialSyncScheduler
import com.example.sumdays.data.sync.InitialSyncWorker
import com.example.sumdays.data.sync.LocalDataOwnerPrefs
import com.example.sumdays.databinding.ActivityLoginBinding
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.GoogleLoginRequest
import com.example.sumdays.network.LoginRequest
import com.example.sumdays.network.LoginResponse
import com.example.sumdays.settings.prefs.UserStatsPrefs
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var userStatsPrefs: UserStatsPrefs
    private val credentialManager by lazy { CredentialManager.create(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        userStatsPrefs = UserStatsPrefs(this)
        setupListeners()
    }

    private fun setupListeners() {
        binding.loginButton.setOnClickListener { handleLogin() }
        binding.googleLoginButton.setOnClickListener { handleGoogleLogin() }
        binding.signupText.setOnClickListener {
            startActivity(Intent(this, RegisterationActivity::class.java))
        }
        binding.skipText.setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java))
        }
    }

    private fun handleLogin() {
        val email = binding.idInputEditText.text.toString().trim()
        val password = binding.passwordInputEditText.text.toString().trim()
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "이메일과 비밀번호를 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        ApiClient.api.login(LoginRequest(email, password)).enqueue(loginCallback())
    }

    private fun handleGoogleLogin() {
        val webClientId = getString(R.string.google_web_client_id)
        if (webClientId.startsWith("YOUR_")) {
            Toast.makeText(this, "Google OAuth Web Client ID를 설정해주세요.", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            try {
                val option = GetSignInWithGoogleOption.Builder(webClientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(option)
                    .build()
                val result = credentialManager.getCredential(this@LoginActivity, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    ApiClient.api.googleLogin(GoogleLoginRequest(idToken)).enqueue(loginCallback())
                } else {
                    Toast.makeText(this@LoginActivity, "Google 계정을 확인할 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (error: GoogleIdTokenParsingException) {
                Log.e("LoginActivity", "Google ID 토큰 응답 파싱 실패", error)
                Toast.makeText(this@LoginActivity, "Google 로그인에 실패했습니다.", Toast.LENGTH_SHORT).show()
            } catch (error: GetCredentialException) {
                Log.e("LoginActivity", "Google 로그인 실패", error)
                Toast.makeText(this@LoginActivity, "Google 로그인에 실패했습니다.", Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                Log.e("LoginActivity", "Google 로그인 처리 실패", error)
                Toast.makeText(this@LoginActivity, "Google 로그인에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loginCallback() = object : Callback<LoginResponse> {
        override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
            val loginResponse = response.body()
            if (response.isSuccessful && loginResponse != null && completeLogin(loginResponse)) return

            val message = loginResponse?.message ?: when (response.code()) {
                400, 401 -> "로그인 정보를 확인해주세요."
                else -> "로그인 실패 (에러 코드: ${response.code()})"
            }
            Toast.makeText(this@LoginActivity, message, Toast.LENGTH_SHORT).show()
        }

        override fun onFailure(call: Call<LoginResponse>, error: Throwable) {
            Log.e("LoginActivity", "로그인 네트워크 오류", error)
            Toast.makeText(this@LoginActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun completeLogin(loginResponse: LoginResponse): Boolean {
        val userId = loginResponse.userId ?: return false
        val token = loginResponse.token ?: return false
        if (!loginResponse.success) return false

        val previousUserId = SessionManager.getUserId()
        if (previousUserId >= 0 && previousUserId != userId) {
            WorkManager.getInstance(applicationContext)
                .cancelAllWorkByTag(InitialSyncWorker.accountWorkTag(previousUserId))
        }
        SessionManager.saveSession(userId, token)
        LocalDataOwnerPrefs.beginAccountSwitch(applicationContext)
        loginResponse.nickname?.let(userStatsPrefs::saveNickname)
        InitialSyncScheduler.ensureScheduled(applicationContext, replace = true)
        Toast.makeText(this, "로그인 성공!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, CalendarActivity::class.java))
        finish()
        return true
    }
}
