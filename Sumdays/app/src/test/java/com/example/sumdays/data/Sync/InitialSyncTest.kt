package com.example.sumdays.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.app.ActivityManager
import android.util.Log
import androidx.work.*
import androidx.room.RoomDatabase // withTransaction 모킹을 위해 필요
import androidx.room.withTransaction
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.data.dao.DailyEntryDao
import com.example.sumdays.data.dao.MemoDao
import com.example.sumdays.data.dao.UserStyleDao
import com.example.sumdays.data.dao.WeekSummaryDao
import com.example.sumdays.network.ApiClient
import com.example.sumdays.network.apiService.ApiService
import io.mockk.*
import kotlinx.coroutines.runBlocking
import retrofit2.Response
import java.io.IOException
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class InitialSyncTest {

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
        // 1. Context 및 WorkManager 환경 Mock (모든 Android 오류 해결)
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context

        // Android 시스템 서비스 및 파일 경로 Mock
        every { context.getSystemService(any()) } returns mockk<ActivityManager>(relaxed = true) // ActivityManager/ConnectivityManager 대체
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns mockk<ActivityManager>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockk<ConnectivityManager>(relaxed = true)
        val mockFilesDir = File(".")
        every { context.getDatabasePath(any()) } returns mockFilesDir
        every { context.filesDir } returns mockFilesDir

        // WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workerParams = mockk(relaxed = true)

        // 2. DAO 및 DB Mock
        mockMemoDao = mockk(relaxed = true)
        mockDailyDao = mockk(relaxed = true)
        mockStyleDao = mockk(relaxed = true)
        mockWeekDao = mockk(relaxed = true)
        mockDb = mockk<AppDatabase>(relaxed = true)
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getDatabase(any()) } returns mockDb
        every { mockDb.memoDao() } returns mockMemoDao
        every { mockDb.dailyEntryDao() } returns mockDailyDao
        every { mockDb.userStyleDao() } returns mockStyleDao
        every { mockDb.weekSummaryDao() } returns mockWeekDao

        // ★★★ withTransaction Mock: 필수 ★★★
        // withTransaction 내부 코드가 실행되어 DAO clearAll, insertAll이 호출되도록 보장
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { (mockDb as RoomDatabase).withTransaction(any<suspend () -> Any?>()) } coAnswers {
            val block = arg<suspend () -> Any?>(0)
            block()
        }

        // 3. Session & Network Mocking
        mockkObject(SessionManager)
        every { SessionManager.getToken() } returns "VALID_TOKEN"
        mockkObject(ApiClient)
        mockApiService = mockk(relaxed = true)
        every { ApiClient.api } returns mockApiService

        // 4. Log Mocking
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    @AfterTest
    fun tearDown() {
        unmockkAll()
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    // =========================================================================
    // 1. Success Case (Full Sync & Conversion Logic Coverage)
    // =========================================================================

    private fun createFullMockServerData(): SyncFetchResponse {
        val anyMock = mockk<Any>(relaxed = true)

        return SyncFetchResponse(
            memo = listOf(MemoPayload(room_id = 1, date = "d1", memo_order = 1, content = "m1", timestamp = "09:00", type = "text")),
            dailyEntry = emptyList(),
            userStyle = listOf(UserStylePayload(styleId = 1L, styleName = "S", styleVector = listOf(0.1f), styleExamples = listOf("e"), stylePrompt = anyMock, sampleDiary = "s_d")),
            weekSummary = listOf(WeekSummaryPayload(startDate = "w1", endDate = "w2", diaryCount = 5,
                emotionAnalysis = anyMock, highlights = anyMock, insights = anyMock, summary = anyMock))
        )
    }
    /*
    @Test
    fun `01_SyncWorker_success_path_covers_db_clear_insert_and_conversion_logic`() {
        runBlocking {
            // 1. API Success Mock (모든 데이터 타입 포함)
            val serverData = createFullMockServerData()
            coEvery { mockApiService.fetchServerData(any()) } returns Response.success(serverData)

            val worker = InitialSyncWorker(context, workerParams)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)

            // 2. DB ClearAll 호출 검증
            coVerify(exactly = 1) { mockMemoDao.clearAll() }
            coVerify(exactly = 1) { mockDailyDao.clearAll() }
            coVerify(exactly = 1) { mockStyleDao.clearAll() }
            coVerify(exactly = 1) { mockWeekDao.clearAll() }

            // 3. DB InsertAll 호출 검증 (데이터 변환 및 삽입 경로 커버)
            coVerify { mockMemoDao.insertAll(any()) }
            coVerify { mockDailyDao.insertAll(any()) }
            coVerify { mockStyleDao.insertAll(any()) }
            coVerify { mockWeekDao.insertAll(any()) }

            // 4. Log.d("Sync-DailyEntry", ...) 호출 경로 커버
            coVerify(atLeast = 1) { Log.d("Sync-DailyEntry", any()) }

            // 5. withTransaction 호출 검증 (필수)
            coVerify(atLeast = 1) { (mockDb as RoomDatabase).withTransaction(any<suspend () -> Any?>()) }
        }
    }
       */
    // =========================================================================
    // 2. Failure Cases (모든 Failure 브랜치 커버)
    // =========================================================================

    @Test
    fun `02_SyncWorker_fails_on_token_error_branch`() {
        runBlocking {
            // 0) token == null 브랜치 커버
            every { SessionManager.getToken() } returns null

            val worker = InitialSyncWorker(context, workerParams)
            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Failure)
            assertEquals("token_error", (result as ListenableWorker.Result.Failure).outputData.getString("type"))
        }
    }

    @Test
    fun `03_SyncWorker_fails_on_unsuccessful_network_response_or_null_body`() {
        runBlocking {
            // 1) !response.isSuccessful 브랜치 커버 (HTTP 404)
            coEvery { mockApiService.fetchServerData(any()) } returns Response.error(404, mockk(relaxed = true))
            var worker = InitialSyncWorker(context, workerParams)
            var result = worker.doWork()
            assertEquals(ListenableWorker.Result.failure(), result)

            // 2) response.body() == null 브랜치 커버
            coEvery { mockApiService.fetchServerData(any()) } returns Response.success(null)
            worker = InitialSyncWorker(context, workerParams)
            result = worker.doWork()
            assertEquals(ListenableWorker.Result.failure(), result)
        }
    }

    @Test
    fun `04_SyncWorker_fails_on_exception_and_covers_catch_block`() {
        runBlocking {
            // Catch 블록 커버
            coEvery { mockApiService.fetchServerData(any()) } throws IOException("API Timeout")

            val worker = InitialSyncWorker(context, workerParams)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            // Log.d("initWork", ...) 호출 경로 커버
            coVerify(atLeast = 1) { Log.d("initWork", any()) }
        }
    }
}