package com.example.sumdays.mission

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.sumdays.auth.SessionManager
import com.example.sumdays.data.sync.BackupSyncRunner
import com.example.sumdays.data.sync.InitialSyncWorker
import com.example.sumdays.network.ApiClient
import com.example.sumdays.shop.PointPrefs
import java.util.concurrent.TimeUnit

class NetworkMissionRepository(context: Context) : MissionRepository {
    private val appContext = context.applicationContext
    private var cachedAdState = AdRewardState(
        watchedCount = 0,
        dailyLimit = MissionConfig.AD_DAILY_LIMIT,
        rewardPoints = MissionConfig.AD_REWARD,
    )

    override suspend fun getMissions(): List<Mission> {
        val session = requireSession()
        prepareServerState(session)
        val response = ApiClient.api.getMissionDashboard(session.authorization)
        if (!response.isSuccessful) {
            throw IllegalStateException("미션 조회 실패: ${response.code()}")
        }
        val dashboard = response.body()?.data
            ?: throw IllegalStateException("미션 응답 데이터가 없습니다.")

        ensureCurrent(session)
        PointPrefs.savePoint(appContext, session.userId, dashboard.pointBalance)
        cachedAdState = dashboard.adReward.toDomain()
        return dashboard.missions.map { it.toDomain() }
    }

    override suspend fun claimReward(missionCode: String): Int? {
        val session = requireSession()
        prepareServerState(session)
        val response = ApiClient.api.claimMissionReward(session.authorization, missionCode)
        if (!response.isSuccessful) {
            throw IllegalStateException("미션 보상 지급 실패: ${response.code()}")
        }
        val reward = response.body()?.data ?: return null
        ensureCurrent(session)
        PointPrefs.savePoint(appContext, session.userId, reward.pointBalance)
        return reward.rewardPoints
    }

    override fun getAdRewardState(): AdRewardState = cachedAdState

    override suspend fun claimAdReward(rewardId: String): AdRewardResult {
        val session = requireSession()
        initializeWallet(session)
        val response = ApiClient.api.claimAdReward(
            session.authorization,
            AdRewardClaimRequest(rewardId),
        )
        ensureCurrent(session)
        if (response.code() == 409) return AdRewardResult.DailyLimitReached
        if (!response.isSuccessful) {
            throw IllegalStateException("광고 보상 지급 실패: ${response.code()}")
        }
        val reward = response.body()?.data
            ?: throw IllegalStateException("광고 보상 응답 데이터가 없습니다.")
        PointPrefs.savePoint(appContext, session.userId, reward.pointBalance)
        cachedAdState = AdRewardState(
            watchedCount = reward.watchedCount ?: cachedAdState.watchedCount,
            dailyLimit = reward.dailyLimit ?: cachedAdState.dailyLimit,
            rewardPoints = reward.rewardPoints,
        )
        return AdRewardResult.Granted(
            rewardPoints = reward.rewardPoints,
            watchedCount = cachedAdState.watchedCount,
            dailyLimit = cachedAdState.dailyLimit,
        )
    }

    private fun MissionResponse.toDomain(): Mission {
        val missionCategory = runCatching { MissionCategory.valueOf(category) }
            .getOrDefault(MissionCategory.DAILY)
        val missionStatus = runCatching { MissionStatus.valueOf(status) }
            .getOrDefault(MissionStatus.IN_PROGRESS)
        return Mission(
            definition = MissionDefinition(
                code = code,
                category = missionCategory,
                title = title,
                description = description,
                targetCount = targetCount,
                rewardPoints = rewardPoints,
                displayOrder = displayOrder,
            ),
            currentCount = currentCount,
            status = missionStatus,
        )
    }

    private fun AdRewardResponse.toDomain() = AdRewardState(
        watchedCount = watchedCount,
        dailyLimit = dailyLimit,
        rewardPoints = rewardPoints,
    )

    private suspend fun prepareServerState(session: AuthSession) {
        initializeWallet(session)
        val result = BackupSyncRunner.syncNow(appContext)
        if (!result.success) {
            throw IllegalStateException(result.message ?: "최신 데이터를 동기화하지 못했습니다.")
        }
        ensureCurrent(session)
    }

    private suspend fun initializeWallet(session: AuthSession) {
        val response = ApiClient.api.initializePointWallet(
            session.authorization,
            LegacyPointBalanceRequest(
                PointPrefs.getLegacyPointForMigration(appContext, session.userId)
            ),
        )
        if (!response.isSuccessful) {
            throw IllegalStateException("포인트 지갑 준비 실패: ${response.code()}")
        }
        val balance = response.body()?.data?.pointBalance
            ?: throw IllegalStateException("포인트 지갑 응답 데이터가 없습니다.")
        ensureCurrent(session)
        PointPrefs.savePoint(appContext, session.userId, balance)
        PointPrefs.markLegacyPointHandled(appContext, session.userId)
    }

    private fun requireSession(): AuthSession {
        val userId = SessionManager.getUserId()
        val token = SessionManager.getToken()
        if (userId < 0 || token == null) {
            throw IllegalStateException("로그인이 필요합니다.")
        }
        return AuthSession(userId, "Bearer $token")
    }

    private fun ensureCurrent(session: AuthSession) {
        if (SessionManager.getUserId() != session.userId) {
            throw IllegalStateException("요청 처리 중 로그인 계정이 변경되었습니다.")
        }
    }

    private data class AuthSession(
        val userId: Int,
        val authorization: String,
    )
}

object MissionRepositoryFactory {
    fun create(context: Context): MissionRepository =
        if (MissionConfig.USE_SERVER_MISSIONS) {
            NetworkMissionRepository(context)
        } else {
            LocalMissionRepository(context)
        }
}

object MissionEventReporter {
    fun recordFriendDiaryView(context: Context, friendId: Int, diaryDate: String) {
        MissionEventPrefs.markFriendDiaryViewed(context)
        if (MissionConfig.USE_SERVER_MISSIONS) {
            enqueue(
                context = context,
                eventType = MissionEventWorker.EVENT_FRIEND_DIARY,
                eventKey = diaryDate,
                friendId = friendId,
                uniqueKey = "friend:$friendId:$diaryDate",
            )
        }
    }

    fun recordWeeklySummaryView(context: Context, summaryStartDate: String) {
        MissionEventPrefs.markWeeklySummaryViewed(context, summaryStartDate)
        if (MissionConfig.USE_SERVER_MISSIONS) {
            enqueue(
                context = context,
                eventType = MissionEventWorker.EVENT_WEEKLY_SUMMARY,
                eventKey = summaryStartDate,
                uniqueKey = "summary:$summaryStartDate",
            )
        }
    }

    private fun enqueue(
        context: Context,
        eventType: String,
        eventKey: String,
        uniqueKey: String,
        friendId: Int = -1,
    ) {
        val userId = SessionManager.getUserId()
        if (userId < 0) return

        val request = OneTimeWorkRequestBuilder<MissionEventWorker>()
            .setInputData(
                workDataOf(
                    MissionEventWorker.KEY_EVENT_TYPE to eventType,
                    MissionEventWorker.KEY_EVENT_KEY to eventKey,
                    MissionEventWorker.KEY_FRIEND_ID to friendId,
                    MissionEventWorker.KEY_EXPECTED_USER_ID to userId,
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(InitialSyncWorker.accountWorkTag(userId))
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "mission_event:$userId:$uniqueKey",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
