package com.example.sumdays.daily.diary

import android.os.Build
import com.example.sumdays.TestApplication
import com.example.sumdays.data.viewModel.DailyEntryViewModel
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.ApiService
import com.google.gson.JsonParser
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE],
    application = TestApplication::class)
class AnalysisRepositoryTest {

    private lateinit var apiMock: ApiService
    private lateinit var viewModel: DailyEntryViewModel

    @Before
    fun setup() {
        mockkObject(ApiClient)
        apiMock = mockk(relaxed = true)
        every { ApiClient.api } returns apiMock
        viewModel = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun requestAnalysis_whenDiaryIsNull_returnsNull_andDoesNotCallApi() = runBlocking {
        val date = "2025-11-15"

        val result = AnalysisRepository.requestAnalysis(
            date = date,
            diary = null,
            viewModel = viewModel
        )

        assertThat(result, `is`(nullValue()))
        coVerify(exactly = 0) { apiMock.diaryAnalyze(any()) }

        verify(exactly = 0) {
            viewModel.updateEntry(
                date = any(),
                diary = any(),
                keywords = any(),
                aiComment = any(),
                emotionScore = any(),
                emotionIcon = any(),
                themeIcon = any()
            )
        }
    }

    @Test
    fun requestAnalysis_success_updatesViewModel_andReturnsParsedResponse() = runBlocking {
        val date = "2025-11-15"
        val diaryText = "오늘은 정말 즐거운 하루였다."

        val jsonStr = """
            {
              "result": {
                "ai_comment": "좋은 하루네요",
                "diary": "$diaryText",
                "entry_date": "$date",
                "icon": "sunny",
                "user_id": 7,
                "analysis": {
                  "emotion_score": 0.88,
                  "keywords": ["행복", "즐거움"]
                }
              }
            }
        """.trimIndent()

        val jsonObj = JsonParser.parseString(jsonStr).asJsonObject

        coEvery { apiMock.diaryAnalyze(any()) } returns Response.success(jsonObj)

        val result = AnalysisRepository.requestAnalysis(
            date = date,
            diary = diaryText,
            viewModel = viewModel
        )

        requireNotNull(result)
        assertThat(result.aiComment, `is`(nullValue()))
        assertThat(result.diary, `is`(diaryText))
        assertThat(result.entryDate, `is`(date))
        assertThat(result.icon, `is`("sunny"))
        assertThat(result.userId, `is`(7))
        assertThat(result.analysis?.emotionScore, `is`(0.88))
        assertThat(result.analysis?.keywords, `is`(listOf("행복", "즐거움")))

        verify(exactly = 1) {
            viewModel.updateEntry(
                date = date,
                diary = diaryText,
                keywords = "행복;즐거움",
                aiComment = null,
                emotionScore = 0.88,
                emotionIcon = null,
                themeIcon = "sunny"
            )
        }
    }

    @Test
    fun requestAnalysis_whenApiThrowsException_returnsNull_andDoesNotUpdateViewModel() = runBlocking {
        val date = "2025-11-15"
        val diaryText = "예외 테스트"

        coEvery { apiMock.diaryAnalyze(any()) } throws RuntimeException("network error")

        val result = AnalysisRepository.requestAnalysis(
            date = date,
            diary = diaryText,
            viewModel = viewModel
        )

        assertThat(result, `is`(nullValue()))

        verify(exactly = 0) {
            viewModel.updateEntry(
                date = any(),
                diary = any(),
                keywords = any(),
                aiComment = any(),
                emotionScore = any(),
                emotionIcon = any(),
                themeIcon = any()
            )
        }
    }

    @Test
    fun requestAnalysis_whenBodyIsNull_returnsNull() = runBlocking {
        val date = "2025-11-15"
        val diaryText = "본문 없음 테스트"

        coEvery { apiMock.diaryAnalyze(any()) } returns Response.success(null)

        val result = AnalysisRepository.requestAnalysis(
            date = date,
            diary = diaryText,
            viewModel = viewModel
        )

        assertThat(result, `is`(nullValue()))

        verify(exactly = 0) {
            viewModel.updateEntry(
                date = any(),
                diary = any(),
                keywords = any(),
                aiComment = any(),
                emotionScore = any(),
                emotionIcon = any(),
                themeIcon = any()
            )
        }
    }
}
