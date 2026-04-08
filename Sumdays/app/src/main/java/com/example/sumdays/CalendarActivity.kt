package com.example.sumdays

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.viewpager2.widget.ViewPager2
import com.example.sumdays.calendar.CalendarLanguage
import com.example.sumdays.calendar.MonthAdapter
import com.example.sumdays.data.viewModel.CalendarViewModel
import com.example.sumdays.shop.AllFoxMap
import com.example.sumdays.shop.AllThemeMap
import com.example.sumdays.theme.FoxRepository
import com.example.sumdays.theme.Theme
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource
import com.example.sumdays.utils.setupEdgeToEdge
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.jakewharton.threetenabp.AndroidThreeTen
import org.threeten.bp.LocalDate
import org.threeten.bp.YearMonth
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.ChronoUnit
import java.util.Locale

class CalendarActivity : AppCompatActivity() {

    private lateinit var calendarViewPager: ViewPager2
    private lateinit var tvMonthYear: TextView
    private lateinit var monthAdapter: MonthAdapter
    private lateinit var btnPrevMonth: ImageButton
    private lateinit var btnNextMonth: ImageButton
    private lateinit var btnSetting: ImageButton
    private lateinit var btnTutorial: ImageButton

    private lateinit var btnSearch: ImageButton
    private lateinit var navBarController: NavBarController
    private lateinit var rootLayout: ConstraintLayout

    private lateinit var btnStatistics: ImageButton

    private val viewModel: CalendarViewModel by viewModels()

    var currentStatusMap: Map<String, Pair<Boolean, String?>> = emptyMap()
    private var currentMonthLiveData: LiveData<Map<String, Pair<Boolean, String?>>>? = null
    private var currentLanguage: CalendarLanguage = CalendarLanguage.KOREAN

    private val today: LocalDate by lazy { LocalDate.now() }
    private val CENTER_POSITION = Int.MAX_VALUE / 2

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureDefaultOwned()
        updateOwned()
        setContentView(R.layout.activity_calendar)
        AndroidThreeTen.init(this)

        calendarViewPager = findViewById(R.id.calendarViewPager)
        tvMonthYear = findViewById(R.id.tv_month_year)
        btnPrevMonth = findViewById(R.id.btn_prev_month)
        btnNextMonth = findViewById(R.id.btn_next_month)
        btnSetting = findViewById(R.id.setting_menu)
        btnSearch = findViewById(R.id.search_btn)
        btnTutorial = findViewById(R.id.tutorial_btn)
        btnStatistics = findViewById(R.id.CalenderView_Statistics)
        rootLayout = findViewById(R.id.root_layout)

        navBarController = NavBarController(this)
        navBarController.setNavigationBar(NavSource.CALENDAR)

        setCustomCalendar()
        applyThemeModeSettings()
        setupEdgeToEdge(rootLayout)

        tvMonthYear.setOnClickListener {
            showYearMonthPicker()
        }

        btnSetting.setOnClickListener {
            val intent = Intent(this@CalendarActivity, SettingActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        btnSearch.setOnClickListener {
            val intent = Intent(this@CalendarActivity, SearchActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        btnTutorial.setOnClickListener {
            val intent = Intent(this@CalendarActivity, TutorialActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        btnStatistics.setOnClickListener {
            val intent = Intent(this@CalendarActivity, StatisticsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }


        val pref: SharedPreferences = getSharedPreferences("checkFirst", Activity.MODE_PRIVATE)
        val checkFirst = pref.getBoolean("checkFirst", false)

        if (!checkFirst) {
            pref.edit().putBoolean("checkFirst", true).apply()
            val intent = Intent(this, TutorialActivity::class.java)
            startActivity(intent)
        }
    }

    private fun ensureDefaultOwned() {

        val themeKey = ThemePrefs.getTheme(this)
        val foxKey = ThemePrefs.getFox(this)

        AllThemeMap.allThemeMap[themeKey]?.isOwned = true
        AllFoxMap.allFoxMap[foxKey]?.isOwned = true
    }

    private fun getCurrentThemeOrNull(): Theme? {
        ThemeRepository.updateOwned()

        val themeKey = ThemePrefs.getTheme(this)

        return ThemeRepository.ownedThemes[themeKey]
            ?: ThemeRepository.allThemeMap[themeKey]
    }

    fun updateOwned(){
        ThemeRepository.updateOwned()
        FoxRepository.updateOwned()
    }

    /**
     * CalendarActivity 전체 화면에 테마 적용
     */
    fun applyThemeModeSettings() {
        val currentTheme = getCurrentThemeOrNull() ?: return

        rootLayout.setBackgroundResource(currentTheme.backgroundColor)

        btnPrevMonth.setImageResource(R.drawable.ic_arrow_back_white)
        btnNextMonth.setImageResource(R.drawable.ic_arrow_forward_white)
        btnSetting.setImageResource(R.drawable.ic_setting_menu_gray)
        btnTutorial.setImageResource(R.drawable.calendar_shape_fox_completed)
    }

    /**
     * DayAdapter에서 각 날짜 셀 배경에 테마 적용할 때 사용
     * activity.applyThemeModeSettings(itemView) 형태로 호출
     */
    fun applyThemeModeSettings(targetView: View) {
        val currentTheme = getCurrentThemeOrNull() ?: return
        targetView.setBackgroundResource(currentTheme.backgroundColor)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setCustomCalendar() {
        monthAdapter = MonthAdapter(activity = this)
        calendarViewPager.adapter = monthAdapter

        val recyclerView =
            calendarViewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
        recyclerView?.itemAnimator = null

//        val headerLayout = findViewById<LinearLayout>(R.id.day_of_week_header)
//        headerLayout.removeAllViews()

        val dayNamesKOR = listOf("일", "월", "화", "수", "목", "금", "토")
        val dayNamesENG = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
        val dayNames =
            if (currentLanguage == CalendarLanguage.KOREAN) dayNamesKOR else dayNamesENG

        val currentTheme = getCurrentThemeOrNull()

        for (dayName in dayNames) {
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
                )
                gravity = Gravity.CENTER
                text = dayName

                setTextColor(
                    when (dayName) {
                        "일", "SUN" -> ContextCompat.getColor(
                            this@CalendarActivity,
                            android.R.color.holo_red_dark
                        )

                        "토", "SAT" -> ContextCompat.getColor(
                            this@CalendarActivity,
                            android.R.color.holo_blue_dark
                        )

                        else -> currentTheme?.themeTextColor_special
                            ?: ContextCompat.getColor(
                                this@CalendarActivity,
                                android.R.color.black
                            )
                    }
                )
            }

            //headerLayout.addView(tv)
        }

        calendarViewPager.setCurrentItem(CENTER_POSITION, false)

        calendarViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateMonthYearTitle(position)
                observeMonthlyData(position)
            }
        })

        btnPrevMonth.setOnClickListener {
            val currentItem = calendarViewPager.currentItem
            calendarViewPager.setCurrentItem(currentItem - 1, true)
        }

        btnNextMonth.setOnClickListener {
            val currentItem = calendarViewPager.currentItem
            calendarViewPager.setCurrentItem(currentItem + 1, true)
        }

        updateMonthYearTitle(CENTER_POSITION)
        observeMonthlyData(CENTER_POSITION)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun observeMonthlyData(position: Int) {
        val targetMonth = getTargetMonthForPosition(position)
        val fromDate = targetMonth.atDay(1).toString()
        val toDate = targetMonth.atEndOfMonth().toString()

        currentMonthLiveData?.removeObservers(this)
        currentMonthLiveData = viewModel.getMonthlyEmojis(fromDate, toDate)

        currentMonthLiveData?.observe(this) { map ->
            currentStatusMap = map
            monthAdapter.notifyItemChanged(calendarViewPager.currentItem)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateMonthYearTitle(position: Int) {
        val targetMonth = getTargetMonthForPosition(position)

        val (pattern, locale) = when (currentLanguage) {
            CalendarLanguage.KOREAN ->
                Pair(R.string.month_year_format, Locale.KOREAN)

            CalendarLanguage.ENGLISH ->
                Pair(R.string.month_year_format_english, Locale.US)
        }

        val formatter = DateTimeFormatter.ofPattern(getString(pattern), locale)
        tvMonthYear.text = targetMonth.format(formatter)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getTargetMonthForPosition(position: Int): YearMonth {
        val baseYearMonth = YearMonth.now()
        val monthDiff = position - CENTER_POSITION
        return baseYearMonth.plusMonths(monthDiff.toLong())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showYearMonthPicker() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_year_month_picker, null)
        dialog.setContentView(view)

        val npYear = view.findViewById<NumberPicker>(R.id.np_year)
        val npMonth = view.findViewById<NumberPicker>(R.id.np_month)
        val btnConfirm = view.findViewById<Button>(R.id.btn_confirm)

        val currentPosition = calendarViewPager.currentItem
        val currentTarget = getTargetMonthForPosition(currentPosition)

        npYear.minValue = 2000
        npYear.maxValue = 2099
        npYear.value = currentTarget.year
        npYear.wrapSelectorWheel = false

        npMonth.minValue = 1
        npMonth.maxValue = 12
        npMonth.value = currentTarget.monthValue
        npMonth.wrapSelectorWheel = true

        btnConfirm.setOnClickListener {
            val selectedYear = npYear.value
            val selectedMonth = npMonth.value

            val targetYearMonth = YearMonth.of(selectedYear, selectedMonth)
            val baseYearMonth = YearMonth.now()

            val monthDiff = ChronoUnit.MONTHS.between(baseYearMonth, targetYearMonth)
            val newPosition = CENTER_POSITION + monthDiff.toInt()

            calendarViewPager.setCurrentItem(newPosition, false)
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        updateOwned()
        applyThemeModeSettings()
        monthAdapter.notifyDataSetChanged()
    }
}