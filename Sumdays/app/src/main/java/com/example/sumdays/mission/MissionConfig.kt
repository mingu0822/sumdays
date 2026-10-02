package com.example.sumdays.mission

/**
 * 프런트 화면 개발용 임시 설정입니다.
 * 서버 연동 전까지 목표 횟수와 보상 포인트는 이 파일에서만 변경합니다.
 */
object MissionConfig {
    // 서버에 미션 API와 DB 마이그레이션을 배포한 뒤 true로 전환합니다.
    const val USE_SERVER_MISSIONS = true

    const val DAILY_MEMO_TARGET = 3
    const val DAILY_MEMO_REWARD = 100

    const val DAILY_DIARY_TARGET = 1
    const val DAILY_DIARY_REWARD = 200

    const val DAILY_FRIEND_DIARY_TARGET = 1
    const val DAILY_FRIEND_DIARY_REWARD = 50

    const val WEEKLY_STREAK_TARGET = 3
    const val WEEKLY_STREAK_REWARD = 300

    const val WEEKLY_DIARY_TARGET = 5
    const val WEEKLY_DIARY_REWARD = 500

    const val AD_REWARD = 200
    const val AD_DAILY_LIMIT = 3

    const val WEEKLY_SUMMARY_TARGET = 1
    const val WEEKLY_SUMMARY_REWARD = 300
}

object MissionCode {
    const val DAILY_WRITE_MEMO = "DAILY_WRITE_MEMO"
    const val DAILY_COMPLETE_DIARY = "DAILY_COMPLETE_DIARY"
    const val DAILY_VIEW_FRIEND_DIARY = "DAILY_VIEW_FRIEND_DIARY"
    const val WEEKLY_DIARY_STREAK = "WEEKLY_DIARY_STREAK"
    const val WEEKLY_WRITE_DIARY = "WEEKLY_WRITE_DIARY"
    const val WEEKLY_SUMMARY_REWARD = "WEEKLY_SUMMARY_REWARD"
}
