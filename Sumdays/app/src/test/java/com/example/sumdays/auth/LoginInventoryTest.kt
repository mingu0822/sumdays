package com.example.sumdays.auth

import android.app.Application
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.EditText
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.example.sumdays.CalendarActivity
import com.example.sumdays.LoginActivity
import com.example.sumdays.R
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.LoginResponse
import com.example.sumdays.network.apiService.ApiService
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LoginInventoryTest {
    private lateinit var api: ApiService
    private lateinit var loginCall: Call<LoginResponse>
    private lateinit var workManager: WorkManager

    @Before fun setup() {
        mockkObject(ApiClient, SessionManager)
        mockkStatic(WorkManager::class)
        api = mockk()
        loginCall = mockk()
        workManager = mockk()
        every { ApiClient.api } returns api
        every { api.login(any()) } returns loginCall
        every { WorkManager.getInstance(any<Context>()) } returns workManager
        every { workManager.enqueue(any<WorkRequest>()) } returns mockk()
        every { loginCall.enqueue(any()) } answers {
            arg<Callback<LoginResponse>>(0).onResponse(loginCall, Response.success(
                LoginResponse(true, "ok", 202, "token", "B")
            ))
        }
    }

    @After fun cleanup() { unmockkAll() }

    private fun submit(activity: LoginActivity) {
        activity.findViewById<EditText>(R.id.idInput_EditText).setText("b@example.com")
        activity.findViewById<EditText>(R.id.passwordInput_EditText).setText("password")
        activity.findViewById<View>(R.id.login_button).performClick()
    }

    @Test fun `login clears previous account screens only after session save succeeds`() {
        every { SessionManager.saveSession(202, "token") } returns true
        val controller = Robolectric.buildActivity(LoginActivity::class.java).setup()
        val activity = controller.get()
        submit(activity)
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(CalendarActivity::class.java.name, intent.component!!.className)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK, intent.flags)
        verify(exactly = 1) { workManager.enqueue(any<WorkRequest>()) }
        controller.pause().stop().destroy()
    }

    @Test fun `failed session persistence neither enters B nor downloads B data`() {
        every { SessionManager.saveSession(202, "token") } returns false
        val controller = Robolectric.buildActivity(LoginActivity::class.java).setup()
        val activity = controller.get()
        submit(activity)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertFalse(activity.isFinishing)
        assertTrue(ShadowToast.getTextOfLatestToast().contains("저장하지 못했습니다"))
        verify(exactly = 0) { workManager.enqueue(any<WorkRequest>()) }
        activity.finish()
        controller.pause().stop().destroy()
    }
}
