package com.example.sumdays.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.os.Parcelable
import androidx.room.ColumnInfo
import kotlinx.parcelize.Parcelize

import android.content.Context
import com.example.sumdays.data.style.StylePrompt
import com.example.sumdays.data.style.loadStyleVector

// Room 데이터베이스 테이블을 정의하는 Entity 클래스
@Parcelize
@Entity(tableName = "memo_table")
data class Memo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "timestamp") val timestamp: String,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "memo_order") val order: Int,
    @ColumnInfo(name = "type", defaultValue = "text") val type: String = "text",
    @ColumnInfo(name = "isEdited", defaultValue = "0") val isEdited: Boolean = false,
    @ColumnInfo(name = "isDeleted", defaultValue = "0") val isDeleted: Boolean = false
) : Parcelable

@Parcelize
@Entity(tableName = "daily_entry")
data class DailyEntry(
    @PrimaryKey val date: String,          // 날짜 (yyyy-MM-dd)
    val diary: String?,                    // 일기 내용
    val keywords: String?,                 // 키워드 (;로 구분된 문자열)
    val aiComment: String?,                // AI 코멘트
    val emotionScore: Double?,             // 감정 점수
    val emotionIcon: String?,              // 감정 이모지
    val themeIcon: String?,                // 테마 이모지
    val photoUrls: String? = null,
    val isAllowed: Boolean? = false, // 외부 권한 허용 여부
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
): Parcelable

@Entity(tableName = "weekly_summary")
data class WeekSummaryEntity(
    @PrimaryKey val startDate: String, // 주간 데이터의 시작일 (고유키)
    val weekSummary: WeekSummary,      // WeekSummary 전체를 저장 (JSON 변환됨)
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false
)

data class EmojiData(
    val date : String,
    val diary: String?,
    val themeIcon : String?,
)


@Entity(tableName = "user_style")
data class UserStyle(
    @PrimaryKey(autoGenerate = true) val styleId: Long = 0, // PK: 각 스타일을 식별하는 고유 ID
    val styleName: String, // 사용자가 지정할 이름 (예: '시니컬 스타일', '경쾌한 스타일')
    val styleVector: List<Float>, // AI 스타일 벡터 (768차원)
    val styleExamples: List<String>, // 추출된 예시 문장들
    val stylePrompt: StylePrompt, // 상세 분석 결과 객체
    val sampleDiary: String = "", // 스타일 적용하여 생성한 샘플 일기
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false
) {
    companion object {
        fun createDefault1(context: Context): UserStyle {
            return UserStyle(
                styleId = 1L,
                styleName = "무색무취",
                styleVector = loadStyleVector(context, 1),
                styleExamples = listOf(
                    "점심때쯤 일어나서 밀린 빨래와 청소를 했다",
                    "오랜만에 땀을 흘리니 몸이 가벼워지는 기분이 들었다",
                    "일찍 자야 내일 아침에 피곤하지 않을 것 같다",
                    "주말이라 늦잠을 잤다"
                ),
                stylePrompt = StylePrompt(
                    character_concept = "일상적인 삶을 살아가는 평범한 사람. 소소한 일상을 관찰하고 기록하는 성향을 가진 인물.",
                    emotional_tone = "감정이 드러나지 않고 중립적인 톤으로, 일상적인 사건을 기록하는 데 집중한다.",
                    formality = "비격식적인 대화체로, 자연스러운 흐름을 유지하며 친근한 느낌을 준다.",
                    lexical_choice = "일상적인 단어와 표현을 사용하여 친근함을 느끼게 한다.",
                    pacing = "느긋하고 여유로운 흐름, 빠르지 않게 사건을 나열.",
                    punctuation_style = "기본적인 문장 부호 사용, 복잡한 구두점은 없다.",
                    sentence_endings = listOf("~었다.", "~했다.", "~었다고 생각했다."),
                    sentence_length = "중간 길이의 문장들이 많으며, 간결하게 표현되어 있다.",
                    sentence_structure = "주어-서술어 구조가 명확하며, 문장이 단순하고 직관적이다.",
                    special_syntax = "일상적인 표현을 그대로 사용하며, 특별한 구문은 없음.",
                    speech_quirks = "특별한 말투의 버릇은 없으며, 대화체적인 표현이 자연스럽다.",
                    tone = "담담하고 차분한 어조로 일상의 소소한 사건들을 서술."
                ),
                sampleDiary = "오늘 아침에 늦잠을 자서 밥을 굶었다. 조금 아쉬웠지만, 느긋하게 하루를 시작했다.  \n" +
                        "\n" +
                        "점심으로 라면을 먹었는데, 맛이 좋아서 기분이 좀 좋아졌다. 간단하지만 만족스러웠다.  \n" +
                        "\n" +
                        "저녁에는 삼겹살을 구워 먹었다. 하루가 평범하게 흘러갔다고 생각했다."
            )
        }

        fun createDefault2(context: Context): UserStyle {
            return UserStyle(
                styleId = 2L,
                styleName = "냥냥체",
                styleVector = loadStyleVector(context, 2),
                styleExamples = listOf(
                    "배가 푹신해서 밟아주는 거다냥",
                    "질척거리지 말고 저리 가서 돈이나 벌어와라냥",
                    "밥그릇 꼴이 이게 뭐냥? 집사는 눈치가 없는 거냥, 배가 안 고픈 거냥?",
                    "딱히 널 기다린 건 아니다냥"
                ),
                stylePrompt = StylePrompt(
                    character_concept = "도도하고 새침한 고양이 캐릭터로, 귀여움과 하대의 경계를 넘나드는 개성 있는 말투를 가진 존재.",
                    emotional_tone = "유머러스하면서도 약간의 경계심을 드러내는, 귀여운 동시에 도도한 느낌.",
                    formality = "비격식적이고 친근한 표현을 사용하며, 구어체가 주를 이룸.",
                    lexical_choice = "일상적인 언어를 사용하나, 고양이와 관련된 단어들이 빈번하게 등장함.",
                    pacing = "빠르게 진행되며, 마치 대화하듯이 리드미컬하게 이어짐.",
                    punctuation_style = "간결하고 명확하며, 느낌표와 물음표를 적절히 사용하여 감정을 표현함.",
                    sentence_endings = listOf("냥", "거냥", "다냥"),
                    sentence_length = "짧고 간결한 문장으로, 감정을 직접적으로 전달함.",
                    sentence_structure = "주어-서술어 형태가 기본이며, 질문형 문장이 자주 사용됨.",
                    special_syntax = "특별한 문법 구조는 없으나, 고양이의 감정을 드러내기 위해 의성어를 자주 사용함.",
                    speech_quirks = "'냥'이라는 의성어를 반복적으로 사용하여 고양이의 특성을 강조함.",
                    tone = "유머러스하고 경쾌하며, 약간의 조롱 섞인 어투."
                ),
                sampleDiary = "오늘 아침은 늦잠 자서 밥을 못 먹었거냥. 배가 고파서 살짝 찡그렸다냥.  \n" +
                        "\n" +
                        "점심엔 라면 먹었는데, 진짜 맛있었거냥! 국물도 후루룩, 기분 좋아졌다냥.  \n" +
                        "\n" +
                        "저녁엔 삼겹살 구워 먹었는데, 냄새만으로도 행복했거냥! 하루가 즐거웠다냥."
            )
        }

        fun createDefault3(context: Context): UserStyle {
            return UserStyle(
                styleId = 3L,
                styleName = "오덕체",
                styleVector = loadStyleVector(context, 3),
                styleExamples = listOf(
                    "(거친 숨소리) 이건 정말",
                    "국가적 보물급이라능!! (코피를 쓱 닦으며) 저장 완료했다는",
                    "(안경을 치켜올리며) 이런 곳에서 동지를 만나게 될 줄은 몰랐다능",
                    "에엣?! (동공지진) 그, 그건 설정 오류라능! 작가님이 그럴 리가 없다능!! (후다닥 도망간다)"
                ),
                stylePrompt = StylePrompt(
                    character_concept = "히키코모리 성격을 가진 캐릭터로, 사회적 상호작용을 피하고 고립된 삶을 사는 인물. 독특한 취향과 개성을 가지고 있으며, 자신만의 세계관을 가지고 있다.",
                    emotional_tone = "유머러스하고 경쾌한 톤을 유지하면서도, 특정 주제에 대해 강한 애정을 드러내는 경향이 있음.",
                    formality = "비격식적이고 친근한 말투. 대화체로, 가벼운 농담과 속어를 사용.",
                    lexical_choice = "일상적인 언어와 속어를 사용하며, 특정 주제에 대한 고유한 용어를 사용하는 경향이 있음. (예: '돈코츠', '갭모에')",
                    pacing = "빠른 템포로 진행되며, 감정의 변화를 강조하기 위해 호흡을 조절하는 경향이 있음.",
                    punctuation_style = "문장 끝에 감정이나 상황을 강조하기 위해 다양한 구두점을 사용. (예: ... , ! , ? )",
                    sentence_endings = listOf("~라능", "~니까", "~달까?"),
                    sentence_length = "짧고 간결한 문장이 많고, 때때로 감정의 격렬함에 따라 문장이 길어지기도 함.",
                    sentence_structure = "주어와 동사가 명확하게 드러나는 간단한 문장 구조. 감정이나 상황에 따라 문장이 유동적으로 변화함.",
                    special_syntax = "감정이나 상황을 강조하기 위해 괄호 안에 행동을 추가하거나, 대사 중간에 감정 표현을 삽입하는 방식.",
                    speech_quirks = "특정 단어 끝에 '능'을 붙이는 버릇이 있으며, 감정 표현을 위해 이모티콘이나 행동 묘사를 자주 사용.",
                    tone = "비꼬는 듯하면서도 유머러스한 어투. 캐릭터의 특성을 드러내는 경쾌하고 장난스러운 말투."
                ),
                sampleDiary = "아침에 늦잠 자서 밥을 못 먹었능... (허탈한 표정) 배고파 죽겠달까?  \n" +
                        "\n" +
                        "점심엔 라면 먹었는데, 진짜 맛있었능! (입맛 다시며) 그 맛에 또 빠져들었달까?  \n" +
                        "\n" +
                        "저녁엔 삼겹살 구워 먹었는데, 오늘 하루 정말 즐거웠다능! (웃으며) 고기 냄새가 코를 찌르더라능!"
            )
        }
    }
}





















































