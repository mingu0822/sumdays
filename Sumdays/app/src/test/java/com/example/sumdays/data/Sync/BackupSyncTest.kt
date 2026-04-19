package com.example.sumdays.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import io.mockk.*
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import retrofit2.Response
import com.example.sumdays.data.*
import com.example.sumdays.network.*
import com.example.sumdays.auth.*
import com.example.sumdays.network.apiService.ApiService

class BackupSyncTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters

    @BeforeTest
    fun setup() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)

        // ---- WorkManager 전역 static mocking ----
        mockkStatic(WorkManager::class)
        val wm = mockk<WorkManager>(relaxed = true)
        every { WorkManager.getInstance(any<Context>()) } returns wm

        // ---- Log ----
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    // =====================================================================
    // 1. BackupScheduler 테스트
    // =====================================================================

    @Test
    fun `01 scheduleAutoBackup enqueues periodic work`() {
        val wm = WorkManager.getInstance(context)

        BackupScheduler.scheduleAutoBackup(context)

        verify {
            wm.enqueueUniquePeriodicWork(
                any(),
                ExistingPeriodicWorkPolicy.KEEP,
                any()
            )
        }
    }

    @Test
    fun `02 triggerManualBackup enqueues one time work`() {
        val wm = WorkManager.getInstance(context)

        BackupScheduler.triggerManualBackup(context)

        verify {
            wm.enqueue(any<OneTimeWorkRequest>())
        }
    }

    // =====================================================================
    // 2. BackupWorker 테스트 (커버리지만 높여서 단순 mock)
    // =====================================================================

    @Test
    fun `03 BackupWorker success path returns Result_success`() = runTest {
        // --- Session token mock ---
        mockkObject(SessionManager)
        every { SessionManager.getToken() } returns "valid_token"

        // --- DB / DAO mock ---
        mockkObject(AppDatabase.Companion)
        val fakeDb = mockk<AppDatabase>(relaxed = true)
        every { AppDatabase.getDatabase(any()) } returns fakeDb

        // fake DAO 들
        val memoDao = mockk<com.example.sumdays.data.dao.MemoDao>(relaxed = true)
        val styleDao = mockk<com.example.sumdays.data.dao.UserStyleDao>(relaxed = true)
        val entryDao = mockk<com.example.sumdays.data.dao.DailyEntryDao>(relaxed = true)
        val weekDao = mockk<com.example.sumdays.data.dao.WeekSummaryDao>(relaxed = true)

        every { fakeDb.memoDao() } returns memoDao
        every { fakeDb.userStyleDao() } returns styleDao
        every { fakeDb.dailyEntryDao() } returns entryDao
        every { fakeDb.weekSummaryDao() } returns weekDao

        // DAO 가 반환하는 리스트 = 전부 빈 리스트
        coEvery { memoDao.getDeletedMemos() } returns emptyList()
        coEvery { memoDao.getEditedMemos() } returns emptyList()
        coEvery { styleDao.getDeletedStyles() } returns emptyList()
        coEvery { styleDao.getEditedStyles() } returns emptyList()
        coEvery { entryDao.getDeletedEntries() } returns emptyList()
        coEvery { entryDao.getEditedEntries() } returns emptyList()
        coEvery { weekDao.getDeletedSummaries() } returns emptyList()
        coEvery { weekDao.getEditedSummaries() } returns emptyList()

        // --- API mock ---
        mockkObject(ApiClient)
        val apiMock = mockk<ApiService>(relaxed = true)
        every { ApiClient.api } returns apiMock

        coEvery {
            apiMock.syncData(any(), any())
        } returns Response.success(SyncResponse("success", "OK"))

        val worker = BackupWorker(context, workerParams)
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `04 BackupWorker returns failure when token missing`() = runTest {
        mockkObject(SessionManager)
        every { SessionManager.getToken() } returns null  // no token

        val worker = BackupWorker(context, workerParams)
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun `05 BackupWorker returns failure when server returns error`() = runTest {
        mockkObject(SessionManager)
        every { SessionManager.getToken() } returns "valid_token"

        // DB 빈 mock 준비
        mockkObject(AppDatabase.Companion)
        val fakeDb = mockk<AppDatabase>(relaxed = true)
        every { AppDatabase.getDatabase(any()) } returns fakeDb

        val memoDao = mockk<com.example.sumdays.data.dao.MemoDao>(relaxed = true)
        every { fakeDb.memoDao() } returns memoDao
        coEvery { memoDao.getDeletedMemos() } returns emptyList()
        coEvery { memoDao.getEditedMemos() } returns emptyList()

        // API mock – error 반환
        mockkObject(ApiClient)
        val apiMock = mockk<ApiService>(relaxed = true)
        every { ApiClient.api } returns apiMock

        coEvery {
            apiMock.syncData(any(), any())
        } returns Response.success(SyncResponse("error", "Fail"))

        val worker = BackupWorker(context, workerParams)
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }
}
