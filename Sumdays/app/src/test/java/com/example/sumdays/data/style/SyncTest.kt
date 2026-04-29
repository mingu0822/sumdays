package com.example.sumdays.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.daily.memo.Memo
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.data.DailyEntry
import com.example.sumdays.data.dao.DailyEntryDao
import com.example.sumdays.data.dao.MemoDao
import com.example.sumdays.data.dao.UserStyleDao
import com.example.sumdays.data.dao.WeekSummaryDao
import com.example.sumdays.data.style.UserStyle
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.ApiService
import com.example.sumdays.statistics.WeekSummary
import io.mockk.*
import kotlinx.coroutines.test.runTest
import retrofit2.Response
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var mockDb: AppDatabase
    private lateinit var mockMemoDao: MemoDao
    private lateinit var mockDailyDao: DailyEntryDao
    private lateinit var mockStyleDao: UserStyleDao
    private lateinit var mockWeekDao: WeekSummaryDao
    private lateinit var mockApiService: ApiService

    @BeforeTest
    fun setup() {
        // Context 설정 (AbstractMethodError 방지)
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context

        workerParams = mockk(relaxed = true)

        // DAO Mocks
        mockMemoDao = mockk(relaxed = true)
        mockDailyDao = mockk(relaxed = true)
        mockStyleDao = mockk(relaxed = true)
        mockWeekDao = mockk(relaxed = true)

        // Database Mocking
        mockDb = mockk(relaxed = true)
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getDatabase(any()) } returns mockDb

        every { mockDb.memoDao() } returns mockMemoDao
        every { mockDb.dailyEntryDao() } returns mockDailyDao
        every { mockDb.userStyleDao() } returns mockStyleDao
        every { mockDb.weekSummaryDao() } returns mockWeekDao

        // Session & Network Mocking
        mockkObject(SessionManager)
        mockkObject(ApiClient)
        mockApiService = mockk(relaxed = true)
        every { ApiClient.api } returns mockApiService

        // Log Mocking
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    // =========================================================================
    // 1. DTO & Mapper Logic Test
    // =========================================================================
    @Test
    fun `SyncDTO should correctly map local entities to sync payload`() {
        val memos = listOf(mockk<Memo>(relaxed = true) { every { id } returns 1 })
        val styles = listOf(mockk<UserStyle>(relaxed = true) { every { styleId } returns 1L })
        val entries = listOf(mockk<DailyEntry>(relaxed = true) { every { date } returns "2023-11-01" })
        val summaries = listOf(mockk<WeekSummary>(relaxed = true) { every { startDate } returns "2023-11-01" })

        val deletedMemoIds = listOf(1, 2)
        val deletedStyleIds = listOf(10L)
        val deletedEntryDates = listOf("2023-10-31")
        val deletedSummaryDates = listOf("2023-10-25")

        val requestFull = buildSyncRequest(
            deletedMemoIds, deletedStyleIds, deletedEntryDates, deletedSummaryDates,
            memos, styles, entries, summaries
        )

        val requestEmpty = buildSyncRequest(
            emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList()
        )

        assertNotNull(requestFull.edited)
        assertNotNull(requestFull.deleted)

        assertTrue(requestEmpty.deleted == null)
        assertTrue(requestEmpty.edited == null)
    }

    // =========================================================================
    // 2. BackupWorker Test
    // =========================================================================
    @Test
    fun `BackupWorker should synchronize changes and reset flags on success`() = runTest {
        every { SessionManager.getToken() } returns "valid_token"

        // ★★★ [수정됨] suspend 함수이므로 every -> coEvery 로 변경 ★★★
        coEvery { mockMemoDao.getDeletedMemos() } returns listOf(mockk(relaxed = true))
        coEvery { mockMemoDao.getEditedMemos() } returns listOf(mockk(relaxed = true))
        coEvery { mockDailyDao.getDeletedEntries() } returns emptyList()
        coEvery { mockDailyDao.getEditedEntries() } returns emptyList()
        coEvery { mockStyleDao.getDeletedStyles() } returns emptyList()
        coEvery { mockStyleDao.getEditedStyles() } returns emptyList()

        val successResponse = Response.success(SyncResponse(status = "success", message = "OK"))
        coEvery { mockApiService.syncData(any(), any()) } returns successResponse

        val worker = BackupWorker(context, workerParams)
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify { mockMemoDao.resetDeletedFlags(any()) }
    }

    @Test
    fun `BackupWorker should handle invalid session token`() = runTest {
        every { SessionManager.getToken() } returns null
        val worker = BackupWorker(context, workerParams)
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun `BackupWorker should handle server failure`() = runTest {
        every { SessionManager.getToken() } returns "valid_token"
        // suspend 호출에 대한 기본값 설정
        coEvery { mockMemoDao.getDeletedMemos() } returns emptyList()
        coEvery { mockMemoDao.getEditedMemos() } returns emptyList()

        val errorResponse = Response.success(SyncResponse(status = "error", message = "Fail"))
        coEvery { mockApiService.syncData(any(), any()) } returns errorResponse

        val worker = BackupWorker(context, workerParams)
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }

    /*
    @Test
    fun `BackupWorker internal utilities integrity check`() = runTest {
        val worker = BackupWorker(context, workerParams)

        // 1. logTest (Private, 일반 함수) 호출 -> 성공
        val logTest = worker.javaClass.getDeclaredMethod("logTest", SyncRequest::class.java)
        logTest.isAccessible = true
        logTest.invoke(worker, SyncRequest(null, null))

        // [삭제됨] testEntityInsert는 private suspend 함수라 Reflection 호출 시 크래시 발생
        // 커버리지를 위해 굳이 테스트 코드를 넣기보다, 실제 사용되지 않는 함수라면 테스트에서 제외하는 게 안전함
    }
    */
    // =========================================================================
    // 3. InitialSyncWorker Test
    // =========================================================================

    /* @Test
    fun `InitialSyncWorker should fetch and overwrite local database`() = runTest {
        every { SessionManager.getToken() } returns "valid_token"
        val fetchResponse = SyncFetchResponse(emptyList(), emptyList(), emptyList(), emptyList())
        coEvery { mockApiService.fetchServerData(any()) } returns Response.success(fetchResponse)

        val worker = InitialSyncWorker(context, workerParams)
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify { mockMemoDao.clearAll() }
    }

     */

    @Test
    fun `InitialSyncWorker should fail on token error`() = runTest {
        every { SessionManager.getToken() } returns null
        val worker = InitialSyncWorker(context, workerParams)
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Failure)
    }

    // =========================================================================
    // 4. BackupScheduler Test
    // =========================================================================
    /*
    @Test
    fun `BackupScheduler should schedule tasks correctly`() {
        mockkStatic(WorkManager::class)
        val mockWorkManager = mockk<WorkManager>(relaxed = true)
        every { WorkManager.getInstance(any()) } returns mockWorkManager

        BackupScheduler.scheduleAutoBackup()

        verify { mockWorkManager.enqueueUniquePeriodicWork(any(), any(), any()) }

        BackupScheduler.triggerManualBackup()

        verify { mockWorkManager.enqueue(any<OneTimeWorkRequest>()) }
    }

     */
}