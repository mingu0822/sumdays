package com.example.sumdays

import android.content.Context
import android.content.Intent
import android.widget.Button // ImageButton -> Button 변경
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.ApiService
import com.example.sumdays.network.LoginResponse
import com.example.sumdays.settings.prefs.UserStatsPrefs
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestApplication::class)
class LoginActivityTest {

    private lateinit var activity: LoginActivity
    private lateinit var apiMock: ApiService
    private lateinit var callMock: Call<LoginResponse>
    private lateinit var mockUserStatsPrefs: UserStatsPrefs
    private lateinit var mockWorkManager: WorkManager

    @Before
    fun setup() {
        // Static Mocking
        mockkObject(ApiClient)
        mockkObject(SessionManager)
        mockkStatic(WorkManager::class)

        apiMock = mockk()
        callMock = mockk()
        mockWorkManager = mockk()
        mockUserStatsPrefs = mockk(relaxed = true)

        // 동작 정의
        every { ApiClient.api } returns apiMock
        every { SessionManager.saveSession(any(), any()) } just Runs

        // WorkManager.getInstance()가 Mock을 반환하도록 설정
        every { WorkManager.getInstance(any<Context>()) } returns mockWorkManager
        every { mockWorkManager.enqueue(any<WorkRequest>()) } returns mockk<Operation>()

        // Activity 생성
        val controller = Robolectric.buildActivity(LoginActivity::class.java)
        controller.create() // onCreate 실행
        activity = controller.get()

        // private val userStatsPrefs 필드를 Mock 객체로 교체
        val field = LoginActivity::class.java.getDeclaredField("userStatsPrefs")
        field.isAccessible = true
        field.set(activity, mockUserStatsPrefs)

        controller.start().resume().visible()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * 로그인 성공 시 CalendarActivity로 이동하는지 테스트
     */
    @Test
    fun success_response_triggers_navigation_to_CalendarActivity() {
        val emailField = activity.findViewById<EditText>(R.id.idInput_EditText) ?: activity.findViewById(R.id.idInput_EditText)
        val passwordField = activity.findViewById<EditText>(R.id.passwordInput_EditText) ?: activity.findViewById(R.id.password_input_edit_text)
        val loginButton = activity.findViewById<Button>(R.id.login_button)

        // 입력 설정
        emailField.setText("user@example.com")
        passwordField.setText("password123")

        // Mock Retrofit call
        every { apiMock.login(any()) } returns callMock

        val response = LoginResponse(
            success = true,
            message = "Login success",
            userId = 1,
            token = "mockToken",
            nickname = "mockNickname"
        )

        every { callMock.enqueue(any()) } answers {
            val cb = arg<Callback<LoginResponse>>(0)
            cb.onResponse(callMock, Response.success(response))
        }

        // 버튼 클릭 실행
        loginButton.performClick()

        val shadowActivity = Shadows.shadowOf(activity)
        val startedIntent: Intent = shadowActivity.nextStartedActivity

        assertNotNull("로그인 성공 후 이동할 Intent가 null입니다.", startedIntent)
        assertEquals(CalendarActivity::class.java.name, startedIntent.component?.className)

        // Session 저장 검증
        verify { SessionManager.saveSession(any(), "mockToken") }
        // 닉네임 저장 검증
        verify { mockUserStatsPrefs.saveNickname("mockNickname") }
        // WorkManager 실행 검증
        verify { mockWorkManager.enqueue(any<WorkRequest>()) }
    }

    /**
     * 로그인 실패 (서버 success=false) 시 Toast 메시지 표시
     */
    @Test
    fun failure_response_triggers_toast() {
        val emailField = activity.findViewById<EditText>(R.id.idInput_EditText) ?: activity.findViewById(R.id.idInput_EditText)
        val passwordField = activity.findViewById<EditText>(R.id.passwordInput_EditText) ?: activity.findViewById(R.id.password_input_edit_text)
        val loginButton = activity.findViewById<Button>(R.id.login_button)

        emailField.setText("wrong@example.com")
        passwordField.setText("badpass")

        every { apiMock.login(any()) } returns callMock

        val response = LoginResponse(
            success = false,
            message = "Invalid credentials",
            userId = null,
            token = null,
            nickname = "invalid"
        )

        every { callMock.enqueue(any()) } answers {
            val cb = arg<Callback<LoginResponse>>(0)
            cb.onResponse(callMock, Response.success(response))
        }

        loginButton.performClick()

        val toast = ShadowToast.getTextOfLatestToast()
        assertNotNull("Toast 메시지가 표시되지 않았습니다.", toast)
        assertTrue(toast.contains("Invalid credentials"))
    }

    /**
     * 네트워크 실패 시 Toast 메시지 표시
     */
    @Test
    fun network_failure_triggers_toast() {
        val emailField = activity.findViewById<EditText>(R.id.idInput_EditText) ?: activity.findViewById(R.id.idInput_EditText)
        val passwordField = activity.findViewById<EditText>(R.id.passwordInput_EditText) ?: activity.findViewById(R.id.password_input_edit_text)
        val loginButton = activity.findViewById<Button>(R.id.login_button) // ImageButton -> Button

        emailField.setText("user@example.com")
        passwordField.setText("password123")

        every { apiMock.login(any()) } returns callMock

        every { callMock.enqueue(any()) } answers {
            val cb = arg<Callback<LoginResponse>>(0)
            cb.onFailure(callMock, Throwable("Network error"))
        }

        loginButton.performClick()

        val toast = ShadowToast.getTextOfLatestToast()
        assertNotNull("Toast 메시지가 표시되지 않았습니다.", toast)
        assertTrue(toast.contains("네트워크 오류"))
    }
}