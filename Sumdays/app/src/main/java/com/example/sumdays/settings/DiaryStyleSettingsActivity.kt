package com.example.sumdays.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.R
import com.example.sumdays.databinding.ActivityProfileDiaryStyleBinding
import com.example.sumdays.data.style.UserStyle
import com.example.sumdays.data.style.UserStyleViewModel
import com.example.sumdays.settings.prefs.ThemeState
import com.example.sumdays.settings.prefs.UserStatsPrefs
import com.example.sumdays.settings.ui.CenterScaleOnScrollListener
import com.example.sumdays.settings.ui.HorizontalMarginItemDecoration
import com.example.sumdays.settings.ui.StyleCardAdapter
import com.example.sumdays.utils.setupEdgeToEdge
import kotlinx.coroutines.*

open class DiaryStyleSettingsActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    lateinit var binding: ActivityProfileDiaryStyleBinding
    private lateinit var userStatsPrefs: UserStatsPrefs
    private lateinit var styleViewModel: UserStyleViewModel
    private lateinit var adapter: StyleCardAdapter

    private val job = Job()
    private var currentSnapPos: Int = 0
    private var hasInitialScroll = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileDiaryStyleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userStatsPrefs = provideUserStatsPrefs()
        styleViewModel = provideUserStyleViewModel()

        setupHeader()
        setupRecycler()
        observeStyles()
        setupSelectButton()

        applyThemeModeSettings()

        // 상태바, 네비게이션바 같은 색으로
        val rootView = findViewById<View>(R.id.setting_dairy_style_root)
        setupEdgeToEdge(rootView)
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun applyThemeModeSettings(){
        // Apply dark mode
        ThemeState.isDarkMode = (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)

        if (ThemeState.isDarkMode){
            binding.header.headerBackIcon.setImageResource(R.drawable.ic_arrow_back_white)
            binding.selectButton.setTextColor(getColor(R.color.white))
        }
        else{
            binding.header.headerBackIcon.setImageResource(R.drawable.ic_arrow_back_black)
            binding.selectButton.setTextColor(getColor(R.color.white))
        }
    }

    protected open fun provideUserStatsPrefs(): UserStatsPrefs = UserStatsPrefs(this)

    protected open fun provideUserStyleViewModel(): UserStyleViewModel = ViewModelProvider(this).get(UserStyleViewModel::class.java)

    protected open fun createAdapter(): StyleCardAdapter = StyleCardAdapter(onSelect = { style -> style?.styleId?.let { saveActiveStyle(it) } }, onRename = { style, newName -> renameStyle(style, newName) }, onDelete = { style -> deleteStyle(style) }, onAdd = { startActivity(Intent(this, StyleExtractionActivity::class.java)) })

    private fun setupHeader() {
        binding.header.headerTitle.text = "AI 스타일 설정"
        binding.header.headerBackIcon.setOnClickListener { finish() }
    }

    private fun setupRecycler() {
        adapter = createAdapter()

        val lm = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        val snap = PagerSnapHelper()

        binding.styleRecycler.apply {
            layoutManager = lm
            adapter = this@DiaryStyleSettingsActivity.adapter

            // 양 옆을 비워서 다음 카드 모서리가 보이도록
            clipToPadding = false
            clipChildren = false
            val horizontalPadding = (resources.displayMetrics.widthPixels * 0.10f).toInt()
            val verticalPadding = (resources.displayMetrics.heightPixels * 0.025f).toInt()
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

            // 카드 간 간격
            addItemDecoration(
                HorizontalMarginItemDecoration(
                    marginPx = (resources.displayMetrics.density * 8).toInt() // 16dp
                )
            )

            // 스냅 + 스크롤 후 현재 카드 찾기
            snap.attachToRecyclerView(this)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(rv, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        val v = snap.findSnapView(lm) ?: return
                        currentSnapPos = lm.getPosition(v)
                        updateSelectButtonText()
                    }
                }
            })

            // 가운데 카드 크고, 양 옆은 약간 작게
            addOnScrollListener(CenterScaleOnScrollListener())
            post { updateSelectButtonText() }
        }
    }

    private fun observeStyles() {
        styleViewModel.getAllStyles().observe(this) { list ->

            // 활성화된 스타일이 없으면 기본 스타일 자동 선택
            var activeId = userStatsPrefs.getActiveStyleId()

//            Log.d("STYLE_DEBUG",
//                "observeStyles() 실행됨\n" +
//                        " - activeId(pref): $activeId\n" +
//                        " - list size: ${list.size}\n" +
//                        " - styleIds: ${list.joinToString { it.styleId.toString() }}\n" +
//                        " - currentSnapPos: $currentSnapPos\n"
//            )

            if (activeId == 0L && list.isNotEmpty()) {
                val firstStyle = list.first()
                userStatsPrefs.saveActiveStyleId(firstStyle.styleId)
                activeId = firstStyle.styleId
            }

            adapter.submit(list, activeId)
            binding.styleRecycler.post {
                updateSelectButtonText()
            }

            // 처음 들어왔을 때만 스크롤 이동
            if (!hasInitialScroll) {
                hasInitialScroll = true

                val activeId = userStatsPrefs.getActiveStyleId()
                val targetIndex = adapter.items.indexOfFirst { it.styleId == activeId }.let {
                    if (it == -1) 0 else it
                }

                binding.styleRecycler.post {
                    binding.styleRecycler.smoothScrollToPosition(targetIndex)
                }
            }
        }
    }

    private fun setupSelectButton() {
        binding.selectButton.setOnClickListener {
            val style = adapter.styleAt(currentSnapPos)
            if (style == null) {
                startActivity(Intent(this, StyleExtractionActivity::class.java))
            } else {
                saveActiveStyle(style.styleId)
            }
        }
    }

    private fun updateSelectButtonText() {
        val style: UserStyle? = adapter.styleAt(currentSnapPos)
        val activeId = userStatsPrefs.getActiveStyleId()
        binding.selectButton.text = when {
            style == null -> "새 스타일 추출하기"
            style.styleId == activeId -> "✔ 선택됨"
            else -> "선택하기"
        }
    }

    fun saveActiveStyle(styleId: Long) = launch(Dispatchers.IO) {
        val current = userStatsPrefs.getActiveStyleId()
        if (current == styleId) {
            userStatsPrefs.saveActiveStyleId(1L)
        } else {
            userStatsPrefs.saveActiveStyleId(styleId)
        }
        withContext(Dispatchers.Main) {
            updateSelectButtonText()
            adapter.updateActiveStyleId(userStatsPrefs.getActiveStyleId())
        }
    }

    fun renameStyle(style: UserStyle, newName: String) = launch(Dispatchers.IO) {
        val updated = style.copy(styleName = newName)
        styleViewModel.updateStyle(updated)
    }

    fun deleteStyle(style: UserStyle) = launch(Dispatchers.IO) {
        styleViewModel.deleteStyle(style)
        if (style.styleId == userStatsPrefs.getActiveStyleId()) {
            userStatsPrefs.clearActiveStyleId()
        }
        withContext(Dispatchers.Main) { updateSelectButtonText() }
    }
}
