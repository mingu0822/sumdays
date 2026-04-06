package com.example.sumdays.statistics

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.sumdays.R
import com.example.sumdays.data.viewModel.DailyEntryViewModel
import com.example.sumdays.databinding.ActivityWeekStatsDetailBinding
import com.example.sumdays.utils.setupEdgeToEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate

class WeekStatsDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeekStatsDetailBinding
    private lateinit var weekSummary: WeekSummary

    private val dailyEntryViewModel: DailyEntryViewModel by viewModels()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Intent에서 데이터 받기
        weekSummary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("week_summary", WeekSummary::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("week_summary")
        } ?: run {
            // 데이터가 없을 경우 임시로 Activity를 종료하거나 오류 처리
            Toast.makeText(this, "통계 데이터를 불러올 수 없습니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding = ActivityWeekStatsDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setupReportViews()

        val rootView = findViewById<View>(R.id.main_detail)
        setupEdgeToEdge(rootView)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupReportViews() {

        binding.weekRangeTextView.text = "${weekSummary.startDate} ~ ${weekSummary.endDate}"
        binding.summaryContentTextView.text = weekSummary.summary.overview
        binding.feedbackContentTextView.text = weekSummary.insights.advice

        // 요일 표시 영역 설정
        val startDate = LocalDate.parse(weekSummary.startDate)

        lifecycleScope.launch {
            // 작성된 모든 날짜 가져오기
            val writtenDatesSet = withContext(Dispatchers.IO) {
                dailyEntryViewModel.getAllWrittenDates().toSet()
            }

            binding.dayRow1.removeAllViews()
            binding.dayRow2.removeAllViews()

            binding.dayRow1.weightSum = 5.0f
            binding.dayRow2.weightSum = 5.0f

            val dayNames = listOf("월", "화", "수", "목", "금", "토", "일")

            for (i in 0 until 7) {
                val date = startDate.plusDays(i.toLong())
                val dayOfWeek = date.dayOfWeek

                // 실제 DB에 저장된 날짜인지 확인
                val isDiaryWritten = writtenDatesSet.contains(date.toString())

                // 레이아웃 인플레이트
                val dayLayout = LayoutInflater.from(this@WeekStatsDetailActivity)
                    .inflate(R.layout.include_day_cell_static, null) as FrameLayout
                val dayTextView: TextView = dayLayout.findViewById(R.id.day_name_text)

                dayTextView.text = dayNames[i % 7]

                // 테두리 적용 (일기 쓴 날이면 주황색 테두리)
                if (isDiaryWritten) {
                    dayLayout.setBackgroundResource(R.drawable.statistics_shape_fox_orange_border)
                } else {
                    dayLayout.setBackgroundResource(android.R.color.transparent)
                }

                // LayoutParams 설정
                val layoutParams = LinearLayout.LayoutParams(
                    0,
                    200,
                    1.0f
                ).apply {
                    marginStart = 4.dp
                    marginEnd = 4.dp
                    bottomMargin = 8.dp
                }
                dayLayout.layoutParams = layoutParams

                if (dayOfWeek <= DayOfWeek.FRIDAY) {
                    binding.dayRow1.addView(dayLayout)
                } else {
                    binding.dayRow2.addView(dayLayout)
                }
            }
        }
    }

    private fun setupListeners() {
        // 뒤로가기 버튼
        binding.backButton.setOnClickListener {
            finish()
        }
    }

    // DP를 Pixel로 변환
    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density + 0.5f).toInt()
}