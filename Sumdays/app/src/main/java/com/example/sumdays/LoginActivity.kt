package com.example.sumdays

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.sync.InitialSyncWorker
import com.example.sumdays.databinding.ActivityLoginBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.sumdays.settings.prefs.UserStatsPrefs
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.LoginRequest
import com.example.sumdays.network.LoginResponse
import com.example.sumdays.utils.setupEdgeToEdge

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var userStatsPrefs: UserStatsPrefs
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Prefs 초기화
        userStatsPrefs = UserStatsPrefs(this)

        setupListeners()


    }

    private fun setupListeners() {
        // "LOGIN" 버튼 클릭 시 handleLogin() 함수 호출
        binding.loginButton.setOnClickListener {
            handleLogin()
        }
        binding.signupText.setOnClickListener {
            val intent = Intent(this, RegisterationActivity::class.java)
            startActivity(intent)
        }
        binding.skipText.setOnClickListener {
            val intent = Intent(this, CalendarActivity::class.java)
            startActivity(intent)
        }
    }

    private fun handleLogin() {
        // 1. EditText에서 사용자 입력 값(이메일, 비밀번호)을 가져옵니다.
        val email = binding.idInputEditText.text.toString().trim()
        val password = binding.passwordInputEditText.text.toString().trim()

        // 입력 값 유효성 검사
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "이메일과 비밀번호를 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. 서버에 보낼 요청 데이터 객체를 생성합니다.
        val loginRequest = LoginRequest(email, password)

        // 3. Retrofit을 사용하여 서버에 로그인 요청을 보냅니다.
        ApiClient.api.login(loginRequest).enqueue(object : Callback<LoginResponse> {

            // 4. 서버로부터 응답을 받았을 때 호출됩니다.
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                // HTTP 상태 코드가 2xx (성공)일 경우
                if (response.isSuccessful) {
                    val loginResponse = response.body()

                    if (loginResponse != null && loginResponse.success && loginResponse.token != null && loginResponse.userId != null) {
                        // 5. 로그인 성공: 서버가 보내준 token과 userId를 저장합니다.
                        Toast.makeText(this@LoginActivity, "로그인 성공!", Toast.LENGTH_SHORT).show()

                        SessionManager.saveSession(loginResponse.userId, loginResponse.token)

                        // 닉네임 저장 로직
                        loginResponse.nickname?.let {
                            userStatsPrefs.saveNickname(it)
                        }

                        // 초기화하고 이동
                        val request = OneTimeWorkRequestBuilder<InitialSyncWorker>().build()
                        WorkManager.getInstance(applicationContext).enqueue(request)

                        // 메인 화면으로 이동
                        val intent = Intent(this@LoginActivity, CalendarActivity::class.java)
                        startActivity(intent)
                        finish() // 로그인 화면은 종료
                    } else {
                        // 서버가 success: false 와 같은 실패 응답을 준 경우
                        val message = loginResponse?.message ?: "알 수 없는 오류가 발생했습니다."
                        Toast.makeText(this@LoginActivity, message, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    when (response.code()) {
                        400, 401 -> {
                            Toast.makeText(
                                this@LoginActivity,
                                "아이디 또는 비밀번호가 올바르지 않습니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        else -> {
                            val errorMessage = "로그인 실패 (에러 코드: ${response.code()})"
                            Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            }

            // 6. 네트워크 요청 자체가 실패했을 때 호출됩니다. (인터넷 연결 문제 등)
            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                Log.e("LoginActivity", "네트워크 오류", t)
                Toast.makeText(this@LoginActivity, "네트워크 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        })
    }
}