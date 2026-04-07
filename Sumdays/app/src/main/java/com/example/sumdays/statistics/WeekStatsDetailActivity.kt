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
        setupReportTexts()
        setupReportDayCells()
    }

    private fun setupReportTexts() {
        binding.weekRangeTextView.text = "${weekSummary.startDate} ~ ${weekSummary.endDate}"
        binding.summaryContentTextView.text = weekSummary.summary.overview
        binding.feedbackContentTextView.text = weekSummary.insights.advice
    }

    private fun setupReportDayCells() {
        val startDate = LocalDate.parse(weekSummary.startDate)

        lifecycleScope.launch {
            // 일기가 작성된 날짜 가져오기
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
                val isWritten = writtenDatesSet.contains(date.toString()) // 일기가 작성된 날인지 확인
                val dayCell = buildDayCell(dayNames[i], isWritten)

                // 1열에는 월~금, 2열에는 토,일을 배치함
                if (date.dayOfWeek <= DayOfWeek.FRIDAY) {
                    binding.dayRow1.addView(dayCell)
                } else {
                    binding.dayRow2.addView(dayCell)
                }
            }
        }
    }

    private fun buildDayCell(dayName: String, isWritten: Boolean): FrameLayout {
        // 요일명 표시
        val dayCell = LayoutInflater.from(this@WeekStatsDetailActivity)
            .inflate(R.layout.include_day_cell_static, null) as FrameLayout
        dayCell.findViewById<TextView>(R.id.day_name_text).text = dayName

        // 테두리 적용 (일기 쓴 날이면 주황색 테두리)
        dayCell.setBackgroundResource(
            if (isWritten) R.drawable.statistics_shape_fox_orange_border
            else android.R.color.transparent
        )

        // 레이아웃 파라미터 설정
        dayCell.layoutParams = LinearLayout.LayoutParams(
            0,
            200,
            1.0f
        ).apply {
            marginStart = 4.dp
            marginEnd = 4.dp
            bottomMargin = 8.dp
        }

        return dayCell
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