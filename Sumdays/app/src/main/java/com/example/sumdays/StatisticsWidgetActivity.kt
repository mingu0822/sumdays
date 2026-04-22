package com.example.sumdays

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GestureDetectorCompat
import com.example.sumdays.utils.setupEdgeToEdge
import kotlin.math.abs

class StatisticsWidgetActivity : AppCompatActivity() {

    private lateinit var rootLayout: ConstraintLayout
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var swipeDownAnimator: ObjectAnimator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics_widget)
        rootLayout = findViewById(R.id.root_layout_widget)
        setupEdgeToEdge(rootLayout)

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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun onPause() {
        super.onPause()
        if (::swipeDownAnimator.isInitialized) swipeDownAnimator.cancel()
    }

    override fun onResume() {
        super.onResume()
        if (::swipeDownAnimator.isInitialized) swipeDownAnimator.start()
    }
}
