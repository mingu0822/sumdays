package com.example.sumdays

import android.app.Dialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.sumdays.daily.memo.MemoMergeAdapter
import com.example.sumdays.daily.memo.MergeMarbleRepository
import com.example.sumdays.daily.memo.MergeMarbleState
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.data.Memo
import com.example.sumdays.data.dao.UserStyleDao
import com.example.sumdays.data.repository.AnalysisRepository
import com.example.sumdays.data.sync.BackupSyncRunner
import com.example.sumdays.data.viewModel.DailyEntryViewModel
import com.example.sumdays.settings.prefs.DiaryPermissionPrefs
import com.example.sumdays.settings.prefs.UserStatsPrefs
import com.example.sumdays.statistics.StreakPrefs
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.utils.setupEdgeToEdge
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    private lateinit var marbleRepository: MergeMarbleRepository
    private lateinit var marbleText: TextView
    private lateinit var marbleCard: MaterialCardView
    private var marbleState: MergeMarbleState? = null
    private var marbleJob: Job? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sum)
        marbleRepository = MergeMarbleRepository(applicationContext)
        marbleText = findViewById(R.id.merge_marble_text)
        marbleCard = findViewById(R.id.merge_marble_card)
        marbleCard.setOnClickListener {
            if (marbleState == null) refreshMergeMarble()
            else startActivity(
                Intent(this, ShopActivity::class.java)
                    .putExtra(ShopActivity.EXTRA_INITIAL_CATEGORY, ShopActivity.CATEGORY_MERGE_TICKET)
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.sum_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        val recyclerView = findViewById<RecyclerView>(R.id.memo_list_view)

        userStatsPrefs = UserStatsPrefs(this)
        userStyleDao = AppDatabase.getDatabase(this).userStyleDao()

        date = intent.getStringExtra("date") ?: "알 수 없는 날짜"
        loadingOverlay = findViewById(R.id.loading_overlay)
        loadingGifView = findViewById(R.id.loading_gif_view)

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
            userStyleDao = userStyleDao,
            onMarbleChanged = { refreshMergeMarble() },
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

        findViewById<ImageButton>(R.id.go_icon).setOnClickListener {
            if (mergeJob?.isActive == true || memoMergeAdapter.isMerging) return@setOnClickListener
            // 남은 횟수가 0이어도 이전 성공 결과 복구는 가능하므로 서버에서 최종 판단한다.
            showLoading(true)
            mergeJob = lifecycleScope.launch {
                try {
                    val result = memoMergeAdapter.mergeAllMemo()
                    saveDiary(result, memoMergeAdapter.lastMood)
                    resolvePermissionAndFinish()
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
                        if (e is com.example.sumdays.daily.memo.MergeException) e.message else "오류가 발생했습니다."
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

    override fun onResume() {
        super.onResume()
        if (::marbleRepository.isInitialized) refreshMergeMarble()
    }

    private fun refreshMergeMarble() {
        if (isFinishing || isDestroyed) return
        marbleJob?.cancel()
        marbleJob = lifecycleScope.launch {
            try {
                renderMergeMarble(marbleRepository.refresh())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                marbleState = null
                marbleText.text = "합치기 횟수를 불러오지 못했어요. 연결을 확인해 주세요."
                marbleCard.isEnabled = true
            }
        }
    }

    private fun renderMergeMarble(state: MergeMarbleState) {
        marbleState = state
        marbleText.text = if (state.passActive) {
            getString(R.string.merge_marble_pass_active)
        } else {
            getString(
                R.string.merge_marble_state,
                state.ticketBalance,
                state.freeRemainingCount,
            )
        }
        marbleCard.isEnabled = true
    }

    private fun applyThemeModeSettings() {
        val themeKey = ThemePrefs.getTheme(this)
        val currentTheme = ThemeRepository.ownedThemes[themeKey] ?: return

        val rootView = findViewById<View>(R.id.sum_layout)
        val memoListView = findViewById<RecyclerView>(R.id.memo_list_view)
        val backIcon = findViewById<ImageView>(R.id.back_icon)
        val undoIcon = findViewById<ImageView>(R.id.undo_icon)
        val skipIcon = findViewById<ImageButton>(R.id.go_icon)

        rootView.setBackgroundResource(currentTheme.backgroundColor)
        memoListView.setBackgroundResource(currentTheme.blockStyleD)

//        dateTextView.setTextColor(
//            ContextCompat.getColor(this, currentTheme.textPrimaryColor)
//        )
//
//        backIcon.setImageResource(currentTheme.backIcon)
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

    // 생성된 일기의 소셜 공개 여부 결정: 설정이 '매번 물어보기'면 팝업, 아니면 기본값을 바로 저장
    private fun resolvePermissionAndFinish() {
        when (DiaryPermissionPrefs.getMode(this)) {
            DiaryPermissionPrefs.MODE_DEFAULT_TRUE -> {
                savePermissionAndFinish(true)
            }

            DiaryPermissionPrefs.MODE_DEFAULT_FALSE -> {
                savePermissionAndFinish(false)
            }

            else -> showPermissionDialog()
        }
    }

    private fun showPermissionDialog() {
        showLoading(false)
        AlertDialog.Builder(this)
            .setTitle("일기 공개 설정")
            .setMessage("이 일기를 소셜 친구에게 공개할까요?\n(일기 화면의 자물쇠 버튼으로 언제든 바꿀 수 있어요)")
            .setPositiveButton("공개") { _, _ ->
                savePermissionAndFinish(true)
            }
            .setNegativeButton("비공개") { _, _ ->
                savePermissionAndFinish(false)
            }
            .setCancelable(false)
            .show()
    }

    private fun savePermissionAndFinish(isAllowed: Boolean) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                viewModel.updateEntryNow(date, isAllowed = isAllowed)
                val sync = BackupSyncRunner.syncNow(applicationContext)
                if (!sync.success) {
                    Toast.makeText(this@DailySumActivity,
                        "일기는 기기에 저장했어요. 서버 저장은 자동으로 재시도합니다.", Toast.LENGTH_LONG).show()
                }
                moveToReadActivity()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@DailySumActivity, "일기 저장을 완료하지 못했어요. 다시 시도해 주세요.", Toast.LENGTH_LONG).show()
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
    private suspend fun saveDiary(mergedResult: String, precomputedMood: String? = null) {
        val todayStr = getTodayDate()
        StreakPrefs.onDiarySaved(this, todayStr)

        viewModel.updateEntryNow(date = date, diary = mergedResult)
        AnalysisRepository.requestAnalysis(
            date = date,
            diary = mergedResult,
            viewModel = viewModel,
            precomputedMood = precomputedMood
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
        sheet.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnCancel =
            view.findViewById<ImageButton>(R.id.btnCancel)
        val btnSave =
            view.findViewById<ImageButton>(R.id.btnSave)

        btnCancel.setOnClickListener {
            sheet.dismiss()
        }

        btnSave.setOnClickListener {
            if (!btnSave.isEnabled) return@setOnClickListener
            btnSave.isEnabled = false
            showLoading(true)
            lifecycleScope.launch {
                try {
                    val text = memoMergeAdapter.getMemoContent(0)
                    saveDiary(text)
                    sheet.dismiss()
                    resolvePermissionAndFinish()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("DailySumActivity", "팝업 저장 중 오류", e)
                    showLoading(false)
                    Toast.makeText(this@DailySumActivity, "일기 저장을 완료하지 못했어요. 다시 시도해 주세요.", Toast.LENGTH_LONG).show()
                } finally {
                    btnSave.isEnabled = true
                }
            }
        }

        sheet.setOnDismissListener { mergeSheetShowing = false }
        sheet.show()

        val width = (resources.displayMetrics.widthPixels * 0.98f).toInt()
        sheet.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        sheet.window?.setGravity(android.view.Gravity.CENTER)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
