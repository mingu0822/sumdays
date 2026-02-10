package com.example.sumdays

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.daily.memo.Memo
import com.example.sumdays.daily.memo.MemoAdapter
import com.example.sumdays.daily.memo.MemoViewModel
import com.example.sumdays.daily.memo.MemoViewModelFactory
import androidx.recyclerview.widget.ItemTouchHelper
import com.example.sumdays.daily.memo.MemoDragAndDropCallback
import com.example.sumdays.audio.AudioRecorderHelper
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import java.util.ArrayList
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.example.sumdays.data.viewModel.DailyEntryViewModel
import com.example.sumdays.settings.prefs.ThemeState
import com.example.sumdays.utils.setupEdgeToEdge
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource

// 일기 작성 및 수정 화면을 담당하는 액티비티
class DailyWriteActivity : AppCompatActivity() {
    private lateinit var date: String
    private lateinit var memoAdapter: MemoAdapter

    // UI 뷰 변수들
    private lateinit var dateTextView: TextView
    private lateinit var memoListView: RecyclerView
    private lateinit var memoInputEditText: EditText
    private lateinit var sendIcon: ImageView

    private lateinit var micIcon: ImageView
    private lateinit var stopIcon: ImageView

    private lateinit var audioWaveView: LinearLayout
    private lateinit var waveBar1: View
    private lateinit var waveBar2: View
    private lateinit var waveBar3: View

    private lateinit var audioRecorderHelper: AudioRecorderHelper
    private lateinit var readDiaryButton: Button
    private lateinit var navBarController: NavBarController


    private val memoViewModel: MemoViewModel by viewModels {
        MemoViewModelFactory(
            (application as MyApplication).repository
        )
    }

    private val dailyEntryViewModel: DailyEntryViewModel by viewModels()

    private var defaultMemoHint: String = ""
    private var isRecording = false
    private var waveAnimatorSet: AnimatorSet? = null

    private var pendingAudioMemoId: Int? = null

    private var isApiProcessingAudio = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_daily_write)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.write)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // 모든 뷰 초기화
        initViews()

        // 헬퍼 초기화 (initViews 이후 호출)
        audioRecorderHelper = createAudioRecorderHelper()

        // 인텐트 데이터 처리 및 데이터 관찰 시작
        handleIntent(intent)

        // UI 요소에 클릭 리스너 설정
        setupClickListeners()

        applyThemeModeSettings()

        // 하단 네비게이션 바 설정
        navBarController = NavBarController(this)
        navBarController.setNavigationBar(NavSource.WRITE) {
            val currentMemos = memoAdapter.currentList
            Intent(this, DailySumActivity::class.java).apply {
                putExtra("date", date)
                putParcelableArrayListExtra("memo_list", ArrayList(currentMemos))
            }
        }

        val rootView = findViewById<View>(R.id.write)
        setupEdgeToEdge(rootView)
    }

    private fun applyThemeModeSettings(){
        // Apply dark mode
        ThemeState.isDarkMode = (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)

        if (ThemeState.isDarkMode){
            readDiaryButton.setTextColor(getColor(R.color.white))
            memoInputEditText.setTextColor(getColor(R.color.black))
            memoInputEditText.setHintTextColor(getColor(R.color.black))
            sendIcon.setImageResource(R.drawable.ic_send_white)
            micIcon.setImageResource(R.drawable.ic_mic_white)
        }
        else{
            readDiaryButton.setTextColor(getColor(R.color.white))
            memoInputEditText.setTextColor(getColor(R.color.black))
            memoInputEditText.setHintTextColor(getColor(R.color.black))
            sendIcon.setImageResource(R.drawable.ic_send_black)
            micIcon.setImageResource(R.drawable.ic_mic_black)
        }
    }

    private fun createAudioRecorderHelper(): AudioRecorderHelper {
        return AudioRecorderHelper(
            activity = this,
            onRecordingStarted = {
                runOnUiThread {
                    Toast.makeText(this, "녹음 시작...", Toast.LENGTH_SHORT).show()
                    isRecording = true
                    micIcon.visibility = View.GONE
                    stopIcon.visibility = View.VISIBLE
                    sendIcon.visibility = View.GONE
                    audioWaveView.visibility = View.VISIBLE
                    memoInputEditText.visibility = View.INVISIBLE
                    startWaveAnimation()
                }
            },
            onRecordingStopped = {
                runOnUiThread {
                    Toast.makeText(this, "녹음 완료. 텍스트 변환 중...", Toast.LENGTH_SHORT).show()
                    isRecording = false

                    isApiProcessingAudio = true
                    micIcon.visibility = View.VISIBLE
                    micIcon.isEnabled = false // 클릭 안되게
                    micIcon.alpha = 0.5f // 반투명하게
                    stopIcon.visibility = View.GONE
                    sendIcon.visibility = View.VISIBLE

                    audioWaveView.visibility = View.GONE
                    memoInputEditText.visibility = View.VISIBLE
                    stopWaveAnimation()

                    val tempId = System.currentTimeMillis().toInt()
                    val dummyMemo = Memo(
                        id = tempId,
                        content = "음성 인식 중...",
                        timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Calendar.getInstance().time),
                        date = date,
                        order = memoAdapter.itemCount ,
                        type = "audio"
                    )
                    pendingAudioMemoId = tempId

                    val currentList = memoAdapter.currentList.toMutableList()
                    currentList.add(dummyMemo)
                    memoAdapter.submitList(currentList)
                    memoListView.smoothScrollToPosition(currentList.size - 1)
                }
            },
            onRecordingComplete = { filePath, transcribedText ->
                Log.d("DailyWriteActivity", "녹음 완료, 파일 경로: $filePath")
                Log.d("DailyWriteActivity", "변환된 텍스트: $transcribedText")
                runOnUiThread {
                    val finalContent = transcribedText ?: "[오디오 파일: $filePath]"
                    removeDummyMemoAndAddFinal(finalContent, "audio")
                    Toast.makeText(this, "텍스트 변환 완료.", Toast.LENGTH_SHORT).show()

                    isApiProcessingAudio = false
                    micIcon.isEnabled = true
                    micIcon.alpha = 1.0f
                }
            },
            onRecordingFailed = { errorMessage ->
                runOnUiThread {
                    Toast.makeText(this, "음성 인식에 실패했습니다:\n$errorMessage", Toast.LENGTH_SHORT).show()
                    val errorContent = "[오류: $errorMessage]"
                    removeDummyMemo(errorContent, "audio")

                    isApiProcessingAudio = false
                    micIcon.isEnabled = true // 아이콘 다시 활성화
                    micIcon.alpha = 1.0f
                }
            },
            onPermissionDenied = {
                runOnUiThread {
                    Toast.makeText(this, "음성 녹음을 사용하려면 마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
            },
            onShowPermissionRationale = {
                runOnUiThread {
                    Toast.makeText(this, "메모를 음성으로 녹음하려면 마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 액티비티가 재사용될 때 새로운 인텐트 처리
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun initViews() {
        dateTextView = findViewById(R.id.date_text_view)
        memoListView = findViewById(R.id.memo_list_view)
        memoInputEditText = findViewById(R.id.memo_input_edittext)
        sendIcon = findViewById(R.id.send_icon)
        micIcon = findViewById(R.id.mic_icon)
        stopIcon = findViewById(R.id.stop_icon)
        // photoIcon = findViewById(R.id.photo_icon) // ★★★ 삭제 ★★★
        readDiaryButton = findViewById(R.id.read_diary_button)

        audioWaveView = findViewById(R.id.audio_wave_view)
        waveBar1 = findViewById(R.id.wave_bar_1)
        waveBar2 = findViewById(R.id.wave_bar_2)
        waveBar3 = findViewById(R.id.wave_bar_3)

        memoListView.layoutManager = LinearLayoutManager(this)
        memoAdapter = MemoAdapter()
        memoListView.adapter = memoAdapter

        // 메모 아이템 클릭 시 수정 다이얼로그 표시
        memoAdapter.setOnItemClickListener(object : MemoAdapter.OnItemClickListener {
            override fun onItemClick(memo: Memo) {
                showEditMemoDialog(memo)
            }
        })

        // 드래그 앤 드롭 및 스와이프 콜백 정의
        val dragAndDropCallback = MemoDragAndDropCallback(
            adapter = memoAdapter,
            onMove = { fromPosition, toPosition -> /* ... */ },
            onDelete = { position ->
                val memoToDelete = memoAdapter.currentList[position]
                memoViewModel.delete(memoToDelete)
            },
            onDragStart = { },
            onDragEnd = {
                val updatedList = memoAdapter.currentList.toMutableList()
                for (i in updatedList.indices) {
                    updatedList[i] = updatedList[i].copy(order = i)
                }
                memoViewModel.updateAll(updatedList)
            }
        )

        val itemTouchHelper = ItemTouchHelper(dragAndDropCallback)
        itemTouchHelper.attachToRecyclerView(memoListView)
    }

    private fun handleIntent(intent: Intent?) {
        date = intent?.getStringExtra("date") ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        dateTextView.text = date

        memoViewModel.getMemosForDate(date).observe(this) { memos ->
            memos?.let {
                // LiveData가 갱신될 때, 현재 UI에 임시 메모가 있다면 유지
                if (pendingAudioMemoId != null && !it.any { memo -> memo.id == pendingAudioMemoId }) {
                    val currentList = memoAdapter.currentList.toMutableList()
                    memoAdapter.submitList(currentList)
                } else {
                    memoAdapter.submitList(it)
                }
            }
        }
        dailyEntryViewModel.getEntry(date).observe(this) { entry ->
            val diaryExists = !entry?.diary.isNullOrEmpty()
            if (diaryExists) {
                // 일기가 있으면: 버튼 활성화 및 보라색
                readDiaryButton.isEnabled = true
                readDiaryButton.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.btn_violet))
            } else {
                // 일기가 없으면: 버튼 비활성화 및 회색
                readDiaryButton.isEnabled = false
                readDiaryButton.backgroundTintList =
                    ContextCompat.getColorStateList(this, R.color.gray)
            }
        }
    }

    fun showEditMemoDialog(memo: Memo) {
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_memo, null)
        val editText = dialogView.findViewById<EditText>(R.id.edit_text_memo_content)
        editText.setText(memo.content)

        builder.setView(dialogView)
            .setPositiveButton("수정") { dialog, id ->
                val newContent = editText.text.toString().trim()
                if (newContent.isNotEmpty()) {
                    val updatedMemo = memo.copy(content = newContent)
                    memoViewModel.update(updatedMemo)
                } else {
                    Toast.makeText(this, "내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("삭제") { dialog, id ->
                memoViewModel.delete(memo)
                dialog.dismiss()
            }
            .setNeutralButton("취소") { dialog, id ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
    private fun setupClickListeners() {
        readDiaryButton.setOnClickListener {
            val intent = Intent(this, DailyReadActivity::class.java)
            intent.putExtra("date", date)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        sendIcon.setOnClickListener {
            val memoContent = memoInputEditText.text.toString().trim()
            if (memoContent.isNotEmpty()) {
                addTextMemoToList(memoContent, "text")
                memoInputEditText.text.clear()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
            } else {
                Toast.makeText(this, "메모 내용을 입력해 주세요.", Toast.LENGTH_SHORT).show()
            }
        }
        micIcon.setOnClickListener {
            if (isApiProcessingAudio) {
                Toast.makeText(this, "이전 음성을 처리 중입니다...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            audioRecorderHelper.checkPermissionAndToggleRecording()
        }
        stopIcon.setOnClickListener {
            audioRecorderHelper.checkPermissionAndToggleRecording()
        }

        memoInputEditText.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                stopIcon.visibility = View.GONE
            } else {
                if (isRecording) {
                    micIcon.visibility = View.GONE
                    stopIcon.visibility = View.VISIBLE
                } else {
                    micIcon.visibility = View.VISIBLE
                    stopIcon.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorderHelper.release()
        waveAnimatorSet?.cancel()
    }
    private fun startWaveAnimation() {
        waveAnimatorSet?.cancel()
        val anim1 = ObjectAnimator.ofFloat(waveBar1, "scaleY", 1.0f, 0.3f, 0.7f, 1.0f).apply {
            duration = 400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
        }
        val anim2 = ObjectAnimator.ofFloat(waveBar2, "scaleY", 1.0f, 0.5f, 0.2f, 0.8f, 1.0f).apply {
            duration = 400; startDelay = 150; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
        }
        val anim3 = ObjectAnimator.ofFloat(waveBar3, "scaleY", 1.0f, 0.6f, 1.0f, 0.4f, 1.0f).apply {
            duration = 400; startDelay = 300; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
        }
        waveAnimatorSet = AnimatorSet().apply { playTogether(anim1, anim2, anim3); start() }
    }

    private fun stopWaveAnimation() {
        waveAnimatorSet?.cancel(); waveAnimatorSet = null
        waveBar1.scaleY = 1.0f; waveBar2.scaleY = 1.0f; waveBar3.scaleY = 1.0f
    }

    private fun addTextMemoToList(content: String, memoType: String = "text") {
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Calendar.getInstance().time)
        val newMemo = Memo(
            id = 0, // Room이 자동 생성
            content = content,
            timestamp = currentTime,
            date = date,
            order = memoAdapter.itemCount,
            type = memoType
        )

        Log.d("test" , "${content} / ${currentTime} / ${date} / ${memoAdapter.itemCount}  / ${memoType}")
        memoViewModel.insert(newMemo)
        memoListView.smoothScrollToPosition(memoAdapter.itemCount)
    }

    private fun removeDummyMemoAndAddFinal(newContent: String, memoType: String) {
        if (pendingAudioMemoId != null) {
            val currentList = memoAdapter.currentList.toMutableList()
            val removed = currentList.removeAll { it.id == pendingAudioMemoId }

            if (removed) {
                memoAdapter.submitList(currentList)
            }
            pendingAudioMemoId = null
        }
        addTextMemoToList(newContent, memoType)
    }
    private fun removeDummyMemo(errorContent: String, memoType: String) {
        if (pendingAudioMemoId != null) {
            val currentList = memoAdapter.currentList.toMutableList()
            val removed = currentList.removeAll { it.id == pendingAudioMemoId }

            if (removed) {
                memoAdapter.submitList(currentList)
            }
            pendingAudioMemoId = null
        }
    }
}