import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object StreakPrefs {
    private const val PREF = "streak_prefs"
    private const val KEY_LAST_DATE = "last_diary_date"   // yyyy-MM-dd
    private const val KEY_STREAK = "current_streak"
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    fun getStreak(context: Context): Int {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return sp.getInt(KEY_STREAK, 0)
    }

    // ✅ 오늘 일기 저장 완료 후 호출 (오늘일 때만 반영)
    fun onDiarySaved(context: Context, entryDateStr: String) {
        val entryDate = runCatching { LocalDate.parse(entryDateStr, fmt) }.getOrNull() ?: return
        val today = LocalDate.now()
        if (entryDate != today) return

        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val lastStr = sp.getString(KEY_LAST_DATE, null)
        val oldStreak = sp.getInt(KEY_STREAK, 0)

        val newStreak = if (lastStr == null) {
            1
        } else {
            val lastDate = runCatching { LocalDate.parse(lastStr, fmt) }.getOrNull()
            if (lastDate == null) 1
            else {
                val diff = ChronoUnit.DAYS.between(lastDate, today).toInt()
                when (diff) {
                    0 -> oldStreak.coerceAtLeast(1)       // 오늘 이미 반영됨
                    1 -> oldStreak.coerceAtLeast(0) + 1   // 어제 작성 → +1
                    else -> 1                              // (원칙상 여기까지 올 일 거의 없음)
                }
            }
        }

        sp.edit()
            .putInt(KEY_STREAK, newStreak)
            .putString(KEY_LAST_DATE, today.toString())
            .apply()
    }

    /**
     * ✅ 통계 화면(또는 앱 시작)에서 호출:
     * - 마지막 작성일이 "오늘"이면 유지
     * - 마지막 작성일이 "어제"이면 유지 (아직 오늘 안 썼지만 streak는 살아있다고 볼지 정책 선택)
     * - 마지막 작성일이 "그 이전"이면 streak=0으로 리셋
     */
    fun refreshOnOpen(context: Context) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val lastStr = sp.getString(KEY_LAST_DATE, null) ?: return

        val lastDate = runCatching { LocalDate.parse(lastStr, fmt) }.getOrNull() ?: return
        val today = LocalDate.now()
        val diff = ChronoUnit.DAYS.between(lastDate, today).toInt()

        if (diff >= 2) {
            sp.edit().putInt(KEY_STREAK, 0).apply()
        }
    }
}