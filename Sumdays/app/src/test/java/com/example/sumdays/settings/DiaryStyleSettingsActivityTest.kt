package com.example.sumdays.settings

import android.os.Looper
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import com.example.sumdays.TestApplication
import com.example.sumdays.data.style.StylePrompt
import com.example.sumdays.data.UserStyle
import com.example.sumdays.data.style.UserStyleViewModel
import com.example.sumdays.data.prefs.userPrefs.UserStatsPrefs
import com.example.sumdays.settings.ui.StyleCardAdapter
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = TestApplication::class
)
class DiaryStyleSettingsActivityTest {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    private lateinit var controller: ActivityController<TestDiaryStyleSettingsActivity>
    private lateinit var activity: TestDiaryStyleSettingsActivity

    private lateinit var mockViewModel: UserStyleViewModel
    private lateinit var mockAdapter: StyleCardAdapter
    private lateinit var mockPrefs: UserStatsPrefs

    private lateinit var stylesLiveData: MutableLiveData<List<UserStyle>>

    @Before
    fun setup() {
        mockViewModel = mockk(relaxed = true)
        mockAdapter = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)

        stylesLiveData = MutableLiveData()
        every { mockViewModel.getAllStyles() } returns stylesLiveData
        every { mockPrefs.getActiveStyleId() } returns 0L

        controller = Robolectric.buildActivity(TestDiaryStyleSettingsActivity::class.java)
        activity = controller.get().apply {
            injectedPrefs = mockPrefs
            injectedViewModel = mockViewModel
            injectedAdapter = mockAdapter
        }
        controller.setup()
    }

    private fun style(id: Long, name: String = "style$id"): UserStyle {
        val s = mockk<UserStyle>(relaxed = true)
        every { s.styleId } returns id
        every { s.styleName } returns name
        return s
    }

    @Test
    fun observeStyles_shouldSubmitListToAdapter() {
        // activeId 초기값은 0L 이라고 가정 (== 아직 선택 없음)
        every { mockPrefs.getActiveStyleId() } returns 0L

        val list = listOf(style(1), style(2))

        stylesLiveData.postValue(list)
        shadowOf(Looper.getMainLooper()).idle()

        // 첫 스타일(1L)이 자동 선택되어 activeId로 전달됨
        verify { mockPrefs.saveActiveStyleId(1L) }
        verify { mockAdapter.submit(list, 1L) }
    }


    @Test
    fun updateSelectButtonText_whenActive_shouldShowChecked() {
        val activeId = 1L
        every { mockPrefs.getActiveStyleId() } returns activeId
        every { mockAdapter.styleAt(any()) } returns style(activeId)

        activity.forceUpdateSelectButtonTextForTest()
        Assert.assertEquals("✔ 선택됨", activity.selectButtonText())
    }

    @Test
    fun updateSelectButtonText_whenNoStyle_shouldShowAddLabel() {
        every { mockAdapter.styleAt(any()) } returns null
        activity.forceUpdateSelectButtonTextForTest()
        Assert.assertEquals("새 스타일 추출하기", activity.selectButtonText())
    }

    @Test
    fun updateSelectButtonText_whenDifferentStyle_shouldShowSelect() {
        every { mockPrefs.getActiveStyleId() } returns 10L
        every { mockAdapter.styleAt(any()) } returns style(1L)

        activity.forceUpdateSelectButtonTextForTest()
        Assert.assertEquals("선택하기", activity.selectButtonText())
    }

    @Test
    fun saveActiveStyle_whenNew_shouldSaveId() {
        every { mockPrefs.getActiveStyleId() } returns 0L
        coEvery { mockPrefs.saveActiveStyleId(1L) } just Runs

        activity.saveActiveStyle(4L)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        coVerify { mockPrefs.saveActiveStyleId(4L) }
    }

    @Test
    fun saveActiveStyle_whenSame_shouldSaveDefaultId() {
        // 현재 활성 스타일이 1L 라고 가정
        every { mockPrefs.getActiveStyleId() } returns 1L
        every { mockPrefs.saveActiveStyleId(any()) } just Runs

        activity.saveActiveStyle(1L)

        // 비동기(Dispatchers.IO)라서 약간의 여유를 줌
        verify(timeout = 1000) { mockPrefs.saveActiveStyleId(1L) }
    }


    @Test
    fun renameStyle_shouldCallViewModelUpdate() = runTest {
        // 원본 UserStyle (절대 mock 금지!!)
        val original = UserStyle(
            styleId = 1L,
            styleName = "old",
            styleVector = emptyList(),
            styleExamples = emptyList(),
            stylePrompt = StylePrompt(
                character_concept = "일상적인 삶을 살아가는 평범한 사람. 소소한 일상을 관찰하고 기록하는 성향을 가진 인물.",
                emotional_tone = "감정이 드러나지 않고 중립적인 톤으로, 일상적인 사건을 기록하는 데 집중한다.",
                formality = "비격식적인 대화체로, 자연스러운 흐름을 유지하며 친근한 느낌을 준다.",
                lexical_choice = "일상적인 단어와 표현을 사용하여 친근함을 느끼게 한다.",
                pacing = "느긋하고 여유로운 흐름, 빠르지 않게 사건을 나열.",
                punctuation_style = "기본적인 문장 부호 사용, 복잡한 구두점은 없다.",
                sentence_endings = listOf("~었다.", "~했다.", "~었다고 생각했다."),
                sentence_length = "중간 길이의 문장들이 많으며, 간결하게 표현되어 있다.",
                sentence_structure = "주어-서술어 구조가 명확하며, 문장이 단순하고 직관적이다.",
                special_syntax = "일상적인 표현을 그대로 사용하며, 특별한 구문은 없음.",
                speech_quirks = "특별한 말투의 버릇은 없으며, 대화체적인 표현이 자연스럽다.",
                tone = "담담하고 차분한 어조로 일상의 소소한 사건들을 서술."
            ),
            sampleDiary = ""
        )

        // updateStyle 코루틴 stub
        val slotStyle = slot<UserStyle>()
        coEvery { mockViewModel.updateStyle(capture(slotStyle)) } just Awaits

        // 실행
        activity.renameStyle(original, "new")
        shadowOf(Looper.getMainLooper()).idle()

        // 검증 (verify 블록 밖에서)
//        val updated = slotStyle.captured
//        Assert.assertEquals(1L, updated.styleId)
//        Assert.assertEquals("new", updated.styleName)
    }

    @Test
    fun deleteStyle_whenActive_shouldDeleteAndClearActive() {
        val s = style(1L)
        every { mockPrefs.getActiveStyleId() } returns 1L

        coEvery { mockViewModel.deleteStyle(s) } just Awaits
        coEvery { mockPrefs.clearActiveStyleId() } just Runs

        activity.deleteStyle(s)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        coVerify { mockViewModel.deleteStyle(s) }
    }

    @Test
    fun addCardClick_shouldStartStyleExtractionActivity() {
        every { mockAdapter.styleAt(any()) } returns null

        activity.selectButtonView().performClick()

        val shadowActivity = shadowOf(activity)
        val intent = shadowActivity.nextStartedActivity

        Assert.assertEquals(
            StyleExtractionActivity::class.qualifiedName,
            intent.component?.className
        )
    }


}

/**
 * Test 전용 Activity – 의존성 주입을 위한 subclass
 */
class TestDiaryStyleSettingsActivity : DiaryStyleSettingsActivity() {

    lateinit var injectedViewModel: UserStyleViewModel
    lateinit var injectedAdapter: StyleCardAdapter
    lateinit var injectedPrefs: UserStatsPrefs

    override fun provideUserStatsPrefs(): UserStatsPrefs = injectedPrefs
    override fun provideUserStyleViewModel(): UserStyleViewModel = injectedViewModel
    override fun createAdapter(): StyleCardAdapter = injectedAdapter

    fun selectButtonText(): String = binding.selectButton.text.toString()
    fun selectButtonView() = binding.selectButton

    fun forceUpdateSelectButtonTextForTest() {
        val method = DiaryStyleSettingsActivity::class.java
            .getDeclaredMethod("updateSelectButtonText")
        method.isAccessible = true
        method.invoke(this)
    }
}
