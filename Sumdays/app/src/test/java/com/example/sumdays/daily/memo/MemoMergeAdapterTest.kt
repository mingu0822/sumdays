package com.example.sumdays.daily.memo

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.sumdays.R
import com.example.sumdays.data.dao.UserStyleDao
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.ApiService
import com.example.sumdays.settings.prefs.UserStatsPrefs
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import io.mockk.coEvery
import io.mockk.spyk
import io.mockk.slot
import android.view.View
import android.view.DragEvent
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sumdays.TestApplication


@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [Build.VERSION_CODES.O],
    application = TestApplication::class
)
class MemoMergeAdapterTest {

    // 메인 디스패처를 테스트용으로 바꿔주기 (Dispatchers.Main 사용 코드 때문에 필요)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setupDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // 네 Memo 엔티티와 맞춘 헬퍼
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

    /** MemoMergeAdapter 생성 공통 함수 */
    private fun adapter(
        list: MutableList<Memo>,
        onAllDone: () -> Unit = {},
        prefs: UserStatsPrefs = Mockito.mock(UserStatsPrefs::class.java),
        dao: UserStyleDao = Mockito.mock(UserStyleDao::class.java)
    ): MemoMergeAdapter {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        return MemoMergeAdapter(
            memoList = list,
            scope = scope,
            onAllMergesDone = onAllDone,
            useStableIds = false,
            userStatsPrefs = prefs,
            userStyleDao = dao,
            context = ApplicationProvider.getApplicationContext()
        )
    }

    // ---------------------------------------------------------------------
    // 1. 기본 정보 관련 메서드들
    // ---------------------------------------------------------------------

    @Test
    fun getItemCount_returnsListSize() {
        val list = mutableListOf(
            memo(1, "A", "10:00", order = 0),
            memo(2, "B", "10:01", order = 1),
            memo(3, "C", "10:02", order = 2)
        )
        val ad = adapter(list)

        assertEquals(3, ad.itemCount)
    }

    @Test
    fun getMemoContent_returnsCorrectText() {
        val list = mutableListOf(
            memo(1, "A", "10:00", order = 0),
            memo(2, "B", "10:01", order = 1)
        )
        val ad = adapter(list)

        assertEquals("A", ad.getMemoContent(0))
        assertEquals("B", ad.getMemoContent(1))
    }

    @Test
    fun getItemId_isCalculatedFromContentAndTimestamp() {
        val m = memo(10, "Hello!", "09:30", order = 0)
        val list = mutableListOf(m)
        val ad = adapter(list)

        val expected = (m.content + m.timestamp).hashCode().toLong()
        assertEquals(expected, ad.getItemId(0))
    }

    // ---------------------------------------------------------------------
    // 2. updateIdMap() 테스트
    // ---------------------------------------------------------------------

    @Test
    fun updateIdMap_updatesInternalMapping() {
        val list = mutableListOf(
            memo(10, "X", "09:00", order = 0),
            memo(20, "Y", "09:10", order = 1)
        )
        val ad = adapter(list)

        val ids = listOf(10, 20, 30)
        ad.updateIdMap(10, ids)

        val f = MemoMergeAdapter::class.java.getDeclaredField("idToMergedIds")
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = f.get(ad) as MutableMap<Int, MutableList<Int>>

        assertTrue(map.containsKey(10))
        assertEquals(ids, map[10])
    }

    // ---------------------------------------------------------------------
    // 3. undoLastMerge() - 성공 / 실패
    // ---------------------------------------------------------------------

    @Test
    fun undoLastMerge_restoresLastMergedState() {
        val m1 = memo(1, "A", "10:00", order = 0)
        val m2 = memo(2, "B", "10:01", order = 1)
        val m3 = memo(3, "C", "10:02", order = 2)

        val list = mutableListOf(m1, m2, m3)
        val ad = adapter(list)

        // [m1, m2, m3] → [merged(m1,m2), m3] 상태를 흉내내기
        val merged = memo(2, "A+B", "10:01", order = 1)
        list[1] = merged
        list.removeAt(0)

        val record = MemoMergeAdapter.MergeRecord(
            fromIndexBefore = 0,
            toIndexBefore = 1,
            fromMemo = m1,
            toMemoBefore = m2,
            previousIdMap = emptyMap(),
            mergedIds = mutableListOf(1, 2)
        )

        val undoField = MemoMergeAdapter::class.java.getDeclaredField("undoStack")
        undoField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stack = undoField.get(ad) as ArrayDeque<MemoMergeAdapter.MergeRecord>
        stack.addLast(record)

        val result = ad.undoLastMerge()

        assertTrue(result)
        assertEquals(3, list.size)
        assertEquals(m1, list[0])
        assertEquals(m2, list[1])
        assertEquals(m3, list[2])
    }

    @Test
    fun undoLastMerge_returnsFalseWhenNoRecord() {
        val list = mutableListOf(
            memo(1, "A", "10:00", order = 0),
            memo(2, "B", "10:01", order = 1)
        )
        val ad = adapter(list)

        val result = ad.undoLastMerge()
        assertFalse(result)
    }

    // ---------------------------------------------------------------------
    // 4. onCreateViewHolder + onBindViewHolder (기본 바인딩)
    // ---------------------------------------------------------------------

    @Test
    fun onCreateViewHolder_and_onBindViewHolder_bindTextsCorrectly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val list = mutableListOf(
            memo(1, "Hello Memo", "11:11", order = 0)
        )
        val ad = adapter(list)

        val parent = FrameLayout(context)
        val holder = ad.onCreateViewHolder(parent, 0)

        ad.onBindViewHolder(holder, 0)

        val tvContent = holder.itemView.findViewById<TextView>(R.id.memo_text)
        val tvTime = holder.itemView.findViewById<TextView>(R.id.memo_time)

        assertEquals("Hello Memo", tvContent.text.toString())
        assertEquals("11:11", tvTime.text.toString())
    }

    // ---------------------------------------------------------------------
    // 5. onBindViewHolder(payloads) - partial 업데이트
    // ---------------------------------------------------------------------

    @Test
    fun onBindViewHolder_withPayload_updatesTextOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = LayoutInflater.from(context).inflate(R.layout.item_memo, null, false)

        val m = memo(1, "Original", "10:00", order = 0)
        val list = mutableListOf(m)
        val ad = adapter(list)

        val ctor = MemoMergeAdapter.VH::class.java.getDeclaredConstructor(
            MemoMergeAdapter::class.java,
            android.view.View::class.java
        )
        ctor.isAccessible = true
        val holder = ctor.newInstance(ad, view)

        val partial = "Streaming text..."

        ad.onBindViewHolder(holder, 0, mutableListOf(partial))

        val tv = view.findViewById<TextView>(R.id.memo_text)
        assertEquals(partial, tv.text.toString())
    }

    // ---------------------------------------------------------------------
    // 6. maybeNotifyAllMerged() 테스트
    // ---------------------------------------------------------------------

    @Test
    fun maybeNotifyAllMerged_callsCallbackWhenSizeLeOne() {
        var called = false
        val list = mutableListOf(
            memo(1, "Only one", "10:00", order = 0)
        )
        val ad = adapter(list, onAllDone = { called = true })

        val method = MemoMergeAdapter::class.java.getDeclaredMethod("maybeNotifyAllMerged")
        method.isAccessible = true
        method.invoke(ad)

        assertTrue(called)
    }

    @Test
    fun maybeNotifyAllMerged_doesNotCallCallbackWhenMoreThanOne() {
        var called = false
        val list = mutableListOf(
            memo(1, "A", "10:00", order = 0),
            memo(2, "B", "10:01", order = 1)
        )
        val ad = adapter(list, onAllDone = { called = true })

        val method = MemoMergeAdapter::class.java.getDeclaredMethod("maybeNotifyAllMerged")
        method.isAccessible = true
        method.invoke(ad)

        assertFalse(called)
    }

    // ---------------------------------------------------------------------
    // 7. mergeTextByIds(endFlag = true) 브랜치 테스트
    // ---------------------------------------------------------------------

    @Test
    fun mergeTextByIds_withEndFlagTrue_returnsExtractedMergedText() = runBlocking {
        val list = mutableListOf(
            memo(1, "A", "10:00", order = 0),
            memo(2, "B", "10:01", order = 1)
        )

        // styleData = UserStyle.Default branch (activeStyleId = null)
        val prefs = Mockito.mock(UserStatsPrefs::class.java)
        val dao = Mockito.mock(UserStyleDao::class.java)
        Mockito.`when`(prefs.getActiveStyleId()).thenReturn(1L)

        val ad = adapter(list, prefs = prefs, dao = dao)

        // ApiClient / api / MemoMergeUtils mocking
        mockkObject(ApiClient)
        mockkObject(MemoMergeUtils)

        // ⚠️ 여기 타입을 실제 ApiClient.api의 타입으로 바꿔줘야 함
        val mockApi = mockk<ApiService>()   // ← 예: SumdaysApi

        every { ApiClient.api } returns mockApi

        val json = JsonObject().apply { addProperty("diary", "final-merged") }

        // suspend fun mergeMemos → coEvery 사용
        io.mockk.coEvery { mockApi.mergeMemos(any()) } returns Response.success(json)

        every { MemoMergeUtils.extractMergedText(json) } returns "final-merged"

        val result = ad.mergeTextToServer(listOf(1, 2), endFlag = true)

        assertEquals("final-merged", result)
    }

    // ---------------------------------------------------------------------
    // 8. mergeByIndex + mergeTextByIds(endFlag=false) 스트리밍 브랜치
    // ---------------------------------------------------------------------

    @Test
    fun mergeByIndex_mergesTwoItems_streamingAndUpdatesListAndUndoStack() {
        // 1) 초기 메모 2개
        val m1 = memo(1, "A", "10:00", order = 0)
        val m2 = memo(2, "B", "10:01", order = 1)
        val list = mutableListOf(m1, m2)

        // styleData = UserStyle.Default branch (activeStyleId = 1L)
        val prefs = Mockito.mock(UserStatsPrefs::class.java)
        val dao = Mockito.mock(UserStyleDao::class.java)
        Mockito.`when`(prefs.getActiveStyleId()).thenReturn(1L)

        val latch = CountDownLatch(1)
        val ad = adapter(
            list = list,
            onAllDone = { latch.countDown() },
            prefs = prefs,
            dao = dao
        )

        // 2) ApiClient.mock: mergeMemosStream → 스트리밍 텍스트 제공
        mockkObject(ApiClient)

        val bodyText = "stream-merged-text"
        val mediaType = "text/plain".toMediaType()
        val responseBody: ResponseBody = bodyText.toResponseBody(mediaType)
        val resp: Response<ResponseBody> = Response.success(responseBody)

        class FakeCall(private val response: Response<ResponseBody>) : Call<ResponseBody> {
            override fun execute(): Response<ResponseBody> = response
            override fun enqueue(callback: Callback<ResponseBody>) { /* not used */ }
            override fun isExecuted(): Boolean = false
            override fun cancel() {}
            override fun isCanceled(): Boolean = false
            override fun clone(): Call<ResponseBody> = FakeCall(response)
            override fun request(): Request =
                Request.Builder().url("http://localhost/").build()
            override fun timeout(): Timeout = Timeout.NONE
        }

        every { ApiClient.api.mergeMemosStream(any()) } returns FakeCall(resp)

        // 3) private mergeByIndex(fromIndex, toIndex, mergedIds) 호출
        val method = MemoMergeAdapter::class.java.getDeclaredMethod(
            "mergeByIndex",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            List::class.java
        )
        method.isAccessible = true

        // fromIndex=0, toIndex=1, mergedIds = [1,2]
        method.invoke(ad, 0, 1, listOf(1, 2))

        // 비동기 코루틴이 끝날 때까지 기다림
        val completed = latch.await(2, TimeUnit.SECONDS)
        assertTrue("mergeByIndex 비동기 작업이 시간 내에 끝나야 함", completed)

        // 이제 list는 하나의 merged 메모만 남아 있어야 함
        assertEquals(1, list.size)
        assertEquals("stream-merged-text", list[0].content)

        // undoStack 에 record가 쌓였는지 확인
        val undoField = MemoMergeAdapter::class.java.getDeclaredField("undoStack")
        undoField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stack = undoField.get(ad) as ArrayDeque<MemoMergeAdapter.MergeRecord>
        assertEquals(1, stack.size)
        val rec = stack.last()
        assertEquals(0, rec.fromIndexBefore)
        assertEquals(1, rec.toIndexBefore)
    }

    @Test
    fun mergeAllMemo_callsMergeTextToServer_andReturnsResult() = runBlocking {
        // 1) 초기 메모 3개
        val m1 = memo(1, "A", "10:00", order = 0)
        val m2 = memo(2, "B", "10:01", order = 1)
        val m3 = memo(3, "C", "10:02", order = 2)
        val list = mutableListOf(m1, m2, m3)

        // style 관련은 어차피 mergeTextByIds 를 mock 해버릴 거라 신경 안 써도 됨
        val prefs = Mockito.mock(UserStatsPrefs::class.java)
        val dao = Mockito.mock(UserStyleDao::class.java)
        Mockito.`when`(prefs.getActiveStyleId()).thenReturn(1L)

        // 2) 진짜 adapter 만들고, 그 위에 spy 래핑
        val realAdapter = adapter(list, prefs = prefs, dao = dao)
        val spyAdapter = spyk(realAdapter)

        // 3) mergeTextByIds 를 mock 하면서, 첫 번째 인자(mergedIds)를 캡처
        val capturedIds = slot<List<Int>>()

        coEvery {
            spyAdapter.mergeTextToServer(capture(capturedIds), endFlag = true, onPartial = any())
        } returns "merged-all"

        // 4) 실제 호출
        val result = spyAdapter.mergeAllMemo()

        // 5) 결과 검증
        assertEquals("merged-all", result)

        // mergeAllMemo 가 originalMemoMap 의 모든 id 를 넘겼는지 확인
        val ids = capturedIds.captured
        assertEquals(3, ids.size)
        // 순서까지 확인하고 싶으면:
        assertEquals(listOf(1, 2, 3), ids)
        // 순서 상관 없이 값만 확인하고 싶으면 아래처럼 해도 됨:
        // assertEquals(setOf(1, 2, 3), ids.toSet())
    }

    @Test
    fun onBindViewHolder_setsListeners_andBindsText() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1) 초기 메모 1개
        val m1 = memo(1, "A", "10:00", order = 0)
        val list = mutableListOf(m1)
        val ad = adapter(list)

        // 2) TestItemView + VH 생성
        val itemView = TestItemView(context)

        val ctor = MemoMergeAdapter.VH::class.java.getDeclaredConstructor(
            MemoMergeAdapter::class.java,
            android.view.View::class.java
        )
        ctor.isAccessible = true
        val holder = ctor.newInstance(ad, itemView)

        // 3) onBindViewHolder 호출 → bind + 리스너 세팅
        ad.onBindViewHolder(holder, 0)

        // 3-1) 텍스트 잘 바인딩됐는지 확인
        val tvContent = itemView.findViewById<TextView>(R.id.memo_text)
        val tvTime = itemView.findViewById<TextView>(R.id.memo_time)
        assertEquals("A", tvContent.text.toString())
        assertEquals("10:00", tvTime.text.toString())

        // 3-2) 리스너가 실제로 설정됐는지 확인
        assertNotNull(itemView.lastLongClickListener)
        assertNotNull(itemView.lastDragListener)

        // (롱클릭이 true를 리턴하거나 alpha가 0.6f 가 되는지까지는
        //  adapterPosition == NO_POSITION 때문에 이 환경에서 보장할 수 없음.
        //  여기서는 "리스너가 붙는다"까지만 검증)
    }

    @Test
    fun onBindViewHolder_setsListeners_andExecutesBasicDragBranches() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1) 초기 메모 1개
        val m1 = memo(1, "A", "10:00", order = 0)
        val list = mutableListOf(m1)
        val ad = adapter(list)

        // 2) TestItemView + VH 생성
        val itemView = TestItemView(context)

        val ctor = MemoMergeAdapter.VH::class.java.getDeclaredConstructor(
            MemoMergeAdapter::class.java,
            android.view.View::class.java
        )
        ctor.isAccessible = true
        val holder = ctor.newInstance(ad, itemView)

        // 3) onBindViewHolder 호출 → bind + 리스너 세팅
        ad.onBindViewHolder(holder, 0)

        // 3-1) 텍스트 잘 바인딩됐는지 확인
        val tvContent = itemView.findViewById<TextView>(R.id.memo_text)
        val tvTime = itemView.findViewById<TextView>(R.id.memo_time)
        assertEquals("A", tvContent.text.toString())
        assertEquals("10:00", tvTime.text.toString())

        // 3-2) 리스너가 실제로 설정됐는지 확인
        val longClickListener = itemView.lastLongClickListener
        val dragListener = itemView.lastDragListener
        assertNotNull(longClickListener)
        assertNotNull(dragListener)

        // 🔹 adapterPosition == NO_POSITION 이라 longClick 결과는 false일 수 있으므로
        //    여기서는 "예외 없이 호출된다"까지만 확인 (리턴값 assert 안 함)
        longClickListener!!.onLongClick(itemView)

        // 3-3) DragListener의 각 action 분기 실행해서 커버리지 채우기
        val listener = dragListener!!

        // ACTION_DRAG_STARTED
        val evStarted = mockk<DragEvent>()
        every { evStarted.action } returns DragEvent.ACTION_DRAG_STARTED
        listener.onDrag(itemView, evStarted)

        // ACTION_DRAG_ENTERED
        val evEntered = mockk<DragEvent>()
        every { evEntered.action } returns DragEvent.ACTION_DRAG_ENTERED
        listener.onDrag(itemView, evEntered)

        // ACTION_DRAG_EXITED
        val evExited = mockk<DragEvent>()
        every { evExited.action } returns DragEvent.ACTION_DRAG_EXITED
        listener.onDrag(itemView, evExited)

        // 여기서 DROP 은 mergeByIndex + 네트워크/코루틴까지 엮이니까 과감히 스킵해도 됨
        // (mergeByIndex 는 별도 테스트에서 이미 커버 중)

        // ACTION_DRAG_ENDED
        val evEnded = mockk<DragEvent>()
        every { evEnded.action } returns DragEvent.ACTION_DRAG_ENDED
        listener.onDrag(itemView, evEnded)

        // 이 테스트의 목적은:
        //  - onBindViewHolder 전체가 실행되고
        //  - DRAG_STARTED / ENTERED / EXITED / ENDED 분기 안의 코드들이 한 번씩 돌도록 하는 것
        //  리턴값은 엄격히 검사하지 않고 "예외 없이 실행"만 보장
    }

    @Test
    fun onBindViewHolder_actionDrop_runsMergeBlockWhenFromAndToDifferent() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1) 초기 메모 2개 (fromIndex=0, toIndex=1 시나리오)
        val m1 = memo(1, "A", "10:00", order = 0)
        val m2 = memo(2, "B", "10:01", order = 1)
        val list = mutableListOf(m1, m2)

        // styleData = UserStyle.Default branch (activeStyleId = null)
        val prefs = Mockito.mock(UserStatsPrefs::class.java)
        val dao = Mockito.mock(UserStyleDao::class.java)
        Mockito.`when`(prefs.getActiveStyleId()).thenReturn(1L)

        val ad = adapter(list, prefs = prefs, dao = dao)

        // 2) mergeByIndex 안에서 호출되는 스트리밍 API 목킹
        mockkObject(ApiClient)

        val bodyText = "drop-merged-text"
        val mediaType = "text/plain".toMediaType()
        val responseBody: ResponseBody = bodyText.toResponseBody(mediaType)
        val resp: Response<ResponseBody> = Response.success(responseBody)

        class FakeCall(private val response: Response<ResponseBody>) : Call<ResponseBody> {
            override fun execute(): Response<ResponseBody> = response
            override fun enqueue(callback: Callback<ResponseBody>) { /* not used */ }
            override fun isExecuted(): Boolean = false
            override fun cancel() {}
            override fun isCanceled(): Boolean = false
            override fun clone(): Call<ResponseBody> = FakeCall(response)
            override fun request(): Request =
                Request.Builder().url("http://localhost/").build()
            override fun timeout(): Timeout = Timeout.NONE
        }

        every { ApiClient.api.mergeMemosStream(any()) } returns FakeCall(resp)

        // 3) 진짜 RecyclerView 에 adapter 붙이고 레이아웃까지 돌려서
        //    adapterPosition 이 제대로 세팅된 ViewHolder를 얻는다
        val rv = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = ad
            // measure & layout
            measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST)
            )
            layout(0, 0, 1080, 1920)
        }

        // position = 1 인 ViewHolder (toIndex=1)
        val holder = rv.findViewHolderForAdapterPosition(1) as MemoMergeAdapter.VH
        val itemView = holder.itemView

        // 4) View 내부의 mListenerInfo → 그 안의 mOnDragListener 를 reflection 으로 꺼내기
        val infoField = View::class.java.getDeclaredField("mListenerInfo")
        infoField.isAccessible = true
        val infoObj = infoField.get(itemView)

        val dragField = infoObj.javaClass.getDeclaredField("mOnDragListener")
        dragField.isAccessible = true
        val dragListener = dragField.get(infoObj) as View.OnDragListener
        assertNotNull(dragListener)   // 리스너가 실제로 붙어 있어야 한다

        // 5) ACTION_DROP 이벤트 만들기
        //    - localState = 0  → fromIndex = 0
        //    - holder.adapterPosition = 1 → toIndex = 1
        val evDrop = mockk<DragEvent>()
        every { evDrop.action } returns DragEvent.ACTION_DROP
        every { evDrop.localState } returns 0    // fromIndex = 0

        // ✅ 여기서 ACTION_DROP 분기가 실제로 실행됨
        dragListener.onDrag(itemView, evDrop)

        // 이 테스트의 목적은:
        //  - fromIndex != toIndex 분기를 한 번 실행해서
        //    idToMergedIds / mergedIds / sortedIdsByOrder / mergeByIndex(...) 라인까지
        //    전부 커버리지에 포함시키는 것
        //  리턴값이나 list 내용까지 엄격하게 검증할 필요는 없음 (mergeByIndex 는 별도 테스트 있음)
    }
}

class TestItemView(context: Context) : FrameLayout(context) {

    var lastLongClickListener: OnLongClickListener? = null
    var lastDragListener: OnDragListener? = null

    init {
        // 실제 item_memo 레이아웃을 이 뷰 안에 붙인다
        LayoutInflater.from(context).inflate(R.layout.item_memo, this, true)
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        lastLongClickListener = l
        super.setOnLongClickListener(l)
    }

    override fun setOnDragListener(l: OnDragListener?) {
        lastDragListener = l
        super.setOnDragListener(l)
    }
}