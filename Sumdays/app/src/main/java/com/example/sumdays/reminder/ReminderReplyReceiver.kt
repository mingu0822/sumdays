package com.example.sumdays.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.sumdays.data.AppDatabase
import com.example.sumdays.data.Memo
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
            ?.getCharSequence(ReminderReceiver.KEY_TEXT_REPLY)
            ?.toString()
            .orEmpty()
            .trim()

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

        // 4. DB 저장 (비동기)
        if (replyText.isEmpty()) return

        val pendingResult = goAsync()
        addMemo(context, replyText) {
            pendingResult.finish()
        }
    }

    private fun addMemo(context: Context, memoText: String, onComplete: () -> Unit) {
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
                val newId = memoDao.insert(newMemo).toInt()
                Log.d("ReminderReplyReceiver", "메모 추가 완료: ${newMemo.content}")

                // 저장 확인 알림 (취소 버튼 포함)
                showSavedConfirmation(context, newId, memoText)

            } catch (e: Exception) {
                Log.e("ReminderReplyReceiver", "메모 추가 실패", e)
            } finally {
                onComplete()
            }
        }
    }

    /**
     * 메모 저장 후 "저장되었어요" 확인 알림을 띄웁니다.
     * - 같은 알림이 아닌 별도 ID(메모 id 기반)라 리마인더 알림과 충돌하지 않습니다.
     * - "취소" 액션으로 방금 저장한 메모를 되돌릴 수 있습니다.
     * - 몇 초 뒤 자동으로 사라집니다(setTimeoutAfter).
     */
    private fun showSavedConfirmation(context: Context, memoId: Int, memoText: String) {
        // Android 13+ 알림 권한이 없으면 표시 생략
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val confirmId = CONFIRM_NOTIFICATION_ID_BASE + memoId

        // 취소(undo) 액션 -> ReminderUndoReceiver 로 메모 id 전달
        val undoIntent = Intent(context, ReminderUndoReceiver::class.java).apply {
            putExtra(ReminderUndoReceiver.EXTRA_MEMO_ID, memoId)
            putExtra(ReminderUndoReceiver.EXTRA_CONFIRM_ID, confirmId)
        }
        val undoPendingIntent = PendingIntent.getBroadcast(
            context,
            confirmId,
            undoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val undoAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "취소",
            undoPendingIntent
        ).build()

        val notification = NotificationCompat.Builder(context, ReminderReceiver.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("메모가 저장되었어요")
            .setContentText(memoText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(undoAction)
            .setAutoCancel(true)
            .setTimeoutAfter(CONFIRM_TIMEOUT_MS)
            .build()

        NotificationManagerCompat.from(context).notify(confirmId, notification)
    }

    companion object {
        // 리마인더 알림 ID(1000번대)와 겹치지 않도록 2000 + memoId 사용
        private const val CONFIRM_NOTIFICATION_ID_BASE = 2000
        private const val CONFIRM_TIMEOUT_MS = 8000L
    }
}