package com.example.sumdays

import android.content.Intent
import android.os.Build
import android.os.Looper
import android.widget.*
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.daily.memo.Memo
import com.example.sumdays.daily.memo.MemoAdapter
import com.example.sumdays.daily.memo.MemoViewModel
import com.example.sumdays.daily.memo.MemoViewModelFactory
import com.example.sumdays.audio.AudioRecorderHelper
import io.mockk.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.ShadowApplication
import java.time.LocalDate
import androidx.appcompat.app.AlertDialog
import android.content.DialogInterface
import org.robolectric.shadows.ShadowDialog
import android.view.View

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [Build.VERSION_CODES.O],
    application = TestApplication::class,
    packageName = "com.example.sumdays"
)
class DailyWriteActivityTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var activity: DailyWriteActivity
    private lateinit var shadowApplication: ShadowApplication

    private val mockMemoViewModel: MemoViewModel = mockk(relaxed = true)
    private val mockMemoListLiveData: MutableLiveData<List<Memo>> = MutableLiveData()
    private val mockAudioRecorderHelper: AudioRecorderHelper = mockk(relaxUnitFun = true)

    private lateinit var memoInputEditText: EditText
    private lateinit var sendIcon: ImageView
    private lateinit var micIcon: ImageView
    private lateinit var dateTextView: TextView
    private lateinit var readDiaryButton: Button
    private lateinit var memoListView: RecyclerView
    private lateinit var controller: org.robolectric.android.controller.ActivityController<DailyWriteActivity>

    @Before
    fun setUp() {
        mockkConstructor(MemoViewModelFactory::class)
        every { anyConstructed<MemoViewModelFactory>().create(MemoViewModel::class.java) } returns mockMemoViewModel
        every { mockMemoViewModel.getMemosForDate(any()) } returns mockMemoListLiveData

        mockkConstructor(AudioRecorderHelper::class)
        every { anyConstructed<AudioRecorderHelper>().checkPermissionAndToggleRecording() } just Runs
        every { anyConstructed<AudioRecorderHelper>().release() } just Runs

        val intent = Intent(Intent.ACTION_MAIN).apply {
            putExtra("date", LocalDate.now().toString())
        }
        controller = Robolectric.buildActivity(DailyWriteActivity::class.java, intent)
        activity = controller.create().get()
        shadowApplication = Shadows.shadowOf(activity.application)

        memoInputEditText = activity.findViewById(R.id.memo_input_edittext)
        sendIcon = activity.findViewById(R.id.send_icon)
        micIcon = activity.findViewById(R.id.mic_icon)
        dateTextView = activity.findViewById(R.id.date_text_view)
        readDiaryButton = activity.findViewById(R.id.read_diary_button)
        memoListView = activity.findViewById(R.id.memo_list_view)

        controller.start().resume()
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized) {
            try {
                controller.pause().stop().destroy()
            } catch (e: Exception) {
            }
        }
        unmockkAll()
    }

    @Test
    fun `testActivitySetup_withIntentDate`() {
        val testDate = "2025-10-25"
        val intent = Intent(Intent.ACTION_MAIN).apply {
            putExtra("date", testDate)
        }
        val newActivity = Robolectric.buildActivity(DailyWriteActivity::class.java, intent).setup().get()
        val dateText: TextView = newActivity.findViewById(R.id.date_text_view)

        assertEquals(testDate, dateText.text.toString())
        verify(exactly = 1) { mockMemoViewModel.getMemosForDate(testDate) }
    }

    @Test
    fun `testActivitySetup_noIntentDate_usesToday`() {
        val today = LocalDate.now().toString()
        assertEquals(today, dateTextView.text.toString())
        verify(exactly = 1) { mockMemoViewModel.getMemosForDate(today) }
    }

    @Test
    fun `testActivitySetup_audioHelperRelease`() {
        controller.destroy()
        verify(exactly = 1) { anyConstructed<AudioRecorderHelper>().release() }
    }

    @Test
    fun `testMemoListUpdate_submitsToAdapter`() {
        val memo1 = Memo(1, "테스트 메모 1", "09:00", "2025-10-25", 0)
        val memo2 = Memo(2, "테스트 메모 2", "10:00", "2025-10-25", 1)
        val testList = listOf(memo1, memo2)

        mockMemoListLiveData.postValue(testList)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val adapter = memoListView.adapter as MemoAdapter
        assertEquals(2, adapter.itemCount)
        assertEquals(memo1.content, adapter.currentList[0].content)
        assertEquals(memo2.content, adapter.currentList[1].content)
    }

    @Test
    fun `testSendIcon_validMemo_insertsAndClears`() {
        val memoContent = "새로운 일기 메모"
        val today = LocalDate.now().toString()
        val adapter = memoListView.adapter as MemoAdapter
        adapter.submitList(emptyList())

        memoInputEditText.setText(memoContent)
        sendIcon.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) {
            mockMemoViewModel.insert(match {
                it.content == memoContent && it.date == today && it.order == 0
            })
        }
        assertEquals("", memoInputEditText.text.toString())
    }

    @Test
    fun `testSendIcon_emptyMemo_showsToast`() {
        memoInputEditText.setText("")
        sendIcon.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 0) { mockMemoViewModel.insert(any()) }
        val latestToast = ShadowToast.getTextOfLatestToast()
        assertEquals("메모 내용을 입력해 주세요.", latestToast)
    }

    @Test
    fun `testMicIconClick_callsHelper`() {
        micIcon.performClick()
        verify(exactly = 1) { anyConstructed<AudioRecorderHelper>().checkPermissionAndToggleRecording() }
    }

    @Test
    fun `testReadDiaryButtonClick_startsDailyReadActivity`() {
        val today = LocalDate.now().toString()
        readDiaryButton.isEnabled = true
        readDiaryButton.performClick()

        val actual = shadowApplication.nextStartedActivity
        assertNotNull(actual)
        assertEquals(DailyReadActivity::class.java.name, actual.component?.className)
        assertEquals(today, actual.getStringExtra("date"))
    }

    @Test
    fun `testBtnSumClick_startsDailySumActivityWithMemos`() {
        val memo1 = Memo(1, "요약할 메모", "10:00", LocalDate.now().toString(), 0)
        val testList = listOf(memo1)
        mockMemoListLiveData.postValue(testList)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val btnSum: ImageButton = activity.findViewById(R.id.btnSum)
        btnSum.performClick()

        val actual = shadowApplication.nextStartedActivity
        assertNotNull(actual)
        assertEquals(DailySumActivity::class.java.name, actual.component?.className)
        assertEquals(LocalDate.now().toString(), actual.getStringExtra("date"))

        val receivedMemos = actual.getParcelableArrayListExtra<Memo>("memo_list")
        assertNotNull(receivedMemos)
        assertEquals(1, receivedMemos?.size)
        assertEquals(memo1.content, receivedMemos?.get(0)?.content)
    }

    @Test
    fun `testBtnInfoClick_startsSettingsActivity`() {
        val btnInfo: ImageButton = activity.findViewById(R.id.btnInfo)
        btnInfo.performClick()

        val actual = shadowApplication.nextStartedActivity
        assertNotNull(actual)
        assertEquals(ProfileActivity::class.java.name, actual.component?.className)
    }

    @Test
    fun `testBtnCalendarClick_startsCalendarActivity`() {
        val btnCalendar: ImageButton = activity.findViewById(R.id.btnCalendar)
        btnCalendar.performClick()

        val actual = shadowApplication.nextStartedActivity
        assertNotNull(actual)
        assertEquals(CalendarActivity::class.java.name, actual.component?.className)
    }

    @Test
    fun `testShowEditMemoDialog_positiveButton_updatesMemo`() {
        val originalMemo = Memo(id = 1, content = "수정 전", timestamp = "09:00", date = LocalDate.now().toString(), order = 0)
        val newContent = "수정 후 내용"

        controller.start().resume()
        activity.showEditMemoDialog(originalMemo)

        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)

        val alertDialog = dialog as androidx.appcompat.app.AlertDialog
        val editText = alertDialog.findViewById<EditText>(R.id.edit_text_memo_content)
        editText!!.setText(newContent)

        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) {
            mockMemoViewModel.update(match {
                it.content == newContent && it.id == originalMemo.id
            })
        }
    }

    @Test
    fun `testShowEditMemoDialog_negativeButton_deletesMemo`() {
        val originalMemo = Memo(id = 1, content = "수정 전", timestamp = "09:00", date = LocalDate.now().toString(), order = 0)
        controller.start().resume()

        activity.showEditMemoDialog(originalMemo)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowDialog.getLatestDialog()
        val alertDialog = dialog as AlertDialog

        alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 0) { mockMemoViewModel.update(any()) }
        verify(exactly = 1) { mockMemoViewModel.delete(originalMemo) }
        assertFalse(alertDialog.isShowing)
    }

    @Test
    fun `testShowEditMemoDialog_neutralButton_cancels`() {
        val originalMemo = Memo(id = 1, content = "수정 전", timestamp = "09:00", date = LocalDate.now().toString(), order = 0)
        controller.start().resume()

        activity.showEditMemoDialog(originalMemo)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowDialog.getLatestDialog()
        val alertDialog = dialog as AlertDialog

        alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 0) { mockMemoViewModel.update(any()) }
        verify(exactly = 0) { mockMemoViewModel.delete(any()) }
        assertFalse(alertDialog.isShowing)
    }

    @Test
    fun `onNewIntent_updatesDate_and_reobserves`() {
        val today = LocalDate.now().toString()
        val newIntent = Intent(Intent.ACTION_MAIN).apply { putExtra("date", today) }
        controller.newIntent(newIntent)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val dateText: TextView = activity.findViewById(R.id.date_text_view)
        assertEquals(today, dateText.text.toString())
        verify(atLeast = 1) { mockMemoViewModel.getMemosForDate(today) }
    }

    @Test
    fun `waveAnimation_start_and_stop_resetScale_and_clearAnimator`() {
        val start = DailyWriteActivity::class.java.getDeclaredMethod("startWaveAnimation").apply { isAccessible = true }
        val stop  = DailyWriteActivity::class.java.getDeclaredMethod("stopWaveAnimation").apply { isAccessible = true }

        val bar1: View = activity.findViewById(R.id.wave_bar_1)
        val bar2: View = activity.findViewById(R.id.wave_bar_2)
        val bar3: View = activity.findViewById(R.id.wave_bar_3)

        start.invoke(activity)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val field = DailyWriteActivity::class.java.getDeclaredField("waveAnimatorSet").apply { isAccessible = true }
        assertNotNull(field.get(activity))

        stop.invoke(activity)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1.0f, bar1.scaleY)
        assertEquals(1.0f, bar2.scaleY)
        assertEquals(1.0f, bar3.scaleY)
        assertNull(field.get(activity))
    }

    @Test
    fun `removeDummyMemoAndAddFinal_replacesDummy_and_insertsFinal`() {
        val pendingId = 777
        val pendingField = DailyWriteActivity::class.java.getDeclaredField("pendingAudioMemoId").apply { isAccessible = true }
        pendingField.set(activity, pendingId)

        val adapter = activity.findViewById<RecyclerView>(R.id.memo_list_view).adapter as MemoAdapter
        val dummy = Memo(id = pendingId, content = "음성 인식 중...", timestamp = "11:11", date = LocalDate.now().toString(), order = 0, type = "audio")
        adapter.submitList(listOf(dummy))
        assertEquals(1, adapter.itemCount)

        val method = DailyWriteActivity::class.java.getDeclaredMethod("removeDummyMemoAndAddFinal", String::class.java, String::class.java).apply { isAccessible = true }
        val finalText = "최종 변환 텍스트"
        val finalType = "audio"

        method.invoke(activity, finalText, finalType)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertTrue(adapter.currentList.none { it.id == pendingId })
        verify(exactly = 1) { mockMemoViewModel.insert(match { it.content == finalText && it.type == finalType }) }
        assertNull(pendingField.get(activity))
    }

    @Test
    fun `handleIntent_keepsAdapterList_when_pending_not_in_livedata`() {
        val pendingId = 999
        val pendingField = DailyWriteActivity::class.java.getDeclaredField("pendingAudioMemoId").apply { isAccessible = true }
        pendingField.set(activity, pendingId)

        val adapter = activity.findViewById<RecyclerView>(R.id.memo_list_view).adapter as MemoAdapter
        val dummy = Memo(id = pendingId, content = "임시", timestamp = "12:34", date = LocalDate.now().toString(), order = 0)
        adapter.submitList(listOf(dummy))
        assertEquals(1, adapter.itemCount)

        val liveDataList = listOf(
            Memo(id = 1, content = "DB의 메모", timestamp = "13:00", date = LocalDate.now().toString(), order = 0)
        )
        mockMemoListLiveData.postValue(liveDataList)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, adapter.itemCount)
        assertEquals(pendingId, adapter.currentList.first().id)
    }

    @Test
    fun `stopIconClick_callsRecorderHelper`() {
        val stopIcon: ImageView = activity.findViewById(R.id.stop_icon)
        stopIcon.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        verify(exactly = 1) {
            anyConstructed<AudioRecorderHelper>().checkPermissionAndToggleRecording()
        }
    }

    @Test
    fun `memoInput_focusGain_hidesStopIcon`() {
        val stopIcon: ImageView = activity.findViewById(R.id.stop_icon)
        stopIcon.visibility = View.VISIBLE

        val listener = memoInputEditText.onFocusChangeListener
        listener!!.onFocusChange(memoInputEditText, true)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, stopIcon.visibility)
    }

    @Test
    fun `memoInput_focusLoss_notRecording_showsMic_andHidesStopIcon`() {
        val stopIcon: ImageView = activity.findViewById(R.id.stop_icon)
        val micIconLocal: ImageView = activity.findViewById(R.id.mic_icon)

        val recField = DailyWriteActivity::class.java
            .getDeclaredField("isRecording")
            .apply { isAccessible = true }
        recField.setBoolean(activity, false)

        val listener = memoInputEditText.onFocusChangeListener
        listener!!.onFocusChange(memoInputEditText, false)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, micIconLocal.visibility)
        assertEquals(View.GONE, stopIcon.visibility)
    }

    @Test
    fun `memoInput_focusLoss_recording_showsStop_andHidesMic`() {
        val stopIcon: ImageView = activity.findViewById(R.id.stop_icon)
        val micIconLocal: ImageView = activity.findViewById(R.id.mic_icon)

        val recField = DailyWriteActivity::class.java
            .getDeclaredField("isRecording")
            .apply { isAccessible = true }
        recField.setBoolean(activity, true)

        val listener = memoInputEditText.onFocusChangeListener
        listener!!.onFocusChange(memoInputEditText, false)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, stopIcon.visibility)
        assertEquals(View.GONE, micIconLocal.visibility)
    }

    @Test
    fun `removeDummyMemo_clearsPendingDummyId_withoutCrash`() {
        val pendingId = 1234
        val pendingField = DailyWriteActivity::class.java
            .getDeclaredField("pendingAudioMemoId")
            .apply { isAccessible = true }
        pendingField.set(activity, pendingId)

        val adapter = activity.findViewById<RecyclerView>(R.id.memo_list_view).adapter as MemoAdapter
        val dummy = Memo(
            id = pendingId,
            content = "임시 음성 메모",
            timestamp = "11:11",
            date = LocalDate.now().toString(),
            order = 0,
            type = "audio"
        )
        adapter.submitList(listOf(dummy))
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val method = DailyWriteActivity::class.java
            .getDeclaredMethod("removeDummyMemo", String::class.java, String::class.java)
            .apply { isAccessible = true }

        method.invoke(activity, "[오류: 테스트]", "audio")
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNull(pendingField.get(activity))
    }

    @Test
    fun `micIconClick_whenApiProcessing_showsToast_andDoesNotCallRecorder`() {
        val apiField = DailyWriteActivity::class.java
            .getDeclaredField("isApiProcessingAudio")
            .apply { isAccessible = true }
        apiField.setBoolean(activity, true)

        micIcon.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals("이전 음성을 처리 중입니다...", ShadowToast.getTextOfLatestToast())

        verify(exactly = 0) {
            anyConstructed<AudioRecorderHelper>().checkPermissionAndToggleRecording()
        }
    }
}
