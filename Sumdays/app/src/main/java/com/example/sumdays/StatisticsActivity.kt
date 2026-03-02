package com.example.sumdays

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.statistics.WeekStatsDetailActivity
import com.example.sumdays.statistics.WeekSummary
import com.example.sumdays.ui.TreeTiledDrawable
import com.example.sumdays.utils.setupEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.sumdays.data.viewModel.DailyEntryViewModel
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.sumdays.data.viewModel.WeekSummaryViewModel
import com.example.sumdays.data.viewModel.WeekSummaryViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.LocalDate

class StatisticsActivity : AppCompatActivity() {
    private lateinit var viewModel: DailyEntryViewModel
    private val weekSummaryViewModel: WeekSummaryViewModel by viewModels {
        WeekSummaryViewModelFactory(
            (application as MyApplication).weekSummaryRepository
        )
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var lm: LinearLayoutManager
    private lateinit var treeDrawable: TreeTiledDrawable
    private lateinit var btnMoveToLatestLeaf: ImageButton
    private lateinit var btnMoveToBottom: ImageButton
    private lateinit var tvStrikeCount: TextView
    private lateinit var tvLeafCount: TextView
    private lateinit var tvGrapeCount: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var loadingOverlay: View
    private lateinit var loadingGifView: ImageView

    private var bgScrollY = 0f      // 배경 전환용
    private var treeScrollY = 0f    // 나무 줄기 타일용
    private var segmentScroll = 8000f  // 어느 정도 스크롤하면 완전히 bg2로 변할지

    private var backgrounds = listOf<Int>(
        R.drawable.statistics_background_morning,
        R.drawable.statistics_background_evening,
        R.drawable.statistics_background_stratosphere,
        R.drawable.statistics_background_space)

    // 전체 스크롤 범위 = (배경 개수 - 1) * segmentScroll
    private val maxScrollForTransition: Float
        get() = segmentScroll * (backgrounds.size - 1)

    // 현재 어떤 구간(배경 i ↔ i+1)을 쓰고 있는지
    private var currentSegmentIndex: Int = -1

    // WeekSummary 데이터 목록 (최대 인덱스 = 최신 데이터)
    private var weekSummaries: List<WeekSummary> = emptyList()

    private lateinit var adapter: LeafAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        loadingOverlay = findViewById(R.id.loading_overlay)
        loadingGifView = findViewById(R.id.loading_gif_view)
        showLoading(true)

        viewModel = ViewModelProvider(this).get(DailyEntryViewModel::class.java)

        val bg1 = findViewById<ImageView>(R.id.statistics_background_1)
        val bg2 = findViewById<ImageView>(R.id.statistics_background_2)

        // 초기 배경은 리스트 첫 번째로
        if (backgrounds.isNotEmpty()) {
            bg1.setImageResource(backgrounds[0])
            bg2.setImageResource(backgrounds.getOrNull(1) ?: backgrounds[0])
            bg1.alpha = 1f
            bg2.alpha = 0f
            currentSegmentIndex = 0
        }


        recyclerView = findViewById(R.id.recyclerView)
        btnMoveToLatestLeaf = findViewById(R.id.btn_move_to_latest_leaf)
        btnMoveToBottom = findViewById(R.id.btn_move_to_bottom_leaf)

        // 1) 레이아웃 매니저: 바닥에서 시작
        lm = LinearLayoutManager(this, RecyclerView.VERTICAL, false).apply {
            stackFromEnd = true
        }
        recyclerView.layoutManager = lm
        recyclerView.overScrollMode = View.OVER_SCROLL_NEVER


        // 2) 배경: 무한 타일
        val bmp = BitmapFactory.decodeResource(resources, R.drawable.statistics_tree_stem)
        treeDrawable = TreeTiledDrawable(
            bitmap = bmp
        )
        recyclerView.background = treeDrawable

        initHeaderViews()

        // 3) 스크롤 리스너: 위로 갈수록 prepend로 확장
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                handleScrollForBackground(bg1, bg2, dy, rv.width)
                maybePrependMore()
            }
        })

        // 상태바, 네비게이션바 같은 색으로
        setupEdgeToEdge(recyclerView)

        lifecycleScope.launch {

            // 비동기로 DB 데이터 가져오기
            weekSummaries = loadWeekSummariesFromDB()

            // 가져온 데이터의 실제 개수
            val currentDataCount = weekSummaries.size

            // 어댑터 연결 (데이터가 준비된 후 실행)
            adapter = LeafAdapter(
                weekSummaries = weekSummaries,
                currentStatsNumber = currentDataCount
            )
            recyclerView.adapter = adapter

            // 헤더 업데이트 (데이터 개수 반영)
            updateStatisticsHeader()

            // 초기 스크롤 위치 설정 (데이터 개수 기반)
            val itemHeightPx = (resources.displayMetrics.density * 160 + 0.5f).toInt()

            // 데이터가 적어 음수가 되는 것을 방지 (최소 0)
            val scrollDistanceY = (currentDataCount - 2).coerceAtLeast(0) * itemHeightPx

            recyclerView.post {
                if (scrollDistanceY > 0) {
                    recyclerView.scrollBy(0, -scrollDistanceY)
                }
            }

            // 버튼 클릭 리스너: 스크롤 상하 이동
            btnMoveToLatestLeaf.setOnClickListener {
                recyclerView.post {
                    recyclerView.scrollBy(0, -itemHeightPx * (currentDataCount + 10))
                    recyclerView.scrollBy(0, itemHeightPx * 7)
                }
            }

            btnMoveToBottom.setOnClickListener {
                recyclerView.post {
                    recyclerView.scrollBy(0, itemHeightPx * (currentDataCount + 10))
                }
            }
        }
        loadingOverlay.post {
            showLoading(false)
        }
    }

    override fun onResume() {
        super.onResume()

        StreakPrefs.refreshOnOpen(this)              // 만료 체크
        val streak = StreakPrefs.getStreak(this)     // 최신 streak 읽기
        tvStrikeCount.text = "🔥: ${streak}"
    }

    private fun showLoading(isLoading: Boolean) {
        runOnUiThread {
            if (isLoading) {
                loadingOverlay.visibility = View.VISIBLE
                loadingGifView.visibility = View.VISIBLE
                // Glide로 GIF 로드
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

    // DB 데이터 로딩 헬퍼 함수
    private suspend fun loadWeekSummariesFromDB(): List<WeekSummary> = withContext(Dispatchers.IO) {
        // 1. 날짜 목록 가져오기
        val dates = weekSummaryViewModel.getAllDatesAsc()
        // 2. WeekSummary 객체로 변환
        val summaries = dates.mapNotNull { weekSummaryViewModel.getSummary(it) }
        summaries
    }

    private fun initHeaderViews() {
        // 뷰 참조
        btnBack = findViewById(R.id.btn_back)
        tvStrikeCount = findViewById(R.id.tv_strike_count)
        tvLeafCount = findViewById(R.id.tv_leaf_count)
        tvGrapeCount = findViewById(R.id.tv_grape_count)

        // 1. 뒤로 가기 버튼 기능
        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun updateStatisticsHeader() {

        // 1. 연속 일기 작성 횟수 (스트라이크)
        calculateAndDisplayStreak()

        // 2. 나뭇잎(주간 요약) 개수
        val leafCount = weekSummaries.size

        // 3. 포도 개수
        val grapeCount = leafCount/5

        // UI 업데이트
        tvLeafCount.text = "🍃: ${leafCount}"
        tvGrapeCount.text = "🍇: ${grapeCount}"
    }

    private fun calculateAndDisplayStreak() {
        // CoroutineScope를 사용하여 백그라운드 (IO 스레드)에서 DB 접근 및 계산 수행
        CoroutineScope(Dispatchers.IO).launch {

            // 1. Room에서 모든 작성 날짜(String)를 가져옵니다.
            val allDates = viewModel.getAllWrittenDates()

            // 2. Strike 횟수를 가져옵니다.
            val streak = StreakPrefs.getStreak(this@StatisticsActivity)

            // 3. Main 스레드에서 UI 업데이트
            withContext(Dispatchers.Main) {
                tvStrikeCount.text = "🔥: ${streak}"
            }
        }
    }


    private fun handleScrollForBackground(bg1: ImageView, bg2: ImageView, dy: Int, rvWidth: Int) {
        // ---------- 배경 전환용 스크롤 (위로 올릴수록 값 증가) ----------
        // 위로 스크롤: dy < 0 → bgScrollY 증가
        // 아래로 스크롤: dy > 0 → bgScrollY 감소
        bgScrollY += -dy
        bgScrollY = bgScrollY.coerceIn(0f, maxScrollForTransition)

        if (backgrounds.size > 1) {
            val progress = bgScrollY / segmentScroll      // 0 ~ (N-1)
            val segmentIndex = progress.toInt().coerceIn(0, backgrounds.size - 2)
            val localRawT = (progress - segmentIndex).coerceIn(0f, 1f)

            if (segmentIndex != currentSegmentIndex) {
                bg1.setImageResource(backgrounds[segmentIndex])
                bg2.setImageResource(backgrounds[segmentIndex + 1])
                currentSegmentIndex = segmentIndex
            }

            // 각 세그먼트 내에서만 "짧은 전환"
            val transitionWidth = 0.2f
            val start = 0.5f - transitionWidth / 2f
            val end   = 0.5f + transitionWidth / 2f

            val localSharpT = when {
                localRawT <= start -> 0f
                localRawT >= end   -> 1f
                else -> (localRawT - start) / (end - start)
            }

            bg1.alpha = 1f - localSharpT
            bg2.alpha = localSharpT
        }

        // ---------- 나무 줄기 스크롤 (RecyclerView와 같은 방향) ----------
        // RecyclerView는 dy<0 이면 "위로 스크롤" → 아이템들이 아래로 이동
        treeScrollY += dy
        treeDrawable.setScroll(treeScrollY, rvWidth)
    }


    /** 리스트 상단 가까이 오면 앞쪽으로 아이템을 붙여 위로 무한 확장 */
    private fun maybePrependMore() {
        val firstPos = lm.findFirstVisibleItemPosition()
        if (firstPos <= 50) { // 상단 임계치
            val firstView = lm.findViewByPosition(firstPos)
            val offsetTop = firstView?.top ?: 0

            val request = 800 // 한 번에 시도할 개수
            val added = adapter.prepend(request)   // 실제로 얼마나 붙었는지 받기

            if (added > 0) {
                // prepend 전 보던 아이템이 동일 위치로 오도록 보정
                lm.scrollToPositionWithOffset(firstPos + added, offsetTop)
            }
        }
    }



    /** 어댑터 클래스: WeekSummary 데이터를 받도록 수정 */
    private class LeafAdapter(
        private val weekSummaries: List<WeekSummary>, // WeekSummary 데이터 목록 (index 1부터 순서대로)
        private val currentStatsNumber: Int
    ) : RecyclerView.Adapter<LeafAdapter.VH>() {

        private data class LeafItem(val index: Int)

        private val items = mutableListOf<LeafItem>()
        private var nextIndex: Int

        private var maxLeafIndex: Int

        init {
            // maxLeafIndex는 데이터 개수 + 여분 가지 (10개)
            maxLeafIndex = currentStatsNumber + 10

            for (i in maxLeafIndex downTo 1) {
                items.add(LeafItem(i))
            }
            nextIndex = maxLeafIndex + 1
        }

        /** 위로 스크롤하다가 더 필요할 때, 위쪽에 잎 추가 */
        fun prepend(requestCount: Int): Int {
            if (requestCount <= 0) return 0

            // 아직 만들 수 있는 잎 개수
            val remaining = maxLeafIndex - (nextIndex - 1)
            if (remaining <= 0) return 0   // 한계 도달 → 더 이상 안 붙임

            val toAdd = minOf(requestCount, remaining)

            val newItems = (nextIndex + toAdd - 1 downTo nextIndex).map { LeafItem(it) }
            items.addAll(0, newItems)
            nextIndex += toAdd

            notifyItemRangeInserted(0, toAdd)
            return toAdd
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_leaf, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val leafIndex = items[position].index // 70(위) ~ 1(아래)

            // LeafItem의 index가 실제 통계 데이터 범위 내에 있는지 확인
            val hasData = leafIndex <= currentStatsNumber && leafIndex >= 1
            val weekSummary = if (hasData) {
                // weekSummaries는 index 1부터 순서대로 저장되어 있으므로, index-1을 사용
                weekSummaries.getOrNull(leafIndex - 1)
            } else {
                null
            }

            // isOnlyBranch: 데이터가 없는 단순 가지
            val isOnlyBranch = !hasData

            val leafLP = holder.buttonWeeklyStats.layoutParams as FrameLayout.LayoutParams
            val foxLP = holder.foxOnBranchImage.layoutParams as FrameLayout.LayoutParams

            val isLeft = (leafIndex % 2 == 0)
            val isGrapeRow = (leafIndex % 5 == 0)

            // 인덱스 설정 (나뭇잎 위)
            holder.leafIndexText.text = leafIndex.toString()

            // 시작 날짜 설정 (데이터가 있을 때만 표시)
            if (hasData && weekSummary != null) {
                holder.leafStartDateText.text = "${weekSummary.startDate}\n~${weekSummary.endDate}"
                holder.leafStartDateText.visibility = View.VISIBLE
            } else {
                holder.leafStartDateText.text = ""
                holder.leafStartDateText.visibility = View.GONE
            }

            // 여우 마스코트는 가장 최신 데이터 위에 배치
           if (leafIndex == currentStatsNumber) {
                holder.foxOnBranchImage.visibility = View.VISIBLE
            }
            else {
                holder.foxOnBranchImage.visibility = View.GONE
            }

            val indexLP = holder.leafIndexText.layoutParams as FrameLayout.LayoutParams
            val dateLP = holder.leafStartDateText.layoutParams as FrameLayout.LayoutParams

            indexLP.gravity = Gravity.CENTER_HORIZONTAL
            indexLP.leftMargin = 0
            indexLP.rightMargin = 0

            if (isLeft) {
                leafLP.gravity = Gravity.START
                foxLP.gravity = Gravity.START
                dateLP.gravity = Gravity.START
                if (isOnlyBranch){
                    if (isGrapeRow){
                        holder.buttonWeeklyStats.setImageResource(R.drawable.statistics_grape_with_branch_left)
                        holder.buttonWeeklyStats.isEnabled = false
                        holder.leafStartDateText.text = ""
                        holder.leafIndexText.text = ""
                    }
                    else{
                        holder.buttonWeeklyStats.setImageResource(R.drawable.statistics_branch_left)
                        holder.buttonWeeklyStats.isEnabled = false
                        holder.leafStartDateText.text = ""
                        holder.leafIndexText.text = ""
                    }
                }
                else if (isGrapeRow) {
                    holder.buttonWeeklyStats.setImageResource(R.drawable.statistics_grape_with_branch_left)
                    holder.buttonWeeklyStats.isEnabled = true

                    dateLP.topMargin = holder.dp(110)
                    dateLP.leftMargin = holder.dp(40)
                }
                else {
                    holder.buttonWeeklyStats.setImageResource(R.drawable.statistics_leaf_left)
                    holder.buttonWeeklyStats.isEnabled = true

                    dateLP.topMargin = holder.dp(65)
                    dateLP.leftMargin = holder.dp(40)
                }
            }
            else { // isRight
                leafLP.gravity = Gravity.END
                foxLP.gravity = Gravity.END
                dateLP.gravity = Gravity.END
                if (isOnlyBranch){
                    if (isGrapeRow){
                        holder.buttonWeeklyStats.setImageResource(R.drawable.statistics_grape_with_branch_right)
                        holder.buttonWeeklyStats.isEnabled = false
                        holder.leafStartDateText.text = ""
                        holder.leafIndexText.text = ""
                    }
                    else{
                        holder.buttonWeeklyStats.setImageResource(R.drawable.statistics_branch_right)
                        holder.buttonWeeklyStats.isEnabled = false
                        holder.leafStartDateText.text = ""
                        holder.leafIndexText.text = ""
                    }
                }
                else if (isGrapeRow) {
                    holder.buttonWeeklyStats.setImageResource(R.drawable.statistics_grape_with_branch_right)
                    holder.buttonWeeklyStats.isEnabled = true

                    dateLP.topMargin = holder.dp(110)
                    dateLP.rightMargin = holder.dp(40)
                }
                else {
                    holder.buttonWeeklyStats.setImageResource(R.drawable.statistics_leaf_right)
                    holder.buttonWeeklyStats.isEnabled = true

                    dateLP.topMargin = holder.dp(65)
                    dateLP.rightMargin = holder.dp(40)
                }
            }
            holder.buttonWeeklyStats.layoutParams = leafLP
            holder.foxOnBranchImage.layoutParams = foxLP

            // 버튼 클릭 리스너 업데이트 (데이터가 있을 때만 호출)
            if (hasData && weekSummary != null) {
                holder.buttonWeeklyStats.setOnClickListener {
                    val intent = Intent(holder.itemView.context, WeekStatsDetailActivity::class.java)

                    // WeekSummary 객체를 Parcelable로 담아 전달
                    intent.putExtra("week_summary", weekSummary)

                    holder.itemView.context.startActivity(intent)
                }
                holder.buttonWeeklyStats.isEnabled = true
            } else {
                holder.buttonWeeklyStats.setOnClickListener(null)
                holder.buttonWeeklyStats.isEnabled = false
            }
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val buttonWeeklyStats: ImageButton = view.findViewById(R.id.btnWeeklyStats)
            val foxOnBranchImage: ImageView = view.findViewById(R.id.fox_on_branch)
            val leafIndexText: TextView = view.findViewById(R.id.text_leaf_index)
            val leafStartDateText: TextView = view.findViewById(R.id.text_leaf_startdate)

            fun dp(v: Int): Int =
                (itemView.resources.displayMetrics.density * v + 0.5f).toInt()
        }
    }
}