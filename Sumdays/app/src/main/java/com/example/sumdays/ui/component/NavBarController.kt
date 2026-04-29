package com.example.sumdays.ui.component

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import com.example.sumdays.CalendarActivity
import com.example.sumdays.DailyWriteActivity
import com.example.sumdays.ProfileActivity
import com.example.sumdays.R
import com.example.sumdays.ShopActivity
import com.example.sumdays.social.SocialActivity
import org.threeten.bp.LocalDate

enum class NavSource {
    CALENDAR,
    WRITE,
    READ,
    PROFILE,
    SEARCH,
    SOCIAL
}

class NavBarController(
    private val activity: Activity,
) {
    private val today: LocalDate by lazy { LocalDate.now() }

    private var centerRoot: View? = null

    fun setCenterSumIcon(drawableRes: Int) {
        val centerIcon = centerRoot?.findViewById<ImageView>(R.id.btnSum)
        centerIcon?.setImageResource(drawableRes)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun setNavigationBar(
        from: NavSource,
        sumIntentProvider: (() -> Intent)? = null
    ) {
        val btnCalendar = activity.findViewById<ImageButton>(R.id.btnCalendar)
        val btnSocial = activity.findViewById<ImageButton>(R.id.social_btn)
        val btnShop = activity.findViewById<ImageButton>(R.id.btnShop)
        val btnInfo = activity.findViewById<ImageButton>(R.id.btnInfo)

        val centerContainer =
            activity.findViewById<LinearLayout>(R.id.nav_center_container)
        centerContainer.removeAllViews()

        val resource = when (from) {
            NavSource.WRITE -> R.layout.include_nav_center_sum
            else -> R.layout.include_nav_center_write
        }

        LayoutInflater.from(activity).inflate(resource, centerContainer, true)

        centerRoot = centerContainer.getChildAt(0)

        val btnCenter = centerRoot?.findViewWithTag<View>("nav_center")

        btnCalendar.setOnClickListener {
            if (from != NavSource.CALENDAR) {
                activity.startActivity(
                    Intent(activity, CalendarActivity::class.java)
                )
                activity.overridePendingTransition(0, 0)
            }
        }

        btnSocial.setOnClickListener {
            if (from != NavSource.SOCIAL) {
                activity.startActivity(
                    Intent(activity, SocialActivity::class.java)
                )
                activity.overridePendingTransition(0, 0)
            }
        }

        btnCenter?.setOnClickListener {
            if (from == NavSource.WRITE) {
                val intent = requireNotNull(sumIntentProvider) {
                    "NavSource.WRITE requires sumIntentProvider"
                }.invoke()
                activity.startActivity(intent)
            } else {
                activity.startActivity(
                    Intent(activity, DailyWriteActivity::class.java)
                        .putExtra("date", today.toString())
                )
            }

            activity.overridePendingTransition(0, 0)
        }

        btnShop.setOnClickListener {
            if (from != NavSource.SEARCH) {
                activity.startActivity(
                    Intent(activity, ShopActivity::class.java)
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