package com.example.sumdays.nav

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import com.example.sumdays.DailyWriteActivity
import com.example.sumdays.SettingsActivity
import com.example.sumdays.StatisticsActivity
import com.example.sumdays.R
import org.threeten.bp.LocalDate



enum class NavSource {
    CALENDAR,
    WRITE,
    READ
}

class NavBarController(
    private val activity: Activity,
) {
    private val today: LocalDate by lazy { LocalDate.now() }
    fun setNavigationBar(
        from : NavSource
    ) {

        val btnCalendar = activity.findViewById<ImageButton>(R.id.btnCalendar)
        val btnStatistic = activity.findViewById<ImageButton>(R.id.statistic_btn)
        val btnSearch = activity.findViewById<ImageButton>(R.id.btnSearch)
        val btnInfo = activity.findViewById<ImageButton>(R.id.btnInfo)

        // center는 따로
        val centerContainer =
            activity.findViewById<LinearLayout>(R.id.nav_center_container)
        centerContainer.removeAllViews()
        val resource = when (from) {
            NavSource.WRITE -> R.layout.include_nav_center_sum
            else -> R.layout.include_nav_center_write
        }
        val centerRoot = LayoutInflater.from(activity)
            .inflate(resource, centerContainer, true)
        val btnCenter = centerRoot.findViewWithTag<View>("nav_center")

        // callback 함수 지정
        btnCalendar.setOnClickListener { }

        btnStatistic.setOnClickListener {
            activity.startActivity(
                Intent(activity, StatisticsActivity::class.java)
            )
            activity.overridePendingTransition(0, 0)
        }

        btnCenter.setOnClickListener {
            activity.startActivity(
                Intent(activity, DailyWriteActivity::class.java)
                    .putExtra("date", today.toString())
            )
            activity.overridePendingTransition(0, 0)
        }

        btnSearch.setOnClickListener { /* 미정 */ }
        btnInfo.setOnClickListener {
            activity.startActivity(
                Intent(activity, SettingsActivity::class.java)
            )
            activity.overridePendingTransition(0, 0)
        }
    }
}
