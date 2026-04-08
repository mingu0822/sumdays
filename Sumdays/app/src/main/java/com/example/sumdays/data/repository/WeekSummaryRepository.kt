package com.example.sumdays.data.repository

import com.example.sumdays.data.dao.WeekSummaryDao
import com.example.sumdays.statistics.WeekSummary

class WeekSummaryRepository (
    private val dao: WeekSummaryDao
) {
    // 주간 통계 데이터를 데이터베이스에 저장하거나 업데이트합
    suspend fun upsertWeekSummary(summary: WeekSummary) {
        dao.upsert(summary)
    }


     //  특정 시작 날짜에 해당하는 주간 통계 데이터 가져오기

    suspend fun getWeekSummary(startDate: String): WeekSummary? {
        return dao.getWeekSummary(startDate)
    }


     // 주간 통계 데이터를 삭제(소프트 삭제)

    suspend fun deleteWeekSummary(startDate: String) {
        dao.deleteWeekSummary(startDate)
    }


    // ㄴ통계 화면 초기 설정을 위해 저장된 모든 유효한 주간의 시작 날짜 목록을 오름차순으로 가져오기
    suspend fun getAllWrittenDatesAsc(): List<String> {
        return dao.getAllDatesAsc()
    }

}