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
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.sumdays.R
import com.example.sumdays.calendar.CalendarLanguage
import com.example.sumdays.data.DailyEntry
import com.example.sumdays.data.viewModel.CalendarViewModel
import com.example.sumdays.network.ApiClient
import com.example.sumdays.shop.AllFoxMap
import com.example.sumdays.shop.AllThemeMap
import com.example.sumdays.theme.FoxRepository
import com.example.sumdays.theme.Theme
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.utils.setupEdgeToEdge
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.jakewharton.threetenabp.AndroidThreeTen
import kotlinx.coroutines.launch
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
    var friendDiaryList: MutableMap<String, Map<String, DailyEntry>> = mutableMapOf()
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

        getFriendDayList()
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getFriendDayList() {
        if (friendId == -1) return

        lifecycleScope.launch {
            try {
                val response = ApiClient.socialApi.getFriendDiaryDates(friendId)

                if (response.isSuccessful && response.body()?.success == true) {
                    // 서버에서 온 원본 맵: {"2026-06-01": true, "2026-06-02": false, ...}
                    val dateMap = response.body()?.data?.dateMap ?: emptyMap()

                    // 연월("YYYY-MM") 단위로 묶어서 MasterMap 생성
                    val masterMap = HashMap<String, HashMap<String, Boolean>>()

                    for ((fullDate, hasDiary) in dateMap) {
                        // "2026-06-01" -> "2026-06" 추출
                        if (fullDate.length >= 7) {
                            val yearMonthKey = fullDate.substring(0, 7)

                            val monthMap = masterMap.getOrPut(yearMonthKey) { HashMap() }
                            monthMap[fullDate] = hasDiary
                        }
                    }

                    // 마스터 맵 할당 및 현재 페이지 UI 갱신
                    socialCalendarMasterMap = masterMap
                    observeMonthlyData(calendarViewPager.currentItem)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getMonthlyDiariesFromServer(yearMonth: String) {
        if (friendId == -1) return

        lifecycleScope.launch {
            try {
                val response = ApiClient.socialApi.getFriendMonthlyDiaries(friendId, yearMonth)

                if (response.isSuccessful && response.body()?.success == true) {
                    val diaryList = response.body()?.data?.diaries ?: emptyList()

                    // 1. 서버 응답(Payload) -> 앱 내부 DailyEntry 객체 리스트로 매핑
                    val dailyEntryList = diaryList.map { p ->
                        DailyEntry(
                            date = p.date,
                            diary = p.diary,
                            keywords = p.keywords,
                            aiComment = p.aiComment,
                            emotionScore = p.emotionScore,
                            emotionIcon = p.emotionIcon,
                            themeIcon = p.themeIcon,
                            photoUrls = p.photoUrls,
                            isAllowed = p.is_allowed,
                            isEdited = false,
                            isDeleted = false
                        )
                    }

                    // 2. 날짜("YYYY-MM-DD")를 키로 하는 단일 월 맵 생성: Map<String, DailyEntry>
                    val monthlyMap = dailyEntryList.associateBy { it.date }

                    // 3. 기존 마스터 맵(friendDiaryList)에 "YYYY-MM" 키로 병합해서 저장
                    friendDiaryList[yearMonth] = monthlyMap

                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
}
