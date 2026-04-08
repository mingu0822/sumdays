package com.example.sumdays.audio

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.STTResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class AudioRecorderHelper(
    private val activity: AppCompatActivity,
    private val onRecordingStarted: () -> Unit,
    private val onRecordingStopped: () -> Unit,
    // ★★★ 콜백 파라미터 유지 (filePath, transcribedText) ★★★
    private val onRecordingComplete: (filePath: String, transcribedText: String?) -> Unit,
    private val onRecordingFailed: (errorMessage: String) -> Unit,
    private val onPermissionDenied: () -> Unit,
    private val onShowPermissionRationale: () -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var audioFilePath: String? = null
    private var rawAudioFilePath: String? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var bufferSizeInBytes = 0
    private val RECORDER_SAMPLE_RATE = 16000
    private val RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO
    private val RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT

    private val requestPermissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) toggleRecording() else onPermissionDenied()
        }

    fun checkPermissionAndToggleRecording() {
        when {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> toggleRecording()
            activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> { onShowPermissionRationale(); requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            else -> requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun toggleRecording() { if (isRecording) stopRecording() else startRecording() }

    private fun startRecording() {
        rawAudioFilePath = "${activity.cacheDir.absolutePath}/sumdays_audio_raw_${System.currentTimeMillis()}.pcm"
        audioFilePath = "${activity.cacheDir.absolutePath}/sumdays_audio_${System.currentTimeMillis()}.wav"
        bufferSizeInBytes = AudioRecord.getMinBufferSize(RECORDER_SAMPLE_RATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING)
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, RECORDER_SAMPLE_RATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, bufferSizeInBytes)
        try {
            audioRecord?.startRecording(); isRecording = true; onRecordingStarted()
            recordingThread = Thread { writeAudioDataToFile(rawAudioFilePath!!) }; recordingThread?.start()
        } catch (e: IllegalStateException) { Log.e("AudioRecorderHelper", "AudioRecord startRecording() failed", e); onRecordingFailed("녹음 시작 실패") }
    }

    private fun writeAudioDataToFile(filePath: String) {
        val data = ByteArray(bufferSizeInBytes); var fos: FileOutputStream? = null
        try { fos = FileOutputStream(filePath); while (isRecording) { val read = audioRecord?.read(data, 0, bufferSizeInBytes) ?: 0; if (read > 0) { try { fos.write(data, 0, read) } catch (e: IOException) {} } }
        } catch (e: IOException) {} finally { try { fos?.close() } catch (e: IOException) {} }
    }

    private fun stopRecording() {
        // 1. 녹음 중지 및 리소스 해제
        isRecording = false
        audioRecord?.apply { try { stop(); release() } catch (e: IllegalStateException) {} }
        audioRecord = null
        recordingThread = null
        onRecordingStopped()

        // 2. WAV 파일 생성
        if (rawAudioFilePath != null && audioFilePath != null) {
            try {
                addWavHeader(rawAudioFilePath!!, audioFilePath!!)
                val finalWavPath = audioFilePath!!
                File(rawAudioFilePath!!).delete()
                rawAudioFilePath = null

                uploadAndTranscribeAudio(finalWavPath)

            } catch (e: IOException) {
                Log.e("AudioRecorderHelper", "Failed to create WAV file", e)
                onRecordingFailed("WAV 파일 생성 실패")
            }
        } else {
            onRecordingFailed("오디오 파일 경로 없음")
        }
    }
    private fun uploadAndTranscribeAudio(filePath: String) {
        val audioFile = File(filePath)
        if (!audioFile.exists()) {
            Log.e("AudioRecorderHelper", "Audio file not found for upload: $filePath")
            onRecordingFailed("오디오 파일을 찾을 수 없음")
            return
        }

        val requestFile = audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("audio", audioFile.name, requestFile)

        ApiClient.api.transcribeAudio(body).enqueue(object : Callback<STTResponse> {
            override fun onResponse(call: Call<STTResponse>, response: Response<STTResponse>) {
                if (response.isSuccessful) {
                    Log.d("response body","$response.body()")
                    val sttResponse = response.body()
                    if (sttResponse != null && sttResponse.success) {
                        // 빈 문자열이 올 경우 toast
                        if (sttResponse.transcribedText == "") {
                            Log.w("AudioRecorderHelper", "STT API returned empty text")
                            onRecordingFailed("텍스트로 변환할 수 없습니다.")
                        }
                        else {
                            onRecordingComplete(filePath, sttResponse.transcribedText)
                        }
                    } else {
                        val message = sttResponse?.message ?: "서버 STT 처리 실패"
                        Log.e("AudioRecorderHelper", "STT API Error: $message")
                        onRecordingFailed(message)
                    }
                } else {
                    Log.e("AudioRecorderHelper", "STT API HTTP Error: ${response.code()} ${response.message()}")
                    onRecordingFailed("서버 통신 오류")
                }
            }

            override fun onFailure(call: Call<STTResponse>, t: Throwable) {
                Log.e("AudioRecorderHelper", "STT API Network Failure", t)
                onRecordingFailed("네트워크 오류")
            }
        })
    }


    @Throws(IOException::class)
    private fun addWavHeader(inFilePath: String, outFilePath: String) {
        val inFile = File(inFilePath); val dataSize = inFile.length(); val totalDataLen = dataSize + 36; val sampleRate = RECORDER_SAMPLE_RATE.toLong(); val channels = 1; val bitsPerSample = 16; val byteRate = (RECORDER_SAMPLE_RATE * channels * bitsPerSample / 8).toLong(); val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte(); header[4] = (totalDataLen and 0xff).toByte(); header[5] = (totalDataLen shr 8 and 0xff).toByte(); header[6] = (totalDataLen shr 16 and 0xff).toByte(); header[7] = (totalDataLen shr 24 and 0xff).toByte(); header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte(); header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte(); header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0; header[20] = 1; header[21] = 0; header[22] = channels.toByte(); header[23] = 0; header[24] = (sampleRate and 0xff).toByte(); header[25] = (sampleRate shr 8 and 0xff).toByte(); header[26] = (sampleRate shr 16 and 0xff).toByte(); header[27] = (sampleRate shr 24 and 0xff).toByte(); header[28] = (byteRate and 0xff).toByte(); header[29] = (byteRate shr 8 and 0xff).toByte(); header[30] = (byteRate shr 16 and 0xff).toByte(); header[31] = (byteRate shr 24 and 0xff).toByte(); header[32] = (channels * bitsPerSample / 8).toByte(); header[33] = 0; header[34] = bitsPerSample.toByte(); header[35] = 0; header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte(); header[40] = (dataSize and 0xff).toByte(); header[41] = (dataSize shr 8 and 0xff).toByte(); header[42] = (dataSize shr 16 and 0xff).toByte(); header[43] = (dataSize shr 24 and 0xff).toByte()
        val outStream = FileOutputStream(outFilePath); outStream.write(header, 0, 44); val inStream = FileInputStream(inFilePath); val buffer = ByteArray(1024); var read: Int; while (inStream.read(buffer).also { read = it } != -1) { outStream.write(buffer, 0, read) }; inStream.close(); outStream.close()
    }

    fun release() { if (isRecording) stopRecording(); audioRecord?.release(); audioRecord = null }
}


