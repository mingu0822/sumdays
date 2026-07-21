package com.example.sumdays.social.diary

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
import com.example.sumdays.R
import com.example.sumdays.calendar.CalendarLanguage
import com.example.sumdays.data.viewModel.CalendarViewModel
import com.example.sumdays.shop.AllFoxMap
import com.example.sumdays.shop.AllThemeMap
import com.example.sumdays.theme.FoxRepository
import com.example.sumdays.theme.Theme
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.utils.setupEdgeToEdge
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.jakewharton.threetenabp.AndroidThreeTen
import org.threeten.bp.YearMonth
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.ChronoUnit
import java.util.Locale

class SocialCalendarActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var calendarViewPager: ViewPager2
    private lateinit var tvMonthYear: TextView
    private lateinit var socialDiaryMonthAdapter: SocialMonthAdapter
    private lateinit var btnPrevMonth: ImageButton
    private lateinit var btnNextMonth: ImageButton
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var tvUserName: TextView


    var socialCalendarMasterMap: Map<String, Map<String, Boolean>> = emptyMap()
    var currentMonthStatusMap: Map<String, Boolean> = emptyMap()
    var friendId : Int = -1

    private var currentLanguage: CalendarLanguage = CalendarLanguage.KOREAN
    private val CENTER_POSITION = Int.MAX_VALUE / 2

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureDefaultOwned()
        updateOwned()
        setContentView(R.layout.activity_social_calendar)
        AndroidThreeTen.init(this)

        btnBack = findViewById(R.id.btn_back)
        calendarViewPager = findViewById(R.id.calendarViewPager)
        tvMonthYear = findViewById(R.id.tv_month_year)
        btnPrevMonth = findViewById(R.id.btn_prev_month)
        btnNextMonth = findViewById(R.id.btn_next_month)
        rootLayout = findViewById(R.id.root_layout)
        tvUserName = findViewById(R.id.user_name)

        val nickname = intent.getStringExtra("nickname") ?: "?"
        tvUserName.text = "${nickname}의 일기장"
        friendId = intent.getIntExtra("friendId", -1)

        socialCalendarMasterMap = getFriendDayList()
        setCustomCalendar()
        applyThemeModeSettings()
        setupEdgeToEdge(rootLayout)

        btnBack.setOnClickListener {
            finish()
        }
        tvMonthYear.setOnClickListener {
            showYearMonthPicker()
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

        btnPrevMonth.setImageResource(currentTheme.backIcon)
        btnNextMonth.setImageResource(currentTheme.forwardIcon)
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
        socialDiaryMonthAdapter = SocialMonthAdapter(activity = this)
        calendarViewPager.adapter = socialDiaryMonthAdapter
        val recyclerView =
            calendarViewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
        recyclerView?.itemAnimator = null

        val headerLayout = findViewById<LinearLayout>(R.id.day_of_week_header)
        headerLayout.removeAllViews()

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
                            this@SocialCalendarActivity,
                            android.R.color.holo_red_dark
                        )

                        "토", "SAT" -> ContextCompat.getColor(
                            this@SocialCalendarActivity,
                            android.R.color.holo_blue_dark
                        )

                        else -> currentTheme?.themeTextColorSpecialA
                            ?: ContextCompat.getColor(
                                this@SocialCalendarActivity,
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
        val yearMonthKey = targetMonth.toString()

        currentMonthStatusMap = socialCalendarMasterMap[yearMonthKey] ?: emptyMap()
        socialDiaryMonthAdapter.notifyItemChanged(position)
    }

    private fun getFriendDayList(): Map<String, Map<String, Boolean>> {
        val masterMap = HashMap<String, HashMap<String, Boolean>>()

        // 📅 5월 데이터 상자
        val mayMap = HashMap<String, Pair<Boolean, Boolean>>().apply {
            put("2026-05-12", Pair(true, true))
            put("2026-05-20", Pair(true, false)) // 일기 있음, 열람 권한 없음 (🔒 잠김)
        }
        masterMap["2026-05"] = mayMap

        // 📅 6월 데이터 상자 (현재 타깃 달 테스트용)
        val juneMap = HashMap<String, Pair<Boolean, Boolean>>().apply {
            put("2026-06-01", Pair(true, true))   // 일기 있음, 열람 가능 (정상 진입)
            put("2026-06-10", Pair(true, false))  // 일기 있음, 열람 권한 없음 (🔒 잠김)
            put("2026-06-15", Pair(false, false)) // 일기 자체가 안 써진 날
            put("2026-06-20", Pair(true, true))   // 일기 있음, 열람 가능 (정상 진입)
            put("2026-06-24", Pair(true, false))  // 일기 있음, 열람 권한 없음 (🔒 잠김)
            put("2026-06-25", Pair(true, true))   // 일기 있음, 열람 가능 (정상 진입)
        }
        masterMap["2026-06"] = juneMap

        // 📅 7월 데이터 상자
        val julyMap = HashMap<String, Pair<Boolean, Boolean>>().apply {
            put("2026-07-05", Pair(true, true))
            put("2026-07-07", Pair(true, false)) // 일기 있음, 열람 권한 없음 (🔒 잠김)
        }
        masterMap["2026-07"] = julyMap

        return masterMap
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
}
