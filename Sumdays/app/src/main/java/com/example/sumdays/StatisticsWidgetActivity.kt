package com.example.sumdays

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.lifecycleScope
import com.example.sumdays.data.viewModel.WeekSummaryViewModel
import com.example.sumdays.data.viewModel.WeekSummaryViewModelFactory
import com.example.sumdays.statistics.StreakPrefs
import com.example.sumdays.utils.setupEdgeToEdge
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatisticsWidgetActivity : AppCompatActivity() {

    private lateinit var rootLayout: ConstraintLayout
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var swipeDownAnimator: ObjectAnimator

    private val weekSummaryViewModel: WeekSummaryViewModel by viewModels {
        WeekSummaryViewModelFactory((application as MyApplication).weekSummaryRepository)
    }

    private lateinit var tvStreakCount: TextView
    private lateinit var tvLeafCount: TextView
    private lateinit var tvGrapeCount: TextView
    private lateinit var ivFoxTreeBg: ImageView
    private lateinit var ivFox: ImageView
    private lateinit var tvLevelsToNextBg: TextView
    private lateinit var cardFoxTree: CardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics_widget)
        rootLayout = findViewById(R.id.root_layout_widget)
        setupEdgeToEdge(rootLayout)

        tvStreakCount   = findViewById(R.id.tv_streak_count)
        tvLeafCount     = findViewById(R.id.tv_leaf_count)
        tvGrapeCount    = findViewById(R.id.tv_grape_count)
        ivFoxTreeBg      = findViewById(R.id.iv_fox_tree_bg)
        ivFox            = findViewById(R.id.iv_fox)
        tvLevelsToNextBg = findViewById(R.id.tv_levels_to_next_bg)
        cardFoxTree     = findViewById(R.id.card_fox_tree)

        cardFoxTree.setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java))
        }

        gestureDetector = GestureDetectorCompat(this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?, e2: MotionEvent,
                    velocityX: Float, velocityY: Float
                ): Boolean {
                    val start = e1 ?: return false
                    val dy = e2.y - start.y
                    val dx = e2.x - start.x
                    if (dy > 100f && abs(dy) > abs(dx) * 1.5f && velocityY > 300f) {
                        finish()
                        overridePendingTransition(R.anim.slide_in_down, R.anim.slide_out_down)
                        return true
                    }
                    return false
                }

                override fun onDown(e: MotionEvent) = true
            })

        setupSwipeDownHint()
        loadWidgetData()
    }

    private fun setupSwipeDownHint() {
        val pill = findViewById<View>(R.id.swipe_down_hint)
        swipeDownAnimator = ObjectAnimator.ofFloat(pill, "translationY", 0f, 12f).apply {
            duration = 600
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        }
        pill.postDelayed({ swipeDownAnimator.start() }, 300)
    }

    private fun loadWidgetData() {
        lifecycleScope.launch {
            val weekSummaries = withContext(Dispatchers.IO) {
                val dates = weekSummaryViewModel.getAllDatesAsc()
                dates.mapNotNull { weekSummaryViewModel.getSummary(it) }
            }
            val streak     = StreakPrefs.getStreak(this@StatisticsWidgetActivity)
            val leafCount  = weekSummaries.size
            val grapeCount = leafCount / 5

            tvStreakCount.text = streak.toString()
            tvLeafCount.text   = leafCount.toString()
            tvGrapeCount.text  = grapeCount.toString()
            bindFoxTreeWidget(leafCount)
        }
    }

    private fun bindFoxTreeWidget(leafCount: Int) {
        val backgrounds = listOf(
            R.drawable.statistics_background_morning,
            R.drawable.statistics_background_evening,
            R.drawable.statistics_background_stratosphere,
            R.drawable.statistics_background_space
        )
        val grapeCount = leafCount / 5
        val bgIndex = (grapeCount / 5).coerceIn(0, backgrounds.lastIndex)
        ivFoxTreeBg.setImageResource(backgrounds[bgIndex])

        // 다음 배경 전환까지 남은 층수 (배경 1개당 25층)
        if (bgIndex < backgrounds.lastIndex) {
            val nextBgLeafThreshold = (bgIndex + 1) * 25
            val remaining = nextBgLeafThreshold - leafCount
            tvLevelsToNextBg.text = "다음 배경까지 ${remaining}층"
        } else {
            tvLevelsToNextBg.text = "우주 도착!"
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun onPause() {
        super.onPause()
        if (::swipeDownAnimator.isInitialized) swipeDownAnimator.cancel()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            StreakPrefs.refreshOnOpen(this)
        }
        if (::swipeDownAnimator.isInitialized) swipeDownAnimator.start()
        loadWidgetData()
    }
}
