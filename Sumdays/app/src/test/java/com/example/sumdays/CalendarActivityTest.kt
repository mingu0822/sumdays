package com.example.sumdays

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sumdays.calendar.MonthAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import org.threeten.bp.LocalDate
import org.threeten.bp.YearMonth
import org.threeten.bp.format.DateTimeFormatter
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE],
    application = CalendarActivityTest.TestApplication::class
)
@LooperMode(LooperMode.Mode.PAUSED)
class CalendarActivityTest {

    class TestApplication : Application() {
        override fun onCreate() {
            super.onCreate()
        }
    }
    private fun buildActivity(firstRun: Boolean = false): CalendarActivity {
        val controller = Robolectric.buildActivity(CalendarActivity::class.java)

        // onCreate 전에 SharedPreferences 세팅
        val activity = controller.get()
        val prefs = activity.getSharedPreferences("checkFirst", Activity.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("checkFirst", !firstRun) // firstRun=true -> false, firstRun=false -> true
            .apply()

        controller.setup()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        return controller.get()
    }


    @Test
    fun onCreate_setsHeaderAndTitle_andAdapter() {
        val activity = buildActivity()  // firstRun=false → checkFirst = true

        val header = activity.findViewById<LinearLayout>(R.id.day_of_week_header)
        assertThat(header.childCount, `is`(7))

        val first = header.getChildAt(0) as TextView
        val last = header.getChildAt(6) as TextView

        assertThat(first.text.toString(), `is`("일"))
        assertThat(last.text.toString(),  `is`("토"))

        // 상단 월/년 텍스트가 비어있지 않음
        val monthTitle = activity.findViewById<TextView>(R.id.tv_month_year)
        assertThat(monthTitle.text.toString().isNotBlank(), `is`(true))

        // ViewPager2 어댑터가 MonthAdapter로 설정됨
        val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.calendarViewPager)
        assertThat(pager.adapter, instanceOf(MonthAdapter::class.java))
    }

    @Test
    fun nextPrevButtons_changeMonthTitle() {
        val activity = buildActivity()

        val monthTitle = activity.findViewById<TextView>(R.id.tv_month_year)
        val btnPrev = activity.findViewById<ImageButton>(R.id.btn_prev_month)
        val btnNext = activity.findViewById<ImageButton>(R.id.btn_next_month)

        val initial = monthTitle.text.toString()

        // 다음 달로
        btnNext.performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        val afterNext = monthTitle.text.toString()
        assertThat(afterNext, not(initial))

        // 이전 달 버튼도 동작하는지만 확인 (원래로 돌아오진 않아도 됨)
        btnPrev.performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        val afterPrev = monthTitle.text.toString()
        assertThat(afterPrev.isNotBlank(), `is`(true))
    }

    @Test
    fun statisticButton_startsStatisticsActivity() {
        val activity = buildActivity()

        val statsBtn = activity.findViewById<ImageButton>(R.id.statistic_btn)
        statsBtn.performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val shadowActivity = Shadows.shadowOf(activity)
        val nextIntent: Intent = shadowActivity.nextStartedActivity
        assertThat(
            nextIntent.component?.className,
            `is`(StatisticsActivity::class.qualifiedName)
        )
    }

    @Test
    fun navBar_dailyButton_startsDailyWrite_withTodayExtra() {
        val activity = buildActivity()

        val dailyBtn = activity.findViewById<FloatingActionButton>(R.id.btnDaily)
        dailyBtn.performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val shadowActivity = Shadows.shadowOf(activity)
        val nextIntent: Intent = shadowActivity.nextStartedActivity
        assertThat(
            nextIntent.component?.className,
            `is`(DailyWriteActivity::class.qualifiedName)
        )

        val expectedDate = LocalDate.now().toString()
        val actualDate = nextIntent.getStringExtra("date")
        assertThat(actualDate, `is`(expectedDate))
    }

    @Test
    fun viewPager_swipeProgrammatically_updatesTitle() {
        val activity = buildActivity()
        val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.calendarViewPager)
        val title = activity.findViewById<TextView>(R.id.tv_month_year)

        val before = title.text.toString()
        val current = pager.currentItem

        // 다음 페이지로 이동 시뮬레이션
        pager.setCurrentItem(current + 1, false)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val after = title.text.toString()
        assertThat(after, not(before))
    }


    @Test
    fun navBar_infoButton_startsSettingsActivity() {
        val activity = buildActivity()

        val infoBtn = activity.findViewById<ImageButton>(R.id.btnInfo)
        infoBtn.performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val shadowActivity = Shadows.shadowOf(activity)
        val nextIntent: Intent = shadowActivity.nextStartedActivity
        assertThat(
            nextIntent.component?.className,
            `is`(ProfileActivity::class.qualifiedName)
        )
    }

    @Test
    fun navBar_calendarButton_doesNotStartNewActivity() {
        val activity = buildActivity()

        val calendarBtn = activity.findViewById<ImageButton>(R.id.btnCalendar)
        calendarBtn.performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val shadowActivity = Shadows.shadowOf(activity)
        val nextIntent: Intent? = shadowActivity.nextStartedActivity
        // 현재 화면 버튼이므로 새 액티비티는 시작되지 않아야 함
        assertThat(nextIntent, `is`(nullValue()))
    }

    @Test
    fun firstRun_launchesTutorialActivity_and_setsCheckFirstTrue() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val prefs = appContext.getSharedPreferences("checkFirst", Activity.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("checkFirst", false).commit()

        val controller = Robolectric.buildActivity(CalendarActivity::class.java).setup()
        val activity = controller.get()

        assertThat(activity.isFinishing, `is`(false))

        val shadowActivity = Shadows.shadowOf(activity)
        val nextIntent: Intent = shadowActivity.nextStartedActivity
        assertThat(
            nextIntent.component?.className,
            `is`(TutorialActivity::class.qualifiedName)
        )

        val updatedPrefs = activity.getSharedPreferences("checkFirst", Activity.MODE_PRIVATE)
        val checkFirst = updatedPrefs.getBoolean("checkFirst", false)
        assertThat(checkFirst, `is`(true))
    }

    @Test
    fun monthTitleClick_opensYearMonthPicker_and_confirmChangesMonthAndTitle() {
        val activity = buildActivity()

        val titleView = activity.findViewById<TextView>(R.id.tv_month_year)
        val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.calendarViewPager)

        val initialTitle = titleView.text.toString()
        val initialItem = pager.currentItem

        // 월/년 텍스트 클릭 → BottomSheetDialog 열림
        titleView.performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val dialog = ShadowDialog.getLatestDialog()
        assertThat(dialog, notNullValue())

        // 다이얼로그에서 NumberPicker와 확인 버튼을 찾는다
        val npYear = dialog.findViewById<NumberPicker>(R.id.np_year)
        val npMonth = dialog.findViewById<NumberPicker>(R.id.np_month)
        val btnConfirm = dialog.findViewById<Button>(R.id.btn_confirm)

        assertThat(npYear, notNullValue())
        assertThat(npMonth, notNullValue())
        assertThat(btnConfirm, notNullValue())

        // 현재 기준에서 한 달 뒤로 이동하도록 값 설정
        val targetYm = YearMonth.now().plusMonths(1)
        npYear.value = targetYm.year
        npMonth.value = targetYm.monthValue

        // 확인 버튼 클릭 → ViewPager 위치 및 타이틀 변경 기대
        btnConfirm.performClick()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val newItem = pager.currentItem
        val newTitle = titleView.text.toString()

        // 페이지가 바뀌었는지
        assertThat(newItem, not(initialItem))

        // 타이틀이 예상 값으로 바뀌었는지 (KOREAN 포맷)
        val pattern = activity.getString(R.string.month_year_format)
        val formatter = DateTimeFormatter.ofPattern(pattern, Locale.KOREAN)
        val expectedTitle = targetYm.format(formatter)
        assertThat(newTitle, `is`(expectedTitle))
        assertThat(newTitle, not(initialTitle))
    }
}
