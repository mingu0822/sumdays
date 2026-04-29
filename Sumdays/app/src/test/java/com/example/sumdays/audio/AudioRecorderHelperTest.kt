package com.example.sumdays.audio

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioRecord
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.sumdays.TestApplication
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.ApiService
import com.example.sumdays.network.STTResponse
import io.mockk.*
import okhttp3.MultipartBody
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Config(sdk = [34],
    application = TestApplication::class)
@RunWith(RobolectricTestRunner::class)
class AudioRecorderHelperTest {

    private lateinit var apiMock: ApiService

    @Before
    fun setup() {
        apiMock = mockk<ApiService>(relaxed = true)

        mockkObject(ApiClient)
        every { ApiClient.api } returns apiMock
        mockkStatic(ContextCompat::class)
        mockkConstructor(AudioRecord::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createActivity(): AppCompatActivity {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).create()
        return controller.get()
    }

    @Test
    fun startThenStop_makesWav_and_callsApi_and_invokesComplete() {
        val activity = createActivity()

        every {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
        } returns PackageManager.PERMISSION_GRANTED

        // Mock AudioRecord behavior
        every { anyConstructed<AudioRecord>().startRecording() } just Runs
        every { anyConstructed<AudioRecord>().stop() } just Runs
        every { anyConstructed<AudioRecord>().release() } just Runs
        every {
            anyConstructed<AudioRecord>().read(any<ByteArray>(), any<Int>(), any<Int>())
        } returnsMany listOf(256, 256, 0)

        // Mock API success
        val callMock: Call<STTResponse> = mockk()
        every { apiMock.transcribeAudio(any<MultipartBody.Part>()) } returns callMock
        every { callMock.enqueue(any()) } answers {
            val cb = arg<Callback<STTResponse>>(0)
            cb.onResponse(callMock, Response.success(STTResponse(true, "transcribed", "ok")))
        }

        var started = false
        var stopped = false
        var completedPath: String? = null
        var completedText: String? = null

        val sut = AudioRecorderHelper(
            activity,
            onRecordingStarted = { started = true },
            onRecordingStopped = { stopped = true },
            onRecordingComplete = { path, text ->
                completedPath = path
                completedText = text
            },
            onRecordingFailed = { Assert.fail("should not fail: $it") },
            onPermissionDenied = {},
            onShowPermissionRationale = {}
        )

        // Start + Stop
        sut.checkPermissionAndToggleRecording()
        sut.checkPermissionAndToggleRecording()

        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        Assert.assertTrue(started)
        Assert.assertTrue(stopped)
        Assert.assertNotNull(completedPath)
        Assert.assertEquals("transcribed", completedText)
    }

    @Test
    fun apiFailure_invokes_onRecordingFailed() {
        val activity = createActivity()

        every {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
        } returns PackageManager.PERMISSION_GRANTED

        every { anyConstructed<AudioRecord>().startRecording() } just Runs
        every { anyConstructed<AudioRecord>().stop() } just Runs
        every { anyConstructed<AudioRecord>().release() } just Runs
        every {
            anyConstructed<AudioRecord>().read(any<ByteArray>(), any<Int>(), any<Int>())
        } returnsMany listOf(256, 0)

        // Mock API failure
        val callMock: Call<STTResponse> = mockk()
        every { apiMock.transcribeAudio(any()) } returns callMock
        every { callMock.enqueue(any()) } answers {
            val cb = arg<Callback<STTResponse>>(0)
            cb.onResponse(callMock, Response.success(STTResponse(false, null, "fail")))
        }

        var failedMessage: String? = null

        val sut = AudioRecorderHelper(
            activity,
            onRecordingStarted = {},
            onRecordingStopped = {},
            onRecordingComplete = { _, _ -> },
            onRecordingFailed = { failedMessage = it },
            onPermissionDenied = {},
            onShowPermissionRationale = {}
        )

        sut.checkPermissionAndToggleRecording() // start
        sut.checkPermissionAndToggleRecording() // stop → triggers API

        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        Assert.assertEquals("fail", failedMessage)
    }

    @Test
    fun whenNoPermission_requestsPermission_notStart() {
        val activity = createActivity()

        every {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
        } returns PackageManager.PERMISSION_DENIED

        var started = false
        var denied = false
        var rationale = false

        val sut = AudioRecorderHelper(
            activity,
            onRecordingStarted = { started = true },
            onRecordingStopped = {},
            onRecordingComplete = { _, _ -> },
            onRecordingFailed = {},
            onPermissionDenied = { denied = true },
            onShowPermissionRationale = { rationale = true }
        )

        sut.checkPermissionAndToggleRecording()

        Assert.assertFalse(started)
        Assert.assertFalse(rationale)
        // denied는 실제 런처 콜백이 없으므로 여기선 false 유지 → 단, 권한 체크 시도만 통과 확인
    }
}
