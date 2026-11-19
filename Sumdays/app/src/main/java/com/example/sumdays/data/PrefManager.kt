package com.example.sumdays.data

import android.content.Context
import com.example.sumdays.data.sync.PreferencesPayload
import com.example.sumdays.data.userInfo.prefs.ProfileImagePrefs
import androidx.core.content.edit
import com.example.sumdays.reminder.ReminderPrefs
import com.example.sumdays.settings.prefs.DiaryPermissionPrefs
import com.example.sumdays.settings.prefs.LabsPrefs

object PrefManager {
    fun resetLocalPrefs(context: Context) {
        // 로컬 전용 Pref만 기본값으로 초기화
        LabsPrefs.clear(context)
        ReminderPrefs(context).clear()
        DiaryPermissionPrefs.clear(context)
    }
    fun resetServerPrefs(context: Context) {
        // 서버 전용 Pref만 기본값으로 초기화
        val prefNames = listOf(
            null
        )

        prefNames.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit() {
                    clear()
                }
        }
    }
    fun initServerPrefs(context: Context, prefPayload : PreferencesPayload?) {
        // 서버 전용 pref - server에서 data 가져와서 초기화
        ProfileImagePrefs.init(context, prefPayload?.profile)
    }
}