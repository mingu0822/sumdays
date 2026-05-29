package com.example.sumdays.settings

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sumdays.R
import com.example.sumdays.databinding.ActivitySettingsNotificationBinding
import com.example.sumdays.reminder.ReminderPrefs
import com.example.sumdays.reminder.ReminderScheduler
import com.example.sumdays.theme.ThemePrefs
import com.example.sumdays.theme.ThemeRepository
import com.example.sumdays.utils.setupEdgeToEdge
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar

class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsNotificationBinding
    private lateinit var reminderPrefs: ReminderPrefs
    private lateinit var alarmAdapter: AlarmAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        reminderPrefs = ReminderPrefs(this)

        alarmAdapter = AlarmAdapter(
            onTimeClicked = { position, currentTime -> showTimePicker(position, currentTime) },
            onDeleteClicked = { position -> deleteAlarm(position) }
        )

        setupHeader()
        setupRecyclerView()
        setupMasterSwitch()
        setupListeners()
        applyThemeModeSettings()
        loadAlarmSettings()

        val rootView = findViewById<View>(R.id.setting_notification_root)
        setupEdgeToEdge(rootView)
    }

    private fun applyThemeModeSettings() {
        val themeKey = ThemePrefs.getTheme(this)
        val currentTheme = ThemeRepository.ownedThemes[themeKey] ?: return

        val primaryColor = ContextCompat.getColor(this, currentTheme.textPrimaryColor)
        val buttonColor = ContextCompat.getColor(this, currentTheme.buttonColor)
        val backgroundColor = currentTheme.backgroundColor
        val blockColor = currentTheme.blockColor

        // 전체 배경
        binding.root.setBackgroundResource(backgroundColor)

        // 헤더
        binding.header.headerTitle.setTextColor(primaryColor)
        binding.header.headerBackIcon.setColorFilter(primaryColor)

        // 알람 리스트 영역
        binding.alarmListContainer.setBackgroundResource(blockColor)
        binding.alarmTimeRecyclerView.setBackgroundResource(blockColor)

        // 추가 버튼
        binding.addAlarmButton.backgroundTintList = ColorStateList.valueOf(buttonColor)
        binding.addAlarmButton.setTextColor(
            ContextCompat.getColor(this, android.R.color.white)
        )

        // 마스터 스위치
        binding.masterSwitch.thumbTintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(
                ContextCompat.getColor(this, android.R.color.white),
                ContextCompat.getColor(this, android.R.color.white)
            )
        )

        binding.masterSwitch.trackTintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(
                buttonColor,
                ContextCompat.getColor(this, android.R.color.darker_gray)
            )
        )
    }

    private fun checkExactAlarmPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(
                    context,
                    "정확한 알람 설정을 위해 권한이 필요합니다.",
                    Toast.LENGTH_LONG
                ).show()

                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)

                return false
            }
        }
        return true
    }

    private fun setupHeader() {
        binding.header.headerTitle.text = "리마인더 설정"
        binding.header.headerBackIcon.setOnClickListener { finish() }
    }

    private fun setupMasterSwitch() {
        val isMasterOn = reminderPrefs.isMasterOn()
        binding.masterSwitch.isChecked = isMasterOn
        updateUiState(isMasterOn)

        binding.masterSwitch.setOnCheckedChangeListener { _, isChecked ->
            reminderPrefs.setMasterSwitch(isChecked)
            updateUiState(isChecked)

            if (isChecked) {
                if (checkExactAlarmPermission(this)) {
                    ReminderScheduler.scheduleAllReminders(this)
                    Toast.makeText(this, "리마인더가 활성화되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    binding.masterSwitch.isChecked = false
                    reminderPrefs.setMasterSwitch(false)
                    updateUiState(false)
                }
            } else {
                ReminderScheduler.cancelAllReminders(this)
                Toast.makeText(this, "리마인더가 비활성화되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUiState(isOn: Boolean) {
        val alpha = if (isOn) 1.0f else 0.5f

        binding.alarmListContainer.alpha = alpha
        binding.alarmListContainer.isEnabled = isOn

        alarmAdapter.isMasterOn = isOn
    }

    private fun setupRecyclerView() {
        alarmAdapter = AlarmAdapter(
            onTimeClicked = { position, currentTime -> showTimePicker(position, currentTime) },
            onDeleteClicked = { position -> deleteAlarm(position) }
        )

        binding.alarmTimeRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@NotificationSettingsActivity)
            adapter = alarmAdapter
        }
    }

    private fun loadAlarmSettings() {
        val times = reminderPrefs.getAlarmTimes()
        alarmAdapter.updateList(times)
        updateUiState(reminderPrefs.isMasterOn())
    }

    private fun setupListeners() {
        binding.addAlarmButton.setOnClickListener {
            if (alarmAdapter.itemCount >= 10) {
                Toast.makeText(this, "알림은 최대 10개까지 설정할 수 있습니다.", Toast.LENGTH_SHORT).show()
            } else {
                showTimePicker(null, null)
            }
        }
    }

    private fun showTimePicker(position: Int?, currentTime: String?) {
        val calendar = Calendar.getInstance()
        val initialHour =
            currentTime?.split(":")?.get(0)?.toIntOrNull() ?: calendar.get(Calendar.HOUR_OF_DAY)
        val initialMinute =
            currentTime?.split(":")?.get(1)?.toIntOrNull() ?: calendar.get(Calendar.MINUTE)

        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(initialHour)
            .setMinute(initialMinute)
            .setTitleText(if (position == null) "새 알림 시간 설정" else "알림 시간 수정")
            .build()

        picker.show(supportFragmentManager, "time_picker_tag")

        picker.addOnPositiveButtonClickListener {
            val newTime = String.format("%02d:%02d", picker.hour, picker.minute)
            saveOrUpdateAlarm(position, newTime)
        }
    }

    private fun saveOrUpdateAlarm(position: Int?, newTime: String) {
        val currentTimes = reminderPrefs.getAlarmTimes().toMutableList()

        if (currentTimes.contains(newTime)) {
            if (position == null) {
                Toast.makeText(this, "이미 $newTime 에 설정된 알람이 있습니다.", Toast.LENGTH_SHORT).show()
                return
            } else if (currentTimes[position] != newTime) {
                Toast.makeText(this, "이미 $newTime 에 설정된 알람이 있습니다.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (!checkExactAlarmPermission(this)) {
            return
        }

        if (position == null) {
            currentTimes.add(newTime)
            Toast.makeText(this, "$newTime 알림이 추가되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            currentTimes[position] = newTime
            Toast.makeText(this, "$newTime 으로 알림이 수정되었습니다.", Toast.LENGTH_SHORT).show()
        }

        reminderPrefs.setAlarmTimes(currentTimes)
        loadAlarmSettings()

        if (reminderPrefs.isMasterOn()) {
            ReminderScheduler.scheduleAllReminders(this)
        }
    }

    private fun deleteAlarm(position: Int) {
        val currentTimes = reminderPrefs.getAlarmTimes().toMutableList()
        val deletedTime = currentTimes.removeAt(position)

        reminderPrefs.setAlarmTimes(currentTimes)
        Toast.makeText(this, "$deletedTime 알림이 삭제되었습니다.", Toast.LENGTH_SHORT).show()

        loadAlarmSettings()

        if (reminderPrefs.isMasterOn()) {
            if (checkExactAlarmPermission(this)) {
                ReminderScheduler.scheduleAllReminders(this)
            }
        }
    }
}