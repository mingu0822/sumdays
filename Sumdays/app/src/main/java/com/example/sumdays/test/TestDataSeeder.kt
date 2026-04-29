package com.example.sumdays.test

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.sumdays.data.WeekSummaryEntity
import com.example.sumdays.data.dao.WeekSummaryDao
import com.example.sumdays.statistics.EmotionAnalysis
import com.example.sumdays.statistics.Highlight
import com.example.sumdays.statistics.Insights
import com.example.sumdays.statistics.SummaryDetails
import com.example.sumdays.statistics.WeekSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object TestDataSeeder {

    private val overviews = listOf(
        "이번 주는 전반적으로 안정적인 한 주였습니다.",
        "다양한 감정이 교차했던 한 주였어요.",
        "조용하고 내면을 돌아보는 시간이 많았습니다.",
        "활기찬 에너지로 가득 찼던 한 주였습니다.",
        "새로운 도전과 변화가 있었던 한 주였어요."
    )

    private val titles = listOf(
        "평온한 한 주", "감정이 풍부했던 주", "성장의 시간",
        "바쁘고 알찼던 주", "내면을 탐색한 주", "새로운 시작",
        "도전과 배움", "휴식과 회복", "설레는 변화"
    )

    private val advices = listOf(
        "자신의 감정을 있는 그대로 받아들이는 연습을 해보세요.",
        "작은 성취에도 스스로를 칭찬해 주세요.",
        "충분한 휴식이 더 나은 내일을 만듭니다.",
        "가까운 사람들과 마음을 나눠보는 건 어떨까요?",
        "지금 이 순간을 충분히 즐기세요."
    )

    private val topics = listOf(
        listOf("일상", "감사", "성장"),
        listOf("여행", "음식", "친구"),
        listOf("독서", "운동", "휴식"),
        listOf("업무", "목표", "도전"),
        listOf("가족", "추억", "행복")
    )

    private val dominantEmojis = listOf("😊", "😌", "😢", "😄", "🤔", "😤", "🥰")

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun seed(dao: WeekSummaryDao, count: Int = 60) {
        val existing = dao.getAllDatesAsc()
        if (existing.isNotEmpty()) return  // 이미 데이터 있으면 skip

        // 오늘 기준으로 count주 전 월요일부터 시작
        val today = LocalDate.now()
        val latestMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val entities = (count - 1 downTo 0).map { weeksAgo ->
            val monday = latestMonday.minusWeeks(weeksAgo.toLong())
            val sunday = monday.plusDays(6)
            val i = count - 1 - weeksAgo  // 0-based index for template rotation

            val summary = WeekSummary(
                startDate  = monday.toString(),
                endDate    = sunday.toString(),
                diaryCount = (3..7).random(),
                emotionAnalysis = EmotionAnalysis(
                    distribution = mapOf(
                        "positive" to (1..5).random(),
                        "neutral"  to (1..3).random(),
                        "negative" to (0..2).random()
                    ),
                    dominantEmoji = dominantEmojis[i % dominantEmojis.size],
                    emotionScore  = (50..90).random() / 10.0,
                    trend = listOf("increasing", "stable", "decreasing").random()
                ),
                highlights = listOf(
                    Highlight(monday.plusDays(1).toString(), "즐거운 하루였습니다."),
                    Highlight(monday.plusDays(4).toString(), "새로운 것을 배웠어요.")
                ),
                insights = Insights(
                    advice       = advices[i % advices.size],
                    emotionCycle = "이번 주는 긍정적인 감정이 주를 이뤘습니다."
                ),
                summary = SummaryDetails(
                    emergingTopics = topics[i % topics.size],
                    overview       = overviews[i % overviews.size],
                    title          = titles[i % titles.size]
                )
            )

            WeekSummaryEntity(
                startDate   = monday.toString(),
                weekSummary = summary,
                isEdited    = false,
                isDeleted   = false
            )
        }

        dao.insertAll(entities)
    }
}
