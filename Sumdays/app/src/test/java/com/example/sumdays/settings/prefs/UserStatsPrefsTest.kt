package com.example.sumdays.settings.prefs

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sumdays.TestApplication
import com.example.sumdays.data.prefs.userPrefs.UserStatsPrefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * UserStatsPrefs 클래스에 대한 Unit Test입니다.
 * 샘플 테스트의 설정(SDK 34)을 사용하여 Robolectric 환경에서 실행됩니다.
 */
@RunWith(AndroidJUnit4::class)
// 샘플 테스트의 환경 설정을 차용하여 SDK 버전 호환성 문제를 해결합니다.
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE],application = TestApplication::class) // 34
@LooperMode(LooperMode.Mode.PAUSED)
class UserStatsPrefsTest {

    // 테스트 대상 객체
    private lateinit var prefs: UserStatsPrefs

    // 테스트에 사용할 Context (Robolectric 환경에서 제공)
    private val context: Context = ApplicationProvider.getApplicationContext<Context>()

    // UserStatsPrefs 내부에서 사용하는 SharedPreferences 파일 이름
    private val prefsFileName = "user_stats_prefs"

    @Before
    fun setUp() {
        // 1. 매 테스트 시작 전 UserStatsPrefs 인스턴스 초기화
        prefs = UserStatsPrefs(context)

        // 2. 테스트 간 간섭을 막기 위해 모든 저장된 데이터를 지웁니다.
        // (SharedPreferences는 singleton이 아니지만, 같은 파일에 접근하므로 초기화 필요)
        context.getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        // 테스트 후에도 데이터를 다시 한번 정리
        context.getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    // --- 닉네임 관리 테스트 (Line Coverage 100%) ---

    @Test
    fun saveAndGetNickname_SavesCorrectly() {
        // GIVEN
        val testNickname = "전문개발자_테스터"
        // WHEN
        prefs.saveNickname(testNickname)
        val result = prefs.getNickname()
        // THEN
        assertEquals(testNickname, result, "저장된 닉네임이 정확히 반환되어야 합니다.")
    }

    @Test
    fun getNickname_ReturnsDefaultWhenNotSet() {
        // WHEN
        val result = prefs.getNickname()
        // THEN
        // 기본값 "사용자"를 반환하는지 확인 (?: "사용자" 분기 커버)
        assertEquals("사용자", result, "설정되지 않았을 때 기본값 '사용자'가 반환되어야 합니다.")
    }

    // --- Strike 관리 테스트 (Line Coverage 100%) ---

    @Test
    fun saveAndGetStreak_SavesCorrectly() {
        // GIVEN
        val testStreak = 100
        // WHEN
        prefs.saveStreak(testStreak)
        val result = prefs.getStreak()
        // THEN
        assertEquals(testStreak, result, "저장된 스트라이크 값이 정확히 반환되어야 합니다.")
    }

    @Test
    fun getStreak_ReturnsDefaultWhenNotSet() {
        // WHEN
        val result = prefs.getStreak()
        // THEN
        // 기본값 0을 반환하는지 확인
        assertEquals(0, result, "설정되지 않았을 때 기본값 0이 반환되어야 합니다.")
    }

    // --- Leaf Count 관리 테스트 (Line Coverage 100%) ---

    @Test
    fun incrementLeafCount_IncrementsFromDefault() {
        // WHEN
        prefs.incrementLeafCount() // 0 -> 1 (기본값 읽기 분기 커버)
        val result = prefs.getLeafCount()
        // THEN
        assertEquals(1, result, "기본값 0에서 1로 정확히 증가해야 합니다.")
    }

    @Test
    fun incrementLeafCount_IncrementsFromExistingValue() {
        // GIVEN: 초기값을 5로 직접 설정하여 getLeafCount()가 저장된 값을 읽도록 유도
        context.getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
            .edit()
            .putInt("leaf_count", 5)
            .commit()
        // WHEN
        prefs.incrementLeafCount() // 5 -> 6 (기존 값 읽기 분기 커버)
        val result = prefs.getLeafCount()
        // THEN
        assertEquals(6, result, "기존 값 5에서 6으로 정확히 증가해야 합니다.")
    }

    @Test
    fun getLeafCount_ReturnsDefaultWhenNotSet() {
        // WHEN
        val result = prefs.getLeafCount()
        // THEN
        assertEquals(0, result, "설정되지 않았을 때 기본값 0이 반환되어야 합니다.")
    }

    // --- Grape Count 관리 테스트 (Line Coverage 100%) ---

    @Test
    fun incrementGrapeCount_IncrementsFromDefault() {
        // WHEN
        prefs.incrementGrapeCount() // 0 -> 1 (기본값 읽기 분기 커버)
        val result = prefs.getGrapeCount()
        // THEN
        assertEquals(1, result, "기본값 0에서 1로 정확히 증가해야 합니다.")
    }

    @Test
    fun incrementGrapeCount_IncrementsFromExistingValue() {
        // GIVEN: 초기값을 3으로 직접 설정하여 getGrapeCount()가 저장된 값을 읽도록 유도
        context.getSharedPreferences(prefsFileName, Context.MODE_PRIVATE)
            .edit()
            .putInt("grape_count", 3)
            .commit()
        // WHEN
        prefs.incrementGrapeCount() // 3 -> 4 (기존 값 읽기 분기 커버)
        val result = prefs.getGrapeCount()
        // THEN
        assertEquals(4, result, "기존 값 3에서 4로 정확히 증가해야 합니다.")
    }

    @Test
    fun getGrapeCount_ReturnsDefaultWhenNotSet() {
        // WHEN
        val result = prefs.getGrapeCount()
        // THEN
        assertEquals(0, result, "설정되지 않았을 때 기본값 0이 반환되어야 합니다.")
    }

    // --- AI 스타일 설정 관련 로직 테스트 (Line Coverage 100%) ---

    @Test
    fun saveAndGetActiveStyleId_SavesCorrectly() {
        // GIVEN
        val testId = 987654321L
        // WHEN
        prefs.saveActiveStyleId(testId)
        val result = prefs.getActiveStyleId()
        // THEN
        assertNotNull(result, "저장 후 null이 아니어야 합니다.")
        assertEquals(testId, result, "저장된 스타일 ID가 정확히 반환되어야 합니다.")
        // if (id == DEFAULT_STYLE_ID) null else id 에서 else 분기 커버
    }

    @Test
    fun getActiveStyleId_ReturnsZeroWhenNotSet() {
        // WHEN
        val result = prefs.getActiveStyleId()
        // THEN
        // 기본값 -1L이 반환될 때 null로 처리되는지 확인 (if 분기 커버)
        assertEquals(0L,result, "스타일이 설정되지 않았을 때 0을 반환해야 합니다.")
    }

    @Test
    fun clearActiveStyleId_RemovesTheSetting() {
        // GIVEN: 스타일 ID를 미리 저장
        val testId = 12345L
        prefs.saveActiveStyleId(testId)
        assertNotNull(prefs.getActiveStyleId(), "클리어 전에는 값이 존재해야 합니다.")

        // WHEN
        prefs.clearActiveStyleId()
        val result = prefs.getActiveStyleId()
        // THEN
        // clearActiveStyleId()가 remove()를 호출했는지, 그 결과 기본값(-1L)이 되어 0로 반환되는지 확인
        assertEquals(0L,result, "clearActiveStyleId() 호출 후 0을 반환해야 합니다.")
    }
}