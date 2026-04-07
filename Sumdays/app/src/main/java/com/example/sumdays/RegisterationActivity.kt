package com.example.sumdays

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.sumdays.databinding.ActivityRegisterationBinding
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.SignupRequest
import com.example.sumdays.network.SignupResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RegisterationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding = ActivityRegisterationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.signupButton.setOnClickListener {
            handleSignup()
        }
    }

    private fun handleSignup() {
        val nickname = binding.nicknameInputEditText.text.toString().trim()
        val email = binding.emailInputEditText.text.toString().trim()
        val password = binding.passwordInputEditText.text.toString().trim()

        if (nickname.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val signupRequest = SignupRequest(nickname, email, password)

        ApiClient.api.signup(signupRequest).enqueue(object : Callback<SignupResponse> {
            override fun onResponse(call: Call<SignupResponse>, response: Response<SignupResponse>) {
                if (response.isSuccessful) {
                    val signupResponse = response.body()
                    if (signupResponse != null && signupResponse.success) {
                        Toast.makeText(this@RegisterationActivity, "회원가입 성공! 로그인 해주세요.", Toast.LENGTH_LONG).show()
                        // 회원가입 성공 시 현재 액티비티 종료 (로그인 화면으로 돌아감)
                        finish()
                    } else {
                        val message = signupResponse?.message ?: "회원가입에 실패했습니다."
                        Toast.makeText(this@RegisterationActivity, message, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@RegisterationActivity, "서버 응답 오류 (코드: ${response.code()})", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<SignupResponse>, t: Throwable) {
                Toast.makeText(this@RegisterationActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}