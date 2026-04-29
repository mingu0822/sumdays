package com.example.sumdays.settings

import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sumdays.TestApplication
import com.example.sumdays.daily.memo.MemoMergeUtils
import com.example.sumdays.data.style.StylePrompt
import com.example.sumdays.data.style.UserStyleViewModel
import com.example.sumdays.network.*
import com.example.sumdays.network.apiService.ApiService
import com.example.sumdays.utils.FileUtil
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28],application = TestApplication::class)
class StyleExtractionActivityTest {

    private val testDispatcher = StandardTestDispatcher()

    @MockK
    private lateinit var mockViewModel: UserStyleViewModel
    @MockK
    private lateinit var mockApiService: ApiService
    @MockK
    private lateinit var mockFile: File
    @MockK
    private lateinit var mockUri: Uri

    private lateinit var scenario: ActivityScenario<StyleExtractionActivity>

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)

        Dispatchers.setMain(testDispatcher)

        // ViewModel mock
        mockkConstructor(ViewModelProvider::class)
        every {
            anyConstructed<ViewModelProvider>().get(UserStyleViewModel::class.java)
        } returns mockViewModel

        // ApiClient mock
        mockkObject(ApiClient)
        every { ApiClient.api } returns mockApiService

        // FileUtil mock
        mockkObject(FileUtil)
        every { FileUtil.getFileFromUri(any(), any()) } returns mockFile
        every { mockFile.exists() } returns false

        // MemoMergeUtils mock
        mockkObject(MemoMergeUtils)

        // ViewModel 기본 동작
        coEvery { mockViewModel.generateNextStyleName() } returns "Test Style"
        coEvery { mockViewModel.insertStyleReturnId(any()) } returns 1L
        coEvery { mockViewModel.updateSampleDiary(any(), any()) } just Awaits
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `onCreate should initialize UI correctly`() {
        scenario = ActivityScenario.launch(StyleExtractionActivity::class.java)

        scenario.onActivity { activity ->
            assertNotNull(activity.binding)
            assertNotNull(activity.binding.runExtractionButton)
            assertNotNull(activity.binding.diaryTextInput)
        }
    }

    @Test
    fun `validation fails when less than 5 items`() {
        scenario = ActivityScenario.launch(StyleExtractionActivity::class.java)

        scenario.onActivity { activity ->
            activity.binding.diaryTextInput.setText("일기1\n일기2")

            activity.binding.runExtractionButton.performClick()

            val toast = ShadowToast.getTextOfLatestToast()
            assertTrue(toast.contains("최소 5개 이상"))
            assertTrue(toast.contains("현재: 2 개"))
        }
    }

    @Test
    fun `handleExtractStyle should disable button during processing`() = runTest {
        val mockCall = mockk<Call<StyleExtractionResponse>>()
        every { mockApiService.extractStyle(any(), any()) } returns mockCall
        every { mockCall.enqueue(any()) } just Runs

        scenario = ActivityScenario.launch(StyleExtractionActivity::class.java)

        scenario.onActivity { activity ->
            activity.binding.diaryTextInput.setText("1\n2\n3\n4\n5")
            activity.binding.runExtractionButton.performClick()

            testScheduler.advanceUntilIdle()

            assertFalse(activity.binding.runExtractionButton.isEnabled)
            assertEquals("스타일 분석 중...", activity.binding.runExtractionButton.text)
        }
    }

    @Test
    fun `API success with null style_vector should show error`() = runTest {
        val callbackSlot = slot<Callback<StyleExtractionResponse>>()
        val mockCall = mockk<Call<StyleExtractionResponse>>()
        every { mockApiService.extractStyle(any(), any()) } returns mockCall
        every { mockCall.enqueue(capture(callbackSlot)) } just Runs

        val mockStylePrompt = mockk<StylePrompt>(relaxed = true)

        scenario = ActivityScenario.launch(StyleExtractionActivity::class.java)

        scenario.onActivity { activity ->
            activity.binding.diaryTextInput.setText("1\n2\n3\n4\n5")
            activity.binding.runExtractionButton.performClick()
            shadowOf(Looper.getMainLooper()).idle()
        }

        testScheduler.advanceUntilIdle()
        verify(timeout = 1000) { mockCall.enqueue(any()) }

        // null vector 응답
        val errorResponse = StyleExtractionResponse(
            style_vector = null,
            style_examples = listOf("예시1"),
            style_prompt = mockStylePrompt,
            message = "데이터 불완전"
        )
        callbackSlot.captured.onResponse(mockCall, Response.success(errorResponse))

        shadowOf(Looper.getMainLooper()).idle()
        testScheduler.advanceUntilIdle()

        scenario.onActivity { activity ->
            val toast = ShadowToast.getTextOfLatestToast()
            assertTrue(toast.contains("데이터 불완전") || toast.contains("데이터가 서버로부터"))
            assertTrue(activity.binding.runExtractionButton.isEnabled)
        }
    }

    @Test
    fun `API HTTP error should show error toast`() = runTest {
        val callbackSlot = slot<Callback<StyleExtractionResponse>>()
        val mockCall = mockk<Call<StyleExtractionResponse>>()
        every { mockApiService.extractStyle(any(), any()) } returns mockCall
        every { mockCall.enqueue(capture(callbackSlot)) } just Runs

        scenario = ActivityScenario.launch(StyleExtractionActivity::class.java)

        scenario.onActivity { activity ->
            activity.binding.diaryTextInput.setText("1\n2\n3\n4\n5")
            activity.binding.runExtractionButton.performClick()
            shadowOf(Looper.getMainLooper()).idle()
        }

        testScheduler.advanceUntilIdle()
        verify(timeout = 1000) { mockCall.enqueue(any()) }

        // HTTP 500 에러
        val errorResponse = Response.error<StyleExtractionResponse>(
            500,
            "".toResponseBody(null)
        )
        callbackSlot.captured.onResponse(mockCall, errorResponse)

        shadowOf(Looper.getMainLooper()).idle()
        testScheduler.advanceUntilIdle()

        scenario.onActivity {
            val toast = ShadowToast.getTextOfLatestToast()
            assertTrue(toast.contains("서버 응답 오류"))
            assertTrue(toast.contains("500"))
        }
    }

    @Test
    fun `network failure should show error toast`() = runTest {
        val callbackSlot = slot<Callback<StyleExtractionResponse>>()
        val mockCall = mockk<Call<StyleExtractionResponse>>()
        every { mockApiService.extractStyle(any(), any()) } returns mockCall
        every { mockCall.enqueue(capture(callbackSlot)) } just Runs

        scenario = ActivityScenario.launch(StyleExtractionActivity::class.java)

        scenario.onActivity { activity ->
            activity.binding.diaryTextInput.setText("1\n2\n3\n4\n5")
            activity.binding.runExtractionButton.performClick()
            shadowOf(Looper.getMainLooper()).idle()
        }

        testScheduler.advanceUntilIdle()
        verify(timeout = 1000) { mockCall.enqueue(any()) }

        // 네트워크 실패
        callbackSlot.captured.onFailure(mockCall, Exception("Network error"))

        shadowOf(Looper.getMainLooper()).idle()
        testScheduler.advanceUntilIdle()

        scenario.onActivity {
            val toast = ShadowToast.getTextOfLatestToast()
            assertTrue(toast.contains("네트워크 오류"))
            assertTrue(toast.contains("Network error"))
        }
    }

    @Test
    fun `createTextPart should create RequestBody from list`() {
        scenario = ActivityScenario.launch(StyleExtractionActivity::class.java)

        scenario.onActivity { activity ->
            val diaries = listOf("일기1", "일기2", "일기3")

            val method = StyleExtractionActivity::class.java.getDeclaredMethod(
                "createTextPart",
                List::class.java
            )
            method.isAccessible = true
            val result = method.invoke(activity, diaries)

            assertNotNull(result)
        }
    }

    @Test
    fun `createImageParts should filter invalid URIs`() {
        every { FileUtil.getFileFromUri(any(), any()) } returns null

        scenario = ActivityScenario.launch(StyleExtractionActivity::class.java)

        scenario.onActivity { activity ->
            val uris = listOf(mockUri, mockUri)

            val method = StyleExtractionActivity::class.java.getDeclaredMethod(
                "createImageParts",
                List::class.java
            )
            method.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val result = method.invoke(activity, uris) as List<*>

            assertTrue(result.isEmpty())
        }
    }

    @Test
    fun `resetUi should enable button and restore text`() {
        scenario = ActivityScenario.launch(StyleExtractionActivity::class.java)

        scenario.onActivity { activity ->
            activity.binding.runExtractionButton.isEnabled = false
            activity.binding.runExtractionButton.text = "처리 중..."

            val method = StyleExtractionActivity::class.java.getDeclaredMethod("resetUi")
            method.isAccessible = true
            method.invoke(activity)

            assertTrue(activity.binding.runExtractionButton.isEnabled)
            assertEquals("스타일 추출 실행", activity.binding.runExtractionButton.text)
        }
    }

    @Test
    fun `updateImageCountUi should display correct count`() {
        scenario = ActivityScenario.launch(StyleExtractionActivity::class.java)

        scenario.onActivity { activity ->
            val field = StyleExtractionActivity::class.java.getDeclaredField("selectedImageUris")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val urisList = field.get(activity) as MutableList<Uri>

            urisList.add(mockUri)
            urisList.add(mockUri)
            urisList.add(mockUri)

            val method = StyleExtractionActivity::class.java.getDeclaredMethod("updateImageCountUi")
            method.isAccessible = true
            method.invoke(activity)

            assertEquals("선택된 이미지: 0개", activity.binding.selectedImageCount.text)
        }
    }

    
}