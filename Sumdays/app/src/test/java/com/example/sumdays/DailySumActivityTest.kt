package com.example.sumdays

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.example.sumdays.daily.memo.Memo
import com.example.sumdays.daily.memo.MemoMergeAdapter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivity

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [Build.VERSION_CODES.O], // DailySumActivity가 O 이상 요구
    application = TestApplication::class
)
class DailySumActivityTest {

    // 네가 만든 Memo 엔티티랑 맞춘 helper
    private fun memo(
        id: Int,
        content: String,
        timestamp: String,
        date: String = "2025-02-05",
        order: Int,
        type: String = "text"
    ) = Memo(
        id = id,
        content = content,
        timestamp = timestamp,
        date = date,
        order = order,
        type = type
    )

    /** 공통: Intent에 memo_list + date 넣고 Activity 생성 */
    private fun createActivityWithMemos(): DailySumActivity {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, DailySumActivity::class.java).apply {
            putExtra("date", "2025-02-05")
            putParcelableArrayListExtra(
                "memo_list",
                arrayListOf(
                    memo(1, "First memo", "10:00", order = 0),
                    memo(2, "Second memo", "10:01", order = 1)
                )
            )
        }

        val controller = Robolectric.buildActivity(DailySumActivity::class.java, intent)
        return controller.setup().get()
    }

    // ---------------------------------------------------------------------
    // 1. onCreate: RecyclerView + Adapter 셋업 확인
    // ---------------------------------------------------------------------
    @Test
    fun onCreate_bindsRecyclerViewWithMemoMergeAdapter() {
        val activity = createActivityWithMemos()

        val dateView = activity.findViewById<TextView>(R.id.date_text_view)
        assertEquals("2025-02-05", dateView.text.toString())

        val recycler = activity.findViewById<RecyclerView>(R.id.memo_list_view)
        assertNotNull(recycler)
        assertNotNull(recycler.adapter)
        assertTrue(recycler.adapter is MemoMergeAdapter)
        assertEquals(2, recycler.adapter!!.itemCount)
    }

    // ---------------------------------------------------------------------
    // 2. undo_icon: undoLastMerge() 호출되는지
    // ---------------------------------------------------------------------
    @Test
    fun clickingUndoIcon_callsUndoLastMergeOnAdapter() {
        val activity = createActivityWithMemos()

        // Activity 내부 memoMergeAdapter를 spy로 교체
        val field = DailySumActivity::class.java.getDeclaredField("memoMergeAdapter")
        field.isAccessible = true
        val originalAdapter = field.get(activity) as MemoMergeAdapter
        val spyAdapter = spy(originalAdapter)

        field.set(activity, spyAdapter)
        val recycler = activity.findViewById<RecyclerView>(R.id.memo_list_view)
        recycler.adapter = spyAdapter

        val undoIcon = activity.findViewById<ImageView>(R.id.undo_icon)
        undoIcon.performClick()

        verify(spyAdapter).undoLastMerge()
    }

    // ---------------------------------------------------------------------
    // 3. showLoading(true/false) 가 overlay visibility를 토글하는지
    // ---------------------------------------------------------------------
    @Test
    fun showLoading_togglesOverlayVisibility() {
        val activity = createActivityWithMemos()

        val overlay = activity.findViewById<View>(R.id.loading_overlay)
        val gifView = activity.findViewById<View>(R.id.loading_gif_view)

        // 초기: GONE 이라고 가정 (레이아웃에 맞춰 필요시 변경)
        assertEquals(View.GONE, overlay.visibility)
        assertEquals(View.GONE, gifView.visibility)

        val method = DailySumActivity::class.java.getDeclaredMethod(
            "showLoading",
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true

        // 로딩 시작
        method.invoke(activity, true)
        assertEquals(View.VISIBLE, overlay.visibility)
        assertEquals(View.VISIBLE, gifView.visibility)

        // 로딩 종료
        method.invoke(activity, false)
        assertEquals(View.GONE, overlay.visibility)
        assertEquals(View.GONE, gifView.visibility)
    }

    // ---------------------------------------------------------------------
    // 4. showAllMergedSheet(): mergeSheetShowing 플래그 확인
    // ---------------------------------------------------------------------
    @Test
    fun showAllMergedSheet_setsFlagAndPreventsDoubleShow() {
        val activity = createActivityWithMemos()

        val flagField = DailySumActivity::class.java.getDeclaredField("mergeSheetShowing")
        flagField.isAccessible = true
        assertEquals(false, flagField.getBoolean(activity))

        val method = DailySumActivity::class.java.getDeclaredMethod("showAllMergedSheet")
        method.isAccessible = true

        // 첫 호출
        method.invoke(activity)
        assertEquals(true, flagField.getBoolean(activity))

        // 두 번째 호출: early return → 여전히 true, 크래시 없이 통과
        method.invoke(activity)
        assertEquals(true, flagField.getBoolean(activity))
    }

    // ---------------------------------------------------------------------
    // 5. skip_icon 클릭: showLoading(true) 호출되어 overlay 가 VISIBLE 되는지
    // ---------------------------------------------------------------------
    @Test
    fun clickingSkipIcon_showsLoadingOverlay() {
        val activity = createActivityWithMemos()

        val overlay = activity.findViewById<View>(R.id.loading_overlay)
        val gifView = activity.findViewById<View>(R.id.loading_gif_view)

        // 초기에는 안 보인다고 가정
        assertEquals(View.GONE, overlay.visibility)
        assertEquals(View.GONE, gifView.visibility)

        val skipIcon = activity.findViewById<ImageButton>(R.id.go_icon)
        skipIcon.performClick()   // 내부에서 showLoading(true) 호출

        // 코루틴이 어떻게 동작하든, showLoading(true)는 즉시 호출되므로
        // overlay는 VISIBLE이어야 함
        assertEquals(View.VISIBLE, overlay.visibility)
        assertEquals(View.VISIBLE, gifView.visibility)
    }

    // ---------------------------------------------------------------------
    // 6. moveToReadActivity(): DailyReadActivity로 넘어가고 date extra 전달되는지
    // ---------------------------------------------------------------------
    @Test
    fun moveToReadActivity_startsDailyReadActivityWithDateExtra() {
        val activity = createActivityWithMemos()

        val method = DailySumActivity::class.java.getDeclaredMethod("moveToReadActivity")
        method.isAccessible = true

        // startActivity() 호출
        method.invoke(activity)

        val shadow = Shadow.extract<ShadowActivity>(activity)
        val startedIntent = shadow.nextStartedActivity
        assertNotNull("DailyReadActivity 로의 Intent 가 존재해야 함", startedIntent)

        // 컴포넌트 클래스 이름이 DailyReadActivity 인지 확인
        assertEquals(
            DailyReadActivity::class.java.name,
            startedIntent.component?.className
        )

        // date extra 확인
        assertEquals("2025-02-05", startedIntent.getStringExtra("date"))
    }
}
