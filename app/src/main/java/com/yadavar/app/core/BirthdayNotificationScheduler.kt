package com.yadavar.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object BirthdayNotificationScheduler {
    private const val BASE = 100000
    private const val MAX_REMINDERS = 8

    fun scheduleAll(context: Context, birthdays: List<StoredBirthday>) {
        val enabled = context.getSharedPreferences("yadavar_data", Context.MODE_PRIVATE)
            .getBoolean("birthday_notifications_enabled", true)
        birthdays.forEach {
            if (enabled) scheduleBirthday(context, it.id, it.name, it.month, it.day, it.reminderOffsets)
            else cancelBirthday(context, it.id, it.month, it.day)
        }
    }

    fun scheduleBirthday(context: Context, id: Int, name: String, month: Int, day: Int, reminderOffsets: String = "1,0") {
        if (!isValidDate(month, day)) return
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = Calendar.getInstance()
        cancelBirthdayAlarms(context, alarm, id)

        val birthday = Calendar.getInstance().apply {
            set(Calendar.YEAR, now.get(Calendar.YEAR))
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        normalizeLeapDay(birthday, month, day)
        if (birthday.timeInMillis <= System.currentTimeMillis()) {
            birthday.add(Calendar.YEAR, 1)
            normalizeLeapDay(birthday, month, day)
        }

        val offsets = reminderOffsets.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0..365 }
            .distinct()
            .sortedDescending()
            .take(MAX_REMINDERS)
            .ifEmpty { listOf(1, 0) }

        offsets.forEachIndexed { index, daysBefore ->
            val alarmTime = (birthday.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -daysBefore)
            }
            setAlarm(context, alarm, id, name, month, day, daysBefore, alarmTime.timeInMillis, BASE + id * MAX_REMINDERS + index)
        }
        cancelLegacyAlarms(context, alarm, month, day)
    }

    fun cancelBirthday(context: Context, id: Int, month: Int, day: Int) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelBirthdayAlarms(context, alarm, id)
        cancelLegacyAlarms(context, alarm, month, day)
    }

    private fun setAlarm(context: Context, alarm: AlarmManager, id: Int, name: String, month: Int, day: Int, daysBefore: Int, time: Long, code: Int) {
        val intent = Intent(context, BirthdayNotificationReceiver::class.java).apply {
            putExtra(BirthdayNotificationReceiver.EXTRA_ID, id)
            putExtra(BirthdayNotificationReceiver.EXTRA_NAME, name)
            putExtra(BirthdayNotificationReceiver.EXTRA_MONTH, month)
            putExtra(BirthdayNotificationReceiver.EXTRA_DAY, day)
            putExtra(BirthdayNotificationReceiver.EXTRA_TYPE, if (daysBefore == 0) BirthdayNotificationReceiver.TYPE_TODAY else BirthdayNotificationReceiver.TYPE_BEFORE)
            putExtra(BirthdayNotificationReceiver.EXTRA_DAYS_BEFORE, daysBefore)
        }
        val pendingIntent = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent)
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, time, pendingIntent)
        }
    }

    private fun cancelBirthdayAlarms(context: Context, alarm: AlarmManager, id: Int) {
        for (index in 0 until MAX_REMINDERS) cancel(context, alarm, BASE + id * MAX_REMINDERS + index)
    }

    private fun cancel(context: Context, alarm: AlarmManager, code: Int) {
        val pendingIntent = PendingIntent.getBroadcast(context, code, Intent(context, BirthdayNotificationReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.cancel(pendingIntent)
    }

    private fun cancelLegacyAlarms(context: Context, alarm: AlarmManager, month: Int, day: Int) {
        cancel(context, alarm, 100000 + month * 100 + day)
        cancel(context, alarm, 200000 + month * 100 + day)
    }

    private fun normalizeLeapDay(calendar: Calendar, month: Int, day: Int) {
        if (month == 2 && day == 29 && !isLeapYear(calendar.get(Calendar.YEAR))) {
            calendar.set(Calendar.MONTH, Calendar.MARCH)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

    private fun isValidDate(month: Int, day: Int): Boolean {
        if (month !in 1..12) return false
        val maxDay = when (month) { 2 -> 29; 4, 6, 9, 11 -> 30; else -> 31 }
        return day in 1..maxDay
    }
}

data class StoredBirthday(
    val id: Int,
    val name: String,
    val month: Int,
    val day: Int,
    val year: Int? = null,
    val reminderOffsets: String = "1,0"
)
