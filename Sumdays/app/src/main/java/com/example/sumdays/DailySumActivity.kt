package com.example.sumdays

import com.example.sumdays.statistics.StreakPrefs
import android.app.Dialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.sumdays.daily.diary.AnalysisRepository
import com.example.sumdays.daily.memo.Memo
import com.example.sumdays.daily.memo.MemoMergeAdapter
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.data.dao.UserStyleDao
import com.example.sumdays.data.viewModel.DailyEntryViewModel
import com.example.sumdays.settings.prefs.UserStatsPrefs
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.utils.setupEdgeToEdge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.core.view.isVisible

class DailySumActivity : AppCompatActivity() {

    private lateinit var date: String
    private lateinit var recyclerView: RecyclerView
    private lateinit var memoMergeAdapter: MemoMergeAdapter
    private val viewModel: DailyEntryViewModel by viewModels()

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

        userStatsPrefs = UserStatsPrefs(this)
        userStyleDao = AppDatabase.getDatabase(this).userStyleDao()

        date = intent.getStringExtra("date") ?: "알 수 없는 날짜"
        loadingOverlay = findViewById(R.id.loading_overlay)
        loadingGifView = findViewById(R.id.loading_gif_view)
        findViewById<TextView>(R.id.date_text_view).text = date

        recyclerView = findViewById(R.id.memo_list_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val receivedMemoList: List<Memo> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra("memo_list", Memo::class.java) ?: emptyList()
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Memo>("memo_list") ?: emptyList()
            }

        val initialMemoList: MutableList<Memo> = receivedMemoList.toMutableList()

        memoMergeAdapter = MemoMergeAdapter(
            this,
            initialMemoList,
            lifecycleScope,
            ::showAllMergedSheet,
            userStatsPrefs = userStatsPrefs,
            userStyleDao = userStyleDao
        )
        recyclerView.itemAnimator = null
        recyclerView.adapter = memoMergeAdapter

        applyThemeModeSettings()

        findViewById<ImageView>(R.id.undo_icon).setOnClickListener {
            memoMergeAdapter.undoLastMerge()
        }

        findViewById<ImageView>(R.id.back_icon).setOnClickListener {
            handleBackPress()
        }

        findViewById<ImageButton>(R.id.skip_icon).setOnClickListener {
            showLoading(true)
            mergeJob = lifecycleScope.launch {
                try {
                    val result = memoMergeAdapter.mergeAllMemo()
                    saveDiary(result)
                    moveToReadActivity()
                } catch (e: CancellationException) {
                    showLoading(false)
                    Toast.makeText(
                        this@DailySumActivity,
                        "메모 합치기가 중단되었습니다",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    showLoading(false)
                    val msg =
                        if (e is MemoMergeAdapter.MergeException) e.message else "오류가 발생했습니다."
                    Toast.makeText(this@DailySumActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        val rootView = findViewById<View>(R.id.sum_layout)
        setupEdgeToEdge(rootView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun applyThemeModeSettings() {
        val themeKey = ThemePrefs.getTheme(this)
        val currentTheme = ThemeRepository.ownedThemes[themeKey] ?: return

        val rootView = findViewById<View>(R.id.sum_layout)
        val memoListView = findViewById<RecyclerView>(R.id.memo_list_view)
        val dateTextView = findViewById<TextView>(R.id.date_text_view)
        val backIcon = findViewById<ImageView>(R.id.back_icon)
        val undoIcon = findViewById<ImageView>(R.id.undo_icon)
        val skipIcon = findViewById<ImageButton>(R.id.skip_icon)

        rootView.setBackgroundResource(currentTheme.backgroundColor)
        memoListView.setBackgroundResource(currentTheme.blockColor)

        dateTextView.setTextColor(
            ContextCompat.getColor(this, currentTheme.textPrimaryColor)
        )

        backIcon.setImageResource(currentTheme.backIcon)
    }

    private fun handleBackPress() {
        if (loadingOverlay.isVisible) {
            mergeJob?.cancel()
        } else {
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
    private fun getTodayDate(): String {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return today.format(formatter)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun saveDiary(mergedResult: String) {
        val todayStr = getTodayDate()
        StreakPrefs.onDiarySaved(this, todayStr)

        viewModel.updateEntry(date = date, diary = mergedResult)
        AnalysisRepository.requestAnalysis(
            date = date,
            diary = mergedResult,
            personaId = 1, // 임시 하드코딩
            context = this@DailySumActivity,
            viewModel = viewModel
        )
    }

    private var mergeSheetShowing = false

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showAllMergedSheet() {
        if (mergeSheetShowing) return
        mergeSheetShowing = true

        val sheet = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_merge_done, null, false)
        sheet.setContentView(view)

        val btnCancel =
            view.findViewById<ImageButton>(R.id.btnCancel)
        val btnSave =
            view.findViewById<ImageButton>(R.id.btnSave)

        btnCancel.setOnClickListener {
            sheet.dismiss()
        }

        btnSave.setOnClickListener {
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

        val width = (resources.displayMetrics.widthPixels * 0.9f).toInt()
        sheet.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        sheet.window?.setGravity(android.view.Gravity.CENTER)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}