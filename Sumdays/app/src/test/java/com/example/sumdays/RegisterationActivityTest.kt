package com.example.sumdays

import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.ApiService
import com.example.sumdays.network.SignupResponse
import com.example.sumdays.network.SignupRequest
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34],
    application = TestApplication::class)
class RegisterationActivityTest {

    private lateinit var activity: RegisterationActivity
    private lateinit var apiMock: ApiService
    private lateinit var callMock: Call<SignupResponse>

    @Before
    fun setup() {
        // RetrofitClient.authApiService 모킹
        mockkObject(ApiClient)
        apiMock = mockk()
        callMock = mockk()
        every { ApiClient.api } returns apiMock

        // Robolectric으로 Activity 생성
        activity = Robolectric.buildActivity(RegisterationActivity::class.java)
            .setup()
            .get()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun fillFields(nickname: String?, email: String?, password: String?) {
        val nicknameEt = activity.findViewById<TextInputEditText>(R.id.nickname_input_edit_text)
        val emailEt = activity.findViewById<TextInputEditText>(R.id.email_input_edit_text)
        val passwordEt = activity.findViewById<TextInputEditText>(R.id.password_input_edit_text)
        nicknameEt.setText(nickname ?: "")
        emailEt.setText(email ?: "")
        passwordEt.setText(password ?: "")
    }

    @Test
    fun success_response_shows_toast_and_finishes() {
        // given
        fillFields("Nick", "nick@example.com", "pw1234")
        val signupBtn = activity.findViewById<Button>(R.id.signup_button)

        every { apiMock.signup(any<SignupRequest>()) } returns callMock
        val response = SignupResponse(success = true, message = "OK")
        every { callMock.enqueue(any()) } answers {
            val cb = arg<Callback<SignupResponse>>(0)
            cb.onResponse(callMock, Response.success(response))
        }

        // when
        signupBtn.performClick()

        // then
        val toast = ShadowToast.getTextOfLatestToast()
        assertTrue(toast.contains("회원가입 성공"))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun failure_response_shows_server_message() {
        // given
        fillFields("Nick", "nick@example.com", "pw1234")
        val signupBtn = activity.findViewById<Button>(R.id.signup_button)

        every { apiMock.signup(any()) } returns callMock
        val response = SignupResponse(success = false, message = "이미 존재하는 이메일입니다.")
        every { callMock.enqueue(any()) } answers {
            val cb = arg<Callback<SignupResponse>>(0)
            cb.onResponse(callMock, Response.success(response))
        }

        // when
        signupBtn.performClick()

        // then
        val toast = ShadowToast.getTextOfLatestToast()
        assertTrue(toast.contains("이미 존재하는 이메일"))
        assertFalse(activity.isFinishing)
    }

    @Test
    fun network_failure_shows_error_message() {
        // given
        fillFields("Nick", "nick@example.com", "pw1234")
        val signupBtn = activity.findViewById<Button>(R.id.signup_button)

        every { apiMock.signup(any()) } returns callMock
        every { callMock.enqueue(any()) } answers {
            val cb = arg<Callback<SignupResponse>>(0)
            cb.onFailure(callMock, Throwable("timeout"))
        }

        // when
        signupBtn.performClick()

        // then
        val toast = ShadowToast.getTextOfLatestToast()
        assertTrue(toast.contains("네트워크 오류"))
        assertFalse(activity.isFinishing)
    }

    @Test
    fun empty_fields_show_validation_toast_and_no_api_call() {
        // given
        fillFields("", "", "")
        val signupBtn = activity.findViewById<Button>(R.id.signup_button)

        // when
        signupBtn.performClick()

        // then
        val toast = ShadowToast.getTextOfLatestToast()
        assertTrue(toast.contains("모든 정보를 입력해주세요."))
        verify(exactly = 0) { apiMock.signup(any()) }
    }
}
