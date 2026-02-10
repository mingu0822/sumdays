package com.example.sumdays.ui.component

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import com.example.sumdays.CalendarActivity
import com.example.sumdays.DailyWriteActivity
import com.example.sumdays.ProfileActivity
import com.example.sumdays.StatisticsActivity
import com.example.sumdays.R
import com.example.sumdays.SearchActivity
import org.threeten.bp.LocalDate



enum class NavSource {
    CALENDAR,
    WRITE,
    READ,
    PROFILE,
    SEARCH,
}

class NavBarController(
    private val activity: Activity,
) {
    private val today: LocalDate by lazy { LocalDate.now() }

    @RequiresApi(Build.VERSION_CODES.O)
    fun setNavigationBar(
        from : NavSource,
        sumIntentProvider: (() -> Intent)? = null
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
        btnCalendar.setOnClickListener {
            if(from != NavSource.CALENDAR) {
                activity.startActivity(
                    Intent(activity, CalendarActivity::class.java)
                )
                activity.overridePendingTransition(0, 0)
            }
        }

        btnStatistic.setOnClickListener {
            activity.startActivity(
                Intent(activity, StatisticsActivity::class.java)
            )
            activity.overridePendingTransition(0, 0)
        }

        btnCenter.setOnClickListener {
            if (from == NavSource.WRITE) {
                val intent = requireNotNull(sumIntentProvider) {
                    "NavSource.READ requires centerIntentProvider"
                }.invoke()
                activity.startActivity(intent)
            }
            else {
                activity.startActivity(
                    Intent(activity, DailyWriteActivity::class.java)
                        .putExtra("date", today.toString())
                )
            }

            activity.overridePendingTransition(0, 0)
        }

        btnSearch.setOnClickListener {
            if (from != NavSource.SEARCH) {
                activity.startActivity(
                    Intent(activity, SearchActivity::class.java)
                )
                activity.overridePendingTransition(0, 0)
            }
        }

        btnInfo.setOnClickListener {
            if (from != NavSource.PROFILE) {
                activity.startActivity(
                    Intent(activity, ProfileActivity::class.java)
                )
                activity.overridePendingTransition(0, 0)
            }
        }
    }
}
