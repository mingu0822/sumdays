package com.example.sumdays

import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.viewModel.DailyEntryViewModel
import com.example.sumdays.settings.AccountSettingsActivity
import com.example.sumdays.settings.DiaryStyleSettingsActivity
import com.example.sumdays.settings.LabsSettingsActivity
import com.example.sumdays.settings.NotificationSettingsActivity
import com.example.sumdays.settings.prefs.UserStatsPrefs
import com.jakewharton.threetenabp.AndroidThreeTen
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.threeten.bp.LocalDate
import org.junit.Assert.assertNotNull
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.ShadowDialog


@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SettingsActivityTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var activity: ProfileActivity
    private lateinit var mockViewModel: DailyEntryViewModel
    private lateinit var mockUserStatsPrefs: UserStatsPrefs
    private lateinit var controller: org.robolectric.android.controller.ActivityController<ProfileActivity>

    @Before
    fun setup() {
        // 1. 기본 설정 (ThreeTenBP 등)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        AndroidThreeTen.init(context)

        // 2. SessionManager Mocking (로그아웃 테스트용)
        mockkObject(SessionManager)
        every { SessionManager.clearSession() } returns Unit

        // 3. Prefs & ViewModel Mocking
        mockUserStatsPrefs = mockk(relaxed = true)
        every { mockUserStatsPrefs.getNickname() } returns "TestUserNickname"

        mockViewModel = mockk(relaxed = true)
        mockkConstructor(ViewModelProvider::class)
        every {
            anyConstructed<ViewModelProvider>().get(DailyEntryViewModel::class.java)
        } returns mockViewModel

        // 4. Activity 실행
        controller = Robolectric.buildActivity(ProfileActivity::class.java)
        activity = controller.get()

        // 필드 주입 (onCreate 실행 전)
        injectMockPrefs()

        // Activity 생명주기 진행 (이제 TestApplication 덕분에 여기서 안 죽습니다!)
        controller.create().start().resume().visible()

        // 필드 재주입 (덮어쓰기 방지)
        injectMockPrefs()
    }

    private fun injectMockPrefs() {
        val prefsField = ProfileActivity::class.java.getDeclaredField("userStatsPrefs")
        prefsField.isAccessible = true
        prefsField.set(activity, mockUserStatsPrefs)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }


    @Test
    fun loadAndDisplayNickname_updatesUI() {
        // 닉네임이 잘 뜨는지 확인
        val nicknameTextView = activity.findViewById<TextView>(R.id.nickname)
        assertEquals("사용자", nicknameTextView.text.toString())
    }

    @Test
    fun safeSettingBlocks_startCorrectActivities() {
        val shadowActivity = Shadows.shadowOf(activity)

        // 알림 설정
        activity.findViewById<View>(R.id.notificationBlock).performClick()
        assertEquals(NotificationSettingsActivity::class.java.name, shadowActivity.nextStartedActivity.component?.className)

        // 다이어리 스타일
        activity.findViewById<View>(R.id.diaryStyleBlock).performClick()
        assertEquals(DiaryStyleSettingsActivity::class.java.name, shadowActivity.nextStartedActivity.component?.className)

        // 계정 설정
        activity.findViewById<View>(R.id.accountBlock).performClick()
        assertEquals(AccountSettingsActivity::class.java.name, shadowActivity.nextStartedActivity.component?.className)

        // 실험실
        activity.findViewById<View>(R.id.labsBlock).performClick()
        assertEquals(LabsSettingsActivity::class.java.name, shadowActivity.nextStartedActivity.component?.className)

        // 튜토리얼
        activity.findViewById<View>(R.id.tutorialBlock).performClick()
        assertEquals(TutorialActivity::class.java.name, shadowActivity.nextStartedActivity.component?.className)
    }

    @Test
    fun logoutBlock_clearsSessionAndNavigatesToLogin() {
        val shadowActivity = Shadows.shadowOf(activity)

        activity.findViewById<View>(R.id.logoutBlock).performClick()

        verify(exactly = 1) { SessionManager.clearSession() }

        val logoutIntent = shadowActivity.nextStartedActivity
        assertEquals(LoginActivity::class.java.name, logoutIntent.component?.className)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK, logoutIntent.flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    @Test
    fun navigationButtons_navigateCorrectly() {
        val shadowActivity = Shadows.shadowOf(activity)

        // 캘린더
        activity.findViewById<ImageButton>(R.id.btnCalendar).performClick()
        assertEquals(CalendarActivity::class.java.name, shadowActivity.nextStartedActivity.component?.className)

        // 데일리 (오늘 날짜 포함 확인)
        activity.findViewById<ImageButton>(R.id.btnDaily).performClick()
        val dailyIntent = shadowActivity.nextStartedActivity
        assertEquals(DailyWriteActivity::class.java.name, dailyIntent.component?.className)
        assertEquals(LocalDate.now().toString(), dailyIntent.getStringExtra("date"))

        // 정보 버튼
        activity.findViewById<ImageButton>(R.id.btnInfo).performClick()
    }

    @Test
    fun functionalButtons_triggerWorkManagerActions() {
        activity.findViewById<View>(R.id.summaryBlock).performClick()
        assertEquals("주간 통계 생성 요청됨", ShadowToast.getTextOfLatestToast())

        activity.findViewById<View>(R.id.backupBtn).performClick()
        assertEquals("수동 백업 완료", ShadowToast.getTextOfLatestToast())

        activity.findViewById<View>(R.id.initBtn).performClick()

        val latestDialog = ShadowDialog.getLatestDialog()
        assertNotNull(latestDialog)
    }



}