package com.example.sumdays.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val KEY_TEXT_REPLY = "memo_reply"
        const val NOTIFICATION_ID_BASE = 1000
    }

    override fun onReceive(context: Context, intent: Intent) {
        // 1. 알람 정보 추출
        val hour = intent.getIntExtra(ReminderScheduler.EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(ReminderScheduler.EXTRA_MINUTE, -1)
        val requestCode = intent.getIntExtra(ReminderScheduler.EXTRA_REQUEST_CODE, -1)

        if (hour == -1 || minute == -1 || requestCode == -1) {
            Log.e("Receiver", "알람 정보 추출 실패. 재등록을 건너뜁니다.")
            return
        }

        // 2. 알림 권한 확인
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        // 3. 알림 바디 입력 -> 앱 실행 및 알림 종료
        val activityIntent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 메모 추가 action (ReplyReceiver로 전달)
        val replyIntent = Intent(context, ReminderReplyReceiver::class.java).apply {
            // ReplyReceiver가 어떤 알람에서 왔는지 식별할 수 있도록 requestCode 전달
            putExtra(ReminderScheduler.EXTRA_REQUEST_CODE, requestCode)
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            context,    
            requestCode + 100,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // 메모 입력창
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("메모를 입력하세요")
            .build()

        // 메모 추가 액션 버튼
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit,
            "메모 추가",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()

        // 4. 알림 빌드 및 표시
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // R.drawable.ic_notification_icon 등으로 변경 권장
            .setContentTitle("Sumdays")
            .setContentText("오늘의 하루, 잠깐만 메모해볼까요?")
            .setContentIntent(pendingIntent) // 본문 클릭 → 앱 실행
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(replyAction)
            .setAutoCancel(true)

        // 알림 표시 (requestCode를 기반으로 고유 ID 할당)
        val notificationId = NOTIFICATION_ID_BASE + requestCode
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())

        Log.d("Receiver", "알림 표시 완료: $hour:$minute (ID: $notificationId)")


        // 5. 다음 날 알람 재등록
        ReminderScheduler.rescheduleNextDayReminder(context, hour, minute, requestCode)

        Log.d("Receiver", "다음 날 알람 재등록 요청 완료: $hour:$minute")
    }
}