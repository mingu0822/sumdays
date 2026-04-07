package com.example.sumdays

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.Glide

class TutorialActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager
    private lateinit var pagerAdapter: TutorialPagerAdapter
    private lateinit var dotsLayout: LinearLayout
    private lateinit var btnSkip: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton

    private lateinit var dots: Array<TextView?>
    private lateinit var layouts: IntArray

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_tutorial)

        // View 바인딩
        viewPager = findViewById(R.id.view_pager)
        dotsLayout = findViewById(R.id.layoutDots)
        btnSkip = findViewById(R.id.btn_skip)
        btnNext = findViewById(R.id.btn_next)
        btnPrev = findViewById(R.id.btn_prev)


        // 페이지 레이아웃들
        layouts = intArrayOf(
            R.layout.tutorial_page1,
            R.layout.tutorial_page2,
            R.layout.tutorial_page3,
            R.layout.tutorial_page4,
            R.layout.tutorial_page5
        )

        // 하단 점 초기화
        addBottomDots(0)

        // ViewPager 세팅
        pagerAdapter = TutorialPagerAdapter()
        viewPager.adapter = pagerAdapter
        viewPager.addOnPageChangeListener(viewPagerPageChangeListener)

        // Skip 버튼 → 메인(캘린더)으로
        btnSkip.setOnClickListener {
            launchMain()
        }

        // Next 버튼
        btnNext.setOnClickListener {
            val next = getItem(+1)
            if (next < layouts.size) {
                // 마지막 페이지 전까지는 다음 페이지로
                viewPager.currentItem = next
            } else {
                // 마지막 페이지에서는 메인으로
                launchMain()
            }
        }

        btnPrev.setOnClickListener {
            val prev = getItem(-1)
            if (prev >= 0) {
                // 마지막 페이지 전까지는 다음 페이지로
                viewPager.currentItem = prev
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // 뒤로가기 눌러도 아무 일도 안 일어남
                }
            }
        )
    }

    private fun launchMain() {
        finish()
    }

    // 하단 점(도트) UI
    private fun addBottomDots(currentPage: Int) {
        dots = arrayOfNulls(layouts.size)

        val colorsActive = resources.getIntArray(R.array.array_dot_active)
        val colorsInactive = resources.getIntArray(R.array.array_dot_inactive)

        dotsLayout.removeAllViews()
        for (i in dots.indices) {
            val inactiveColor = colorsInactive.getOrElse(currentPage) {
                colorsInactive.lastOrNull() ?: Color.GRAY
            }

            dots[i] = TextView(this).apply {
                // 하단 점 문자
                text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml("&#8226;", Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml("&#8226;")
                }
                textSize = 35f
                setTextColor(inactiveColor)
            }
            dotsLayout.addView(dots[i])
        }

        if (dots.isNotEmpty()) {
            val activeColor = colorsActive.getOrElse(currentPage) {
                colorsActive.lastOrNull() ?: Color.WHITE
            }
            dots[currentPage]?.setTextColor(activeColor)
        }
    }

    private fun getItem(offset: Int): Int = viewPager.currentItem + offset

    // 페이지 변경 리스너
    private val viewPagerPageChangeListener = object : ViewPager.OnPageChangeListener {
        override fun onPageSelected(position: Int) {
            addBottomDots(position)
        }

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
        ) {}

        override fun onPageScrollStateChanged(state: Int) {}
    }

    // ViewPager 어댑터
    inner class TutorialPagerAdapter : PagerAdapter() {

        private val inflater: LayoutInflater by lazy {
            getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val view = inflater.inflate(layouts[position], container, false)

            // ★ 3번째 페이지(tutorial_page3)에서 GIF 재생
            // 0: page1, 1: page2, 2: page3
            if (position == 2) {
                val gifView = view.findViewById<ImageView>(R.id.gifView)
                if (gifView != null) {
                    Glide.with(this@TutorialActivity)
                        .asGif()
                        .load(R.drawable.tutorial_03)   // drawable에 있는 GIF 리소스
                        .into(gifView)
                }
            }

            container.addView(view)
            return view
        }

        override fun getCount(): Int = layouts.size

        override fun isViewFromObject(view: View, obj: Any): Boolean = (view === obj)

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            container.removeView(obj as View)
        }
    }
}
