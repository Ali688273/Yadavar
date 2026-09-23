package com.yadavar.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object BirthdayNotificationScheduler {
    private const val TOMORROW_BASE = 100000
    private const val TODAY_BASE = 200000

    fun scheduleAll(context: Context, birthdays: List<StoredBirthday>) {
        birthdays.forEach {
            scheduleBirthday(context, it.id, it.name, it.month, it.day)
        }
    }

    fun scheduleBirthday(
        context: Context,
        id: Int,
        name: String,
        month: Int,
        day: Int
    ) {
        if (!isValidDate(month, day)) return

        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = Calendar.getInstance()

        cancel(context, alarm, TOMORROW_BASE + id)
        cancel(context, alarm, TODAY_BASE + id)

        val birthday = Calendar.getInstance().apply {
            set(Calendar.YEAR, now.get(Calendar.YEAR))
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (birthday.timeInMillis <= System.currentTimeMillis()) {
            birthday.add(Calendar.YEAR, 1)
        }

        val tomorrow = (birthday.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }

        setAlarm(context, alarm, id, name, month, day,
            BirthdayNotificationReceiver.TYPE_TOMORROW,
            tomorrow.timeInMillis, TOMORROW_BASE + id)

        setAlarm(context, alarm, id, name, month, day,
            BirthdayNotificationReceiver.TYPE_TODAY,
            birthday.timeInMillis, TODAY_BASE + id)

        cancelLegacyAlarms(context, alarm, month, day)
    }

    fun cancelBirthday(context: Context, id: Int, month: Int, day: Int) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancel(context, alarm, TOMORROW_BASE + id)
        cancel(context, alarm, TODAY_BASE + id)
        cancelLegacyAlarms(context, alarm, month, day)
    }

    private fun setAlarm(
        context: Context,
        alarm: AlarmManager,
        id: Int,
        name: String,
        month: Int,
        day: Int,
        type: Int,
        time: Long,
        code: Int
    ) {
        val intent = Intent(context, BirthdayNotificationReceiver::class.java).apply {
            putExtra(BirthdayNotificationReceiver.EXTRA_ID, id)
            putExtra(BirthdayNotificationReceiver.EXTRA_NAME, name)
            putExtra(BirthdayNotificationReceiver.EXTRA_MONTH, month)
            putExtra(BirthdayNotificationReceiver.EXTRA_DAY, day)
            putExtra(BirthdayNotificationReceiver.EXTRA_TYPE, type)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent)
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, time, pendingIntent)
        }
    }

    private fun cancel(context: Context, alarm: AlarmManager, code: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, BirthdayNotificationReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarm.cancel(pendingIntent)
    }

    private fun cancelLegacyAlarms(
        context: Context,
        alarm: AlarmManager,
        month: Int,
        day: Int
    ) {
        cancel(context, alarm, TOMORROW_BASE + month * 100 + day)
        cancel(context, alarm, TODAY_BASE + month * 100 + day)
    }

    private fun isValidDate(month: Int, day: Int): Boolean {
        if (month !in 1..12) return false
        val maxDay = when (month) {
            2 -> 29
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return day in 1..maxDay
    }
}

data class StoredBirthday(
    val id: Int,
    val name: String,
    val month: Int,
    val day: Int
)
