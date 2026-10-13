package com.example.sumdays.stickers

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sumdays.R
import com.example.sumdays.ShopActivity
import com.example.sumdays.auth.AccountStorage
import com.example.sumdays.auth.InventoryOwner
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.DailyEntry
import com.example.sumdays.utils.setupEdgeToEdge
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class DiaryStickerDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val rows = ConcurrentHashMap<String, DiarySticker>()
    private val diary = DailyEntry("2026-10-11",
        "가을바람이 기분 좋게 불던 하루.\n\n오랜만에 친구와 동네를 걸었다. 작은 카페에서 따뜻한 차를 마시며 한참 이야기를 나눴다.\n\n평범한 순간이 오래 기억에 남을 것 같다.",
        null, null, null, null, null, isAllowed = true)
    private lateinit var controller: DiaryStickerController

    @Before fun setup() {
        assumeTrue(context.packageName.endsWith(".inventorycheck"))
        for (actor in listOf(101, 202)) {
            assertTrue(SessionManager.saveSession(actor, "isolated-sticker-test"))
            AccountStorage.preferences(context, "sticker_inventory").edit().clear().commit()
            StickerInventoryStore(context).add(1001)
            StickerInventoryStore(context).add(1001)
        }
        assertTrue(SessionManager.saveSession(101, "isolated-sticker-test"))
    }

    // The production reader layouts and controller run in an existing isolated activity host.
    // No real API, production database or user's Room data is used by these UI tests.
    private fun reader(owner: Int, remote: FakeRemote, entry: DailyEntry = diary,
                       denied: () -> Unit = {}): ActivityScenario<ShopActivity> {
        return ActivityScenario.launch(ShopActivity::class.java).also { scenario ->
            scenario.onActivity { activity ->
                activity.setContentView(if (owner == SessionManager.getUserId())
                    R.layout.activity_daily_read else R.layout.activity_social_daily_read)
                activity.setupEdgeToEdge(activity.findViewById(R.id.main))
                activity.findViewById<TextView>(R.id.date_text).text = entry.date
                activity.findViewById<TextView>(R.id.diary_content_text_view).text = entry.diary
                controller = DiaryStickerController(activity, InventoryOwner(),
                    activity.findViewById(R.id.diary_sticker_canvas), activity.findViewById(R.id.sticker_button),
                    activity.findViewById(R.id.sticker_visibility_button),
                    remote, denied)
                controller.bind(owner, entry)
            }
        }
    }

    private fun awaitReady(scenario: ActivityScenario<ShopActivity>, count: Int) {
        await { var ready = false
            scenario.onActivity { activity ->
                val button = activity.findViewById<Button>(R.id.sticker_button)
                val readyText = if (activity.findViewById<View>(R.id.read_back_button) != null) "장난치기!" else "스티커 꾸미기"
                ready = button.isEnabled && button.text == readyText &&
                    activity.findViewById<StickerCanvasView>(R.id.diary_sticker_canvas).stickers.size == count
            }
            ready
        }
    }

    private fun pick() {
        onView(withId(R.id.sticker_button)).perform(click())
        onView(withContentDescription("마음 한 조각, 보유 2개")).perform(click())
    }

    @Test fun placeMoveReopenAndDecorateFriendsDiary() {
        var geometry: StickerGeometry? = null
        reader(101, FakeRemote(101)).use { scenario ->
            awaitReady(scenario, 0)
            onView(withId(R.id.sticker_button)).perform(click())
            capture("diary-sticker-picker")
            onView(withContentDescription("마음 한 조각, 보유 2개")).perform(click())
            onView(withId(R.id.stickerCancel)).perform(click())
            assertEquals(2, StickerInventoryStore(context).count(1001))
            pick()
            onView(withId(R.id.stickerEditorCanvas)).perform(GeneralSwipeAction(Swipe.SLOW,
                { view -> location(view, .5f, .5f) }, { view -> location(view, .8f, .68f) }, Press.FINGER))
            onView(withId(R.id.stickerSize)).perform(GeneralClickAction(Tap.SINGLE,
                { view -> location(view, .7f, .5f) }, Press.FINGER, 0, 0))
            onView(withId(R.id.stickerRotation)).perform(GeneralClickAction(Tap.SINGLE,
                { view -> location(view, .57f, .5f) }, Press.FINGER, 0, 0))
            capture("diary-sticker-editor")
            onView(withId(R.id.stickerConfirm)).perform(click())
            awaitReady(scenario, 1)
            assertEquals(1, StickerInventoryStore(context).count(1001))
            geometry = rows.values.single().geometry()
            assertTrue(geometry!!.x > .6f)
            assertTrue(geometry!!.size > .2f)
            assertTrue(geometry!!.rotation > 0f)
            capture("diary-sticker-own")
        }
        reader(101, FakeRemote(101)).use { scenario ->
            awaitReady(scenario, 1)
            scenario.onActivity { activity ->
                assertEquals(geometry, activity.findViewById<StickerCanvasView>(R.id.diary_sticker_canvas).stickers.single().geometry())
            }
            onView(withId(R.id.sticker_button)).perform(click())
            onView(withText("붙인 스티커 관리 (1)")).perform(click())
            onView(withText("마음 한 조각 · 친구101")).perform(click())
            onView(withText("위치·크기·회전 바꾸기")).perform(click())
            onView(withId(R.id.stickerRotation)).perform(GeneralClickAction(Tap.SINGLE,
                { view -> location(view, .4f, .5f) }, Press.FINGER, 0, 0))
            onView(withId(R.id.stickerConfirm)).perform(click())
            awaitReady(scenario, 1)
            assertTrue(rows.values.single().rotation < 0f)
            assertEquals(1, StickerInventoryStore(context).count(1001))
        }
        assertTrue(SessionManager.saveSession(202, "isolated-sticker-test"))
        reader(101, FakeRemote(202)).use { scenario ->
            awaitReady(scenario, 1)
            pick()
            onView(withId(R.id.stickerConfirm)).perform(click())
            awaitReady(scenario, 2)
            assertEquals(1, StickerInventoryStore(context).count(1001))
            capture("diary-sticker-friend")
        }
        assertTrue(SessionManager.saveSession(101, "isolated-sticker-test"))
        reader(101, FakeRemote(101)).use { scenario ->
            awaitReady(scenario, 2)
            onView(withId(R.id.sticker_button)).perform(click())
            onView(withText("붙인 스티커 관리 (2)")).perform(click())
            onView(withText("마음 한 조각 · 친구202")).perform(click())
            onView(withText("삭제")).perform(click())
            onView(withText("삭제")).perform(click())
            awaitReady(scenario, 1)
            assertTrue(rows.values.all { it.authorId == 101 })
            assertEquals(1, StickerInventoryStore(context).count(1001))
        }
    }

    @Test fun changingDateWhileSavingLoadsNewPageAfterOriginalRequestSettles() {
        val remote = FakeRemote(101).apply { gate = CompletableDeferred() }
        reader(101, remote).use { scenario ->
            awaitReady(scenario, 0)
            pick()
            onView(withId(R.id.stickerConfirm)).perform(click())
            await { StickerInventoryStore(context).pending().isNotEmpty() }
            scenario.onActivity { controller.bind(101, diary.copy(date = "2026-10-12")) }
            remote.gate!!.complete(Unit)
            awaitReady(scenario, 0)
            assertEquals(diary.date, rows.values.single().date)
            assertEquals(1, StickerInventoryStore(context).count(1001))
            assertTrue(StickerInventoryStore(context).pending().isEmpty())
        }
    }

    @Test fun accessRevocationClearsStickersAndPrivateOrEmptyFriendDiaryHidesButton() {
        var denied = false
        val remote = FakeRemote(101)
        rows["existing"] = DiarySticker("existing", 202, 101, "친구101", diary.date, 1001, .5f, .5f, .18f, 0f)
        reader(202, remote, denied = { denied = true }).use { scenario ->
            awaitReady(scenario, 1)
            remote.forbidden = true
            scenario.onActivity { controller.refresh() }
            await { denied }
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<StickerCanvasView>(R.id.diary_sticker_canvas).stickers.isEmpty())
                controller.bind(202, diary.copy(isAllowed = false))
                assertEquals(View.GONE, activity.findViewById<View>(R.id.sticker_button).visibility)
                controller.bind(202, diary.copy(diary = ""))
                assertEquals(View.GONE, activity.findViewById<View>(R.id.sticker_button).visibility)
            }
            assertEquals(2, StickerInventoryStore(context).count(1001))
        }
    }

    private inner class FakeRemote(private val actor: Int) : StickerRemote {
        var gate: CompletableDeferred<Unit>? = null
        @Volatile var forbidden = false
        override suspend fun list(owner: Int, date: String): List<DiarySticker> {
            if (forbidden) throw StickerRequestException(403, "STICKER_FORBIDDEN", "공개 일기가 아니에요.")
            return rows.values.filter { it.ownerId == owner && it.date == date }.sortedBy { it.authorId }
        }
        override suspend fun place(request: PendingSticker): DiarySticker {
            gate?.await()
            return rows.getOrPut(request.id) { DiarySticker(request.id, request.ownerId, actor, "친구$actor",
                request.date, request.stickerId, request.geometry.x, request.geometry.y, request.geometry.size, request.geometry.rotation) }
        }
        override suspend fun update(sticker: DiarySticker, geometry: StickerGeometry) = sticker.withGeometry(geometry).also { rows[it.id] = it }
        override suspend fun remove(sticker: DiarySticker) { rows.remove(sticker.id) }
        override suspend fun syncOwnDiary(entry: DailyEntry) {}
    }

    private fun location(view: View, x: Float, y: Float): FloatArray {
        val location = IntArray(2); view.getLocationOnScreen(location)
        return floatArrayOf(location[0] + view.width * x, location[1] + view.height * y)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue("UI did not reach expected state", condition())
    }

    private fun capture(name: String) {
        SystemClock.sleep(2300)
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()!!
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: context.cacheDir
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
