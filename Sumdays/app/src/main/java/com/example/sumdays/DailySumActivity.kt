package com.example.sumdays

import android.app.Dialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.daily.memo.Memo
import com.example.sumdays.daily.memo.MemoMergeAdapter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.example.sumdays.daily.diary.AnalysisRepository
import kotlinx.coroutines.launch
import androidx.activity.viewModels
import com.example.sumdays.data.viewModel.DailyEntryViewModel
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.data.dao.UserStyleDao
import com.example.sumdays.settings.prefs.UserStatsPrefs
import com.bumptech.glide.Glide
import com.example.sumdays.utils.setupEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.activity.OnBackPressedCallback
import com.example.sumdays.settings.prefs.PersonaPrefs

class DailySumActivity : AppCompatActivity() {

    private lateinit var date: String
    private lateinit var recyclerView: RecyclerView
    private lateinit var memoMergeAdapter: MemoMergeAdapter
    private val viewModel: DailyEntryViewModel by viewModels()

    // Prefs 및 DAO 인스턴스 선언
    private lateinit var userStatsPrefs: UserStatsPrefs
    private lateinit var userStyleDao: UserStyleDao
    private lateinit var loadingOverlay: View
    private lateinit var loadingGifView: ImageView
    private var mergeJob: Job? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sum)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.sum_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        // Prefs 및 DAO 초기화
        userStatsPrefs = UserStatsPrefs(this)
        userStyleDao = AppDatabase.getDatabase(this).userStyleDao() // StyleDatabase를 통해 DAO 획득

        date = intent.getStringExtra("date") ?: "알 수 없는 날짜"
        loadingOverlay = findViewById(R.id.loading_overlay)
        loadingGifView = findViewById(R.id.loading_gif_view)
        findViewById<TextView>(R.id.date_text_view).text = date

        findViewById<ImageView>(R.id.undo_icon).setOnClickListener {
            memoMergeAdapter.undoLastMerge()
        }
        findViewById<ImageView>(R.id.back_icon).setOnClickListener {
            handleBackPress()
        }

        findViewById<ImageButton>(R.id.skip_icon).setOnClickListener {
                showLoading(true)
            // 코루틴 시작 시 Job을 변수에 저장
            mergeJob = lifecycleScope.launch {
                try {
                    val result = memoMergeAdapter.mergeAllMemo()
                    saveDiary(result)
                    moveToReadActivity()
                } catch (e: CancellationException) {
                    // 작업이 취소되었을 때 처리
                    showLoading(false)
                    Toast.makeText(this@DailySumActivity, "메모 합치기가 중단되었습니다", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    // 그 외 오류 (네트워크 등)
                    showLoading(false)
                    // Adapter 내부에서 Toast를 띄우지 않는 구조라면 여기서 띄워줌
                    val msg = if (e is MemoMergeAdapter.MergeException) e.message else "오류가 발생했습니다."
                    Toast.makeText(this@DailySumActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Intent에서 메모 리스트를 받음
        val receivedMemoList: List<Memo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("memo_list", Memo::class.java) ?: emptyList()
        } else {
            // Deprecated 메서드를 사용하는 이전 API 레벨 대응 코드
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Memo>("memo_list") ?: emptyList()
        }

        // MemoMergeAdapter는 MutableList<Memo>를 기대하므로 변환함
        val initialMemoList: MutableList<Memo> = receivedMemoList.toMutableList()

        recyclerView = findViewById(R.id.memo_list_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        memoMergeAdapter = MemoMergeAdapter(
            this,
            initialMemoList,
            lifecycleScope,
            ::showAllMergedSheet,
            userStatsPrefs = userStatsPrefs,
            userStyleDao = userStyleDao
        )
        recyclerView.itemAnimator = null // streaming시 메모 깜빡임 방지
        recyclerView.adapter = memoMergeAdapter

        // 상태바, 네비게이션바 같은 색으로
        val rootView = findViewById<View>(R.id.sum_layout)
        setupEdgeToEdge(rootView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun handleBackPress() {
        if (loadingOverlay.visibility == View.VISIBLE) {
            // 1. 로딩 중이라면 작업 취소
            mergeJob?.cancel()
            // cancel() 호출 -> 코루틴 내부에서 CancellationException 발생 -> catch 블록 실행 -> Toast 출력
        } else {
            // 2. 로딩 중이 아니라면 Activity 종료
            finish()
        }
    }

    private fun showLoading(isLoading: Boolean) {
        runOnUiThread {
            if (isLoading) {
                loadingOverlay.visibility = View.VISIBLE
                loadingGifView.visibility = View.VISIBLE
                Glide.with(this)
                    .asGif()
                    .load(R.drawable.loading_animation)
                    .into(loadingGifView)
            } else {
                loadingOverlay.visibility = View.GONE
                loadingGifView.visibility = View.GONE
                // Glide 로드 중지
                Glide.with(this).clear(loadingGifView)
            }
        }
    }
    private fun moveToReadActivity() {
        val intent = Intent(this, DailyReadActivity::class.java).putExtra("date", date)
        startActivity(intent)
        finish()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getTodayDate(): String{
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val todayStr = today.format(formatter)
        return todayStr
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun saveDiary(mergedResult: String){
        val todayStr = getTodayDate()
        // 일기 작성 연속일 수 계산
        StreakPrefs.onDiarySaved(this, todayStr)
        // 로컬 db에 일기 저장
        viewModel.updateEntry(date = date, diary = mergedResult)
        // 일기 분석 요청
        AnalysisRepository.requestAnalysis(
            date = date,
            diary = mergedResult,
            personaId = PersonaPrefs.getSelectedPersonaId(this),
            context = this,
            viewModel = viewModel)
    }

    private var mergeSheetShowing = false  // 중복 방지용

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showAllMergedSheet() {
        if (mergeSheetShowing) return
        mergeSheetShowing = true

        val sheet = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_merge_done, null, false)
        sheet.setContentView(view)

        val btnCancel = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnSave   = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)

        btnCancel.setOnClickListener {
            sheet.dismiss()
        }

        btnSave.setOnClickListener {
            // 서버 머지 결과를 최종 일기로 저장하고 읽기화면 이동
            lifecycleScope.launch {
                try {
                    saveDiary(memoMergeAdapter.getMemoContent(0))
                    moveToReadActivity()
                } finally {
                    sheet.dismiss()
                }
            }
        }

        sheet.setOnDismissListener { mergeSheetShowing = false }
        sheet.show()

        // 화면 폭의 90%로 설정
        val width = (resources.displayMetrics.widthPixels * 0.9f).toInt()
        sheet.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        // 중앙 정렬
        sheet.window?.setGravity(android.view.Gravity.CENTER)
    }
    override fun onDestroy() {
        super.onDestroy()
    }
}
