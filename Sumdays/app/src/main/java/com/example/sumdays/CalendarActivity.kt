package com.example.sumdays

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.NumberPicker // [변경] 기본 위젯 import
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.viewpager2.widget.ViewPager2
import com.example.sumdays.calendar.CalendarLanguage
import com.example.sumdays.calendar.MonthAdapter
import com.example.sumdays.data.viewModel.CalendarViewModel
import com.example.sumdays.settings.ThemeSettingsActivity
import com.example.sumdays.settings.prefs.ThemeState
import com.example.sumdays.utils.setupEdgeToEdge
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.jakewharton.threetenabp.AndroidThreeTen
// import com.shawnlin.numberpicker.NumberPicker [삭제]
import org.threeten.bp.LocalDate
import org.threeten.bp.YearMonth
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.ChronoUnit
import java.util.Locale
import com.example.sumdays.ui.component.NavBarController
import com.example.sumdays.ui.component.NavSource

class CalendarActivity : AppCompatActivity() {

    private lateinit var calendarViewPager: ViewPager2
    private lateinit var tvMonthYear: TextView
    private lateinit var monthAdapter: MonthAdapter
    private lateinit var btnPrevMonth: ImageButton
    private lateinit var btnNextMonth: ImageButton
    private lateinit var btnSetting: ImageButton
    private lateinit var navBarController: NavBarController

    private val viewModel: CalendarViewModel by viewModels()
    var currentStatusMap: Map<String, Pair<Boolean, String?>> = emptyMap()
    private var currentMonthLiveData: LiveData<Map<String, Pair<Boolean, String?>>>? = null
    private var currentLanguage: CalendarLanguage = CalendarLanguage.KOREAN
    private val today: LocalDate by lazy { LocalDate.now() }
    private val CENTER_POSITION = Int.MAX_VALUE / 2

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)
        AndroidThreeTen.init(this)

        calendarViewPager = findViewById(R.id.calendarViewPager)
        tvMonthYear = findViewById(R.id.tv_month_year)
        btnPrevMonth = findViewById(R.id.btn_prev_month)
        btnNextMonth = findViewById(R.id.btn_next_month)
        btnSetting = findViewById(R.id.setting_menu)

        applyThemeModeSettings()

        setCustomCalendar()
        navBarController = NavBarController(this)
        navBarController.setNavigationBar(NavSource.CALENDAR)

        tvMonthYear.setOnClickListener {
            showYearMonthPicker()
        }

        btnSetting.setOnClickListener {
            val intent = Intent(this@CalendarActivity, SettingActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
        
        val rootView = findViewById<View>(R.id.root_layout)
        setupEdgeToEdge(rootView)

        val pref: SharedPreferences = getSharedPreferences("checkFirst", Activity.MODE_PRIVATE)
        val checkFirst = pref.getBoolean("checkFirst", false)

        if (!checkFirst) {
            val editor = pref.edit()
            editor.putBoolean("checkFirst", true)
            editor.apply()
            val intent = Intent(this, TutorialActivity::class.java)
            startActivity(intent)
        }
    }

    private fun applyThemeModeSettings(){
        // Apply dark mode
        ThemeState.isDarkMode = (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)

        if (ThemeState.isDarkMode){
            btnPrevMonth.setImageResource(R.drawable.ic_arrow_back_white)
            btnNextMonth.setImageResource(R.drawable.ic_arrow_forward_white)
            btnSetting.setImageResource(R.drawable.ic_setting_menu_gray)
        }
        else{
            btnPrevMonth.setImageResource(R.drawable.ic_arrow_back_black)
            btnNextMonth.setImageResource(R.drawable.ic_arrow_forward_black)
            btnSetting.setImageResource(R.drawable.ic_setting_menu_black)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setCustomCalendar() {
        monthAdapter = MonthAdapter(activity = this)
        calendarViewPager.adapter = monthAdapter

        val headerLayout = findViewById<LinearLayout>(R.id.day_of_week_header)
        headerLayout.removeAllViews()

        val dayNamesKOR = listOf("일", "월", "화", "수", "목", "금", "토")
        val dayNamesENG = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
        val dayNames = if (currentLanguage == CalendarLanguage.KOREAN) dayNamesKOR else dayNamesENG

        for (dayName in dayNames) {
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                gravity = Gravity.CENTER
                text = dayName
                setTextColor(
                    when (dayName) {
                        "일", "SUN" -> ContextCompat.getColor(this@CalendarActivity, android.R.color.holo_red_dark)
                        "토", "SAT" -> ContextCompat.getColor(this@CalendarActivity, android.R.color.holo_blue_dark)
                        else -> if (ThemeState.isDarkMode) Color.WHITE else Color.BLACK
                    }
                )
            }
            headerLayout.addView(tv)
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
            val nextPos = currentItem + 1
            calendarViewPager.setCurrentItem(nextPos, true)
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
            CalendarLanguage.KOREAN -> Pair(R.string.month_year_format, Locale.KOREAN)
            CalendarLanguage.ENGLISH -> Pair(R.string.month_year_format_english, Locale.US)
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

        // 연도 설정 (2000 ~ 2099)
        npYear.minValue = 2000
        npYear.maxValue = 2099
        npYear.value = currentTarget.year
        npYear.wrapSelectorWheel = false

        // 월 설정 (1 ~ 12)
        npMonth.minValue = 1
        npMonth.maxValue = 12
        npMonth.value = currentTarget.monthValue
        npMonth.wrapSelectorWheel = true // 월은 12에서 1로 돌아가게

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
}