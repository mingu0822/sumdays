package com.example.sumdays.mission

enum class MissionCategory(val title: String, val order: Int) {
    DAILY("일간 미션", 0),
    WEEKLY("주간 미션", 1),
    REWARD("보너스 미션", 2),
}

enum class MissionStatus {
    IN_PROGRESS,
    CLAIMABLE,
    CLAIMED,
}

data class MissionDefinition(
    val code: String,
    val category: MissionCategory,
    val title: String,
    val description: String,
    val targetCount: Int,
    val rewardPoints: Int,
    val displayOrder: Int,
)

data class Mission(
    val definition: MissionDefinition,
    val currentCount: Int,
    val status: MissionStatus,
)

data class AdRewardState(
    val watchedCount: Int,
    val dailyLimit: Int,
    val rewardPoints: Int,
) {
    val canWatch: Boolean
        get() = watchedCount < dailyLimit
}

sealed class AdRewardResult {
    data class Granted(
        val rewardPoints: Int,
        val watchedCount: Int,
        val dailyLimit: Int,
    ) : AdRewardResult()

    data object DailyLimitReached : AdRewardResult()
}

sealed class MissionListItem {
    data class Section(
        val category: MissionCategory,
    ) : MissionListItem()

    data class Card(
        val mission: Mission,
    ) : MissionListItem()
}
