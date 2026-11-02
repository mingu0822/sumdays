package com.example.sumdays.mission

import android.content.Context
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.shop.PointPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneId

interface MissionRepository {
    suspend fun getMissions(): List<Mission>
    suspend fun claimReward(missionCode: String): Int?
    fun getAdRewardState(): AdRewardState
    suspend fun claimAdReward(rewardId: String): AdRewardResult
}

object MissionCatalog {
    val definitions = listOf(
        MissionDefinition(
            code = MissionCode.DAILY_WRITE_MEMO,
            category = MissionCategory.DAILY,
            title = "메모 ${MissionConfig.DAILY_MEMO_TARGET}회 작성하기",
            description = "오늘 작성한 메모가 진행도에 반영돼요",
            targetCount = MissionConfig.DAILY_MEMO_TARGET,
            rewardPoints = MissionConfig.DAILY_MEMO_REWARD,
            displayOrder = 0,
        ),
        MissionDefinition(
            code = MissionCode.DAILY_COMPLETE_DIARY,
            category = MissionCategory.DAILY,
            title = "오늘의 일기 완성하기",
            description = "메모를 모아 오늘의 일기를 완성해 보세요",
            targetCount = MissionConfig.DAILY_DIARY_TARGET,
            rewardPoints = MissionConfig.DAILY_DIARY_REWARD,
            displayOrder = 1,
        ),
        MissionDefinition(
            code = MissionCode.DAILY_VIEW_FRIEND_DIARY,
            category = MissionCategory.DAILY,
            title = "친구 일기 구경하기",
            description = "친구의 공개된 일기를 한 번 읽어 보세요",
            targetCount = MissionConfig.DAILY_FRIEND_DIARY_TARGET,
            rewardPoints = MissionConfig.DAILY_FRIEND_DIARY_REWARD,
            displayOrder = 2,
        ),
        MissionDefinition(
            code = MissionCode.WEEKLY_DIARY_STREAK,
            category = MissionCategory.WEEKLY,
            title = "${MissionConfig.WEEKLY_STREAK_TARGET}일 연속 일기 작성하기",
            description = "이번 주에 연속으로 일기를 완성해 보세요",
            targetCount = MissionConfig.WEEKLY_STREAK_TARGET,
            rewardPoints = MissionConfig.WEEKLY_STREAK_REWARD,
            displayOrder = 0,
        ),
        MissionDefinition(
            code = MissionCode.WEEKLY_WRITE_DIARY,
            category = MissionCategory.WEEKLY,
            title = "이번 주 일기 ${MissionConfig.WEEKLY_DIARY_TARGET}회 작성하기",
            description = "이번 주에 완성한 일기 수를 확인해요",
            targetCount = MissionConfig.WEEKLY_DIARY_TARGET,
            rewardPoints = MissionConfig.WEEKLY_DIARY_REWARD,
            displayOrder = 1,
        ),
        MissionDefinition(
            code = MissionCode.WEEKLY_SUMMARY_REWARD,
            category = MissionCategory.REWARD,
            title = "주간 통계 확인하기",
            description = "새로운 주간 통계를 얻어 확인해 보세요",
            targetCount = MissionConfig.WEEKLY_SUMMARY_TARGET,
            rewardPoints = MissionConfig.WEEKLY_SUMMARY_REWARD,
            displayOrder = 0,
        ),
    )
}

class LocalMissionRepository(
    context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context),
) : MissionRepository {
    private val appContext = context.applicationContext
    private val claimMutex = Mutex()

    override suspend fun getMissions(): List<Mission> {
        val today = koreaToday()
        val weekStart = today.with(DayOfWeek.MONDAY)
        val weekEnd = weekStart.plusDays(6)

        val roomProgress = withContext(Dispatchers.IO) {
            val memoCount = database.memoDao().getMemoCountByDate(today.toString())
            val todayEntry = database.dailyEntryDao().getEntrySnapshot(today.toString())
            val weeklyWrittenDates = database.dailyEntryDao()
                .getEntriesBetween(weekStart.toString(), weekEnd.toString())
                .asSequence()
                .filter { !it.isDeleted && !it.diary.isNullOrBlank() }
                .map { LocalDate.parse(it.date) }
                .distinct()
                .sorted()
                .toList()

            RoomMissionProgress(
                memoCount = memoCount,
                isTodayDiaryComplete = !todayEntry?.diary.isNullOrBlank() && todayEntry?.isDeleted == false,
                weeklyDiaryCount = weeklyWrittenDates.size,
                weeklyStreak = longestConsecutiveDays(weeklyWrittenDates),
            )
        }
        val summaryRewardState = MissionEventPrefs.getWeeklySummaryRewardState(appContext)

        val progressByCode = mapOf(
            MissionCode.DAILY_WRITE_MEMO to roomProgress.memoCount,
            MissionCode.DAILY_COMPLETE_DIARY to if (roomProgress.isTodayDiaryComplete) 1 else 0,
            MissionCode.DAILY_VIEW_FRIEND_DIARY to if (
                MissionEventPrefs.hasFriendDiaryView(appContext, today)
            ) 1 else 0,
            MissionCode.WEEKLY_DIARY_STREAK to roomProgress.weeklyStreak,
            MissionCode.WEEKLY_WRITE_DIARY to roomProgress.weeklyDiaryCount,
            MissionCode.WEEKLY_SUMMARY_REWARD to if (summaryRewardState.readySummaryId != null) 1 else 0,
        )

        return MissionCatalog.definitions.map { definition ->
            val progress = progressByCode[definition.code]
                ?.coerceIn(0, definition.targetCount)
                ?: 0
            val status = if (definition.code == MissionCode.WEEKLY_SUMMARY_REWARD) {
                when {
                    summaryRewardState.readySummaryId != null -> MissionStatus.CLAIMABLE
                    summaryRewardState.latestSummaryRewarded -> MissionStatus.CLAIMED
                    else -> MissionStatus.IN_PROGRESS
                }
            } else {
                when {
                    MissionStatePrefs.isClaimed(appContext, definition, today, weekStart) ->
                        MissionStatus.CLAIMED
                    progress >= definition.targetCount -> MissionStatus.CLAIMABLE
                    else -> MissionStatus.IN_PROGRESS
                }
            }

            Mission(definition, progress, status)
        }
    }

    override suspend fun claimReward(missionCode: String): Int? = claimMutex.withLock {
        val mission = getMissions().firstOrNull {
            it.definition.code == missionCode
        } ?: return@withLock null

        if (mission.status != MissionStatus.CLAIMABLE) return@withLock null

        if (missionCode == MissionCode.WEEKLY_SUMMARY_REWARD) {
            val summaryId = MissionEventPrefs.getWeeklySummaryRewardState(appContext)
                .readySummaryId ?: return@withLock null
            MissionEventPrefs.markWeeklySummaryRewarded(appContext, summaryId)
        } else {
            val today = koreaToday()
            val weekStart = today.with(DayOfWeek.MONDAY)
            MissionStatePrefs.markClaimed(appContext, mission.definition, today, weekStart)
        }

        val currentPoint = PointPrefs.getPoint(appContext)
        PointPrefs.savePoint(appContext, currentPoint + mission.definition.rewardPoints)
        mission.definition.rewardPoints
    }

    override fun getAdRewardState(): AdRewardState = AdRewardState(
        watchedCount = MissionEventPrefs.getAdViewCount(appContext, koreaToday()),
        dailyLimit = MissionConfig.AD_DAILY_LIMIT,
        rewardPoints = MissionConfig.AD_REWARD,
    )

    override suspend fun claimAdReward(rewardId: String): AdRewardResult = claimMutex.withLock {
        val state = getAdRewardState()
        if (MissionEventPrefs.isAdRewardProcessed(appContext, rewardId)) {
            return@withLock AdRewardResult.Granted(
                rewardPoints = state.rewardPoints,
                watchedCount = state.watchedCount,
                dailyLimit = state.dailyLimit,
            )
        }
        if (!state.canWatch) return@withLock AdRewardResult.DailyLimitReached

        val watchedCount = MissionEventPrefs.incrementAdViewCount(appContext, koreaToday())
        MissionEventPrefs.markAdRewardProcessed(appContext, rewardId)
        val currentPoint = PointPrefs.getPoint(appContext)
        PointPrefs.savePoint(appContext, currentPoint + state.rewardPoints)

        AdRewardResult.Granted(
            rewardPoints = state.rewardPoints,
            watchedCount = watchedCount,
            dailyLimit = state.dailyLimit,
        )
    }

    private fun longestConsecutiveDays(dates: List<LocalDate>): Int {
        if (dates.isEmpty()) return 0

        var longest = 1
        var current = 1
        dates.zipWithNext().forEach { (previous, next) ->
            if (previous.plusDays(1) == next) {
                current += 1
                longest = maxOf(longest, current)
            } else {
                current = 1
            }
        }
        return longest
    }

    private data class RoomMissionProgress(
        val memoCount: Int,
        val isTodayDiaryComplete: Boolean,
        val weeklyDiaryCount: Int,
        val weeklyStreak: Int,
    )
}

object MissionEventPrefs {
    private const val PREF_NAME = "mission_event_prefs"
    private const val KEY_FRIEND_DIARY_VIEW = "friend_diary_view"
    private const val KEY_AD_VIEW_COUNT = "ad_view_count"
    private const val KEY_PROCESSED_AD_REWARDS = "processed_ad_rewards"
    private const val KEY_WEEKLY_SUMMARY_CREATED = "weekly_summary_created"
    private const val KEY_WEEKLY_SUMMARY_VIEWED = "weekly_summary_viewed"
    private const val KEY_WEEKLY_SUMMARY_REWARDED = "weekly_summary_rewarded"

    fun markFriendDiaryViewed(context: Context) {
        saveBoolean(context, KEY_FRIEND_DIARY_VIEW, koreaToday().toString())
    }

    fun markWeeklySummaryCreated(context: Context, summaryId: String) {
        addToSet(context, KEY_WEEKLY_SUMMARY_CREATED, summaryId)
    }

    fun markWeeklySummaryViewed(context: Context, summaryId: String) {
        if (summaryId in readSet(context, KEY_WEEKLY_SUMMARY_CREATED)) {
            addToSet(context, KEY_WEEKLY_SUMMARY_VIEWED, summaryId)
        }
    }

    fun hasFriendDiaryView(context: Context, date: LocalDate): Boolean =
        readBoolean(context, KEY_FRIEND_DIARY_VIEW, date.toString())

    fun getAdViewCount(context: Context, date: LocalDate): Int =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(scopedKey("${KEY_AD_VIEW_COUNT}_${date}"), 0)

    fun incrementAdViewCount(context: Context, date: LocalDate): Int {
        val nextCount = getAdViewCount(context, date) + 1
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(scopedKey("${KEY_AD_VIEW_COUNT}_${date}"), nextCount)
            .apply()
        return nextCount
    }

    fun isAdRewardProcessed(context: Context, rewardId: String): Boolean =
        rewardId in readSet(context, KEY_PROCESSED_AD_REWARDS)

    fun markAdRewardProcessed(context: Context, rewardId: String) {
        addToSet(context, KEY_PROCESSED_AD_REWARDS, rewardId)
    }

    fun getWeeklySummaryRewardState(context: Context): WeeklySummaryRewardState {
        val created = readSet(context, KEY_WEEKLY_SUMMARY_CREATED)
        val viewed = readSet(context, KEY_WEEKLY_SUMMARY_VIEWED)
        val rewarded = readSet(context, KEY_WEEKLY_SUMMARY_REWARDED)
        val ready = created.intersect(viewed).minus(rewarded).sorted().firstOrNull()
        val latest = created.maxOrNull()

        return WeeklySummaryRewardState(
            readySummaryId = ready,
            latestSummaryRewarded = latest != null && latest in rewarded,
        )
    }

    fun markWeeklySummaryRewarded(context: Context, summaryId: String) {
        addToSet(context, KEY_WEEKLY_SUMMARY_REWARDED, summaryId)
    }

    private fun saveBoolean(context: Context, event: String, period: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(scopedKey("${event}_$period"), true)
            .apply()
    }

    private fun readBoolean(context: Context, event: String, period: String): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(scopedKey("${event}_$period"), false)

    private fun addToSet(context: Context, key: String, value: String) {
        val values = readSet(context, key).toMutableSet().apply { add(value) }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(scopedKey(key), values)
            .apply()
    }

    private fun readSet(context: Context, key: String): Set<String> =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getStringSet(scopedKey(key), emptySet())
            ?.toSet()
            .orEmpty()

    private fun scopedKey(key: String): String = "user_${SessionManager.getUserId()}_$key"

    data class WeeklySummaryRewardState(
        val readySummaryId: String?,
        val latestSummaryRewarded: Boolean,
    )
}

private object MissionStatePrefs {
    private const val PREF_NAME = "mission_claim_prefs"

    fun isClaimed(
        context: Context,
        definition: MissionDefinition,
        today: LocalDate,
        weekStart: LocalDate,
    ): Boolean = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        .getBoolean(scopedClaimKey(definition, today, weekStart), false)

    fun markClaimed(
        context: Context,
        definition: MissionDefinition,
        today: LocalDate,
        weekStart: LocalDate,
    ) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(scopedClaimKey(definition, today, weekStart), true)
            .apply()
    }

    private fun scopedClaimKey(
        definition: MissionDefinition,
        today: LocalDate,
        weekStart: LocalDate,
    ): String = "user_${SessionManager.getUserId()}_${claimKey(definition, today, weekStart)}"

    private fun claimKey(
        definition: MissionDefinition,
        today: LocalDate,
        weekStart: LocalDate,
    ): String {
        val period = when (definition.category) {
            MissionCategory.DAILY -> today.toString()
            MissionCategory.WEEKLY, MissionCategory.REWARD -> weekStart.toString()
        }
        return "claimed_${definition.code}_$period"
    }
}

private fun koreaToday(): LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
