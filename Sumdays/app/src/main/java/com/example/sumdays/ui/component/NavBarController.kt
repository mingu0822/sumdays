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
import com.example.sumdays.CustomizeActivity
import com.example.sumdays.DailyWriteActivity
import com.example.sumdays.ProfileActivity
import com.example.sumdays.R
import com.example.sumdays.ShopActivity
import com.example.sumdays.social.SocialActivity
import org.threeten.bp.LocalDate
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
enum class NavSource {
    CALENDAR,
    WRITE,
    READ,
    PROFILE,
    SEARCH,
    SOCIAL,
    SHOP,
    CUSTOMIZE,
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
                    Intent(activity, CalendarActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
                activity.overridePendingTransition(0, 0)
                activity.finish() // 현재 탭 종료하여 스택 정리
            }
        }

        btnSocial.setOnClickListener {
            if (from != NavSource.SOCIAL) {
                activity.startActivity(
                    Intent(activity, SocialActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                )
                activity.overridePendingTransition(0, 0)
            }
        }

        btnShop.setOnClickListener {
            if (from != NavSource.SHOP) {
                activity.startActivity(
                    Intent(activity, ShopActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                )
                activity.overridePendingTransition(0, 0)
            }
        }

        btnInfo.setOnClickListener {
            if (from != NavSource.CUSTOMIZE) {
                activity.startActivity(
                    Intent(activity, CustomizeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
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

        }
    }
}
fun ComponentActivity.setupBackToCalendar() {
    onBackPressedDispatcher.addCallback(this) {
        val intent = Intent(this@setupBackToCalendar, CalendarActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}