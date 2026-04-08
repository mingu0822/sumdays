package com.example.sumdays.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.sumdays.daily.memo.Memo
import com.example.sumdays.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.threeten.bp.LocalDate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReminderReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        // 1. 입력된 텍스트 가져오기 및 저장
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(ReminderReceiver.KEY_TEXT_REPLY).toString()

        if (replyText.isNotEmpty()) {
            addMemo(context, replyText)
        }

        // 2. 알림 ID 계산하기
        val requestCode = intent.getIntExtra(ReminderScheduler.EXTRA_REQUEST_CODE, -1)

        if (requestCode != -1) {
            val notificationId = ReminderReceiver.NOTIFICATION_ID_BASE + requestCode

            // 3. 기존 알림 삭제
            NotificationManagerCompat.from(context).cancel(notificationId)

            Log.d("ReplyReceiver", "알림 삭제 완료 (ID: $notificationId)")
        } else {
            Log.e("ReplyReceiver", "RequestCode를 찾을 수 없어 알림을 삭제하지 못했습니다.")
        }
    }

    private fun addMemo(context: Context, memoText : String) {
        val db = AppDatabase.getDatabase(context)
        val memoDao = db.memoDao()
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Calendar.getInstance().time)
        val date = LocalDate.now().toString()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val count = memoDao.getMemoCountByDate(date)
                val newMemo = Memo(
                    id = 0,
                    content = memoText,
                    timestamp = currentTime,
                    date = date,
                    order = count,
                    type = "text"
                )
                memoDao.insert(newMemo)
                Log.d("ReminderReplyReceiver", "메모 추가 완료: ${newMemo.content}")

            } catch (e: Exception) {
                Log.e("ReminderReplyReceiver", "메모 추가 실패", e)
            }
        }
    }
}