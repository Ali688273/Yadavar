package com.yadavar.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object TaskNotificationScheduler {
    private const val BASE = 300000
    private const val SNOOZE_BASE = 400000

    fun scheduleAll(context: Context, tasks: List<com.yadavar.app.TodoItem>) {
        tasks.forEach { scheduleTask(context, it) }
    }

    fun scheduleTask(context: Context, task: com.yadavar.app.TodoItem) {
        cancelTask(context, task.id)
        if (!task.hasReminder) return

        val hour = task.reminderHour ?: return
        val minute = task.reminderMinute ?: return
        if (hour !in 0..23 || minute !in 0..59) return

        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (task.repeat) {
            "weekly" -> {
                while (next.timeInMillis <= now.timeInMillis) {
                    next.add(Calendar.WEEK_OF_YEAR, 1)
                }
            }
            "monthly" -> {
                while (next.timeInMillis <= now.timeInMillis) {
                    next.add(Calendar.MONTH, 1)
                }
            }
            "daily" -> {
                if (next.timeInMillis <= now.timeInMillis) {
                    next.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            else -> {
                if (next.timeInMillis <= now.timeInMillis) return
            }
        }

        setAlarm(
            context = context,
            taskId = task.id,
            title = task.title,
            repeat = task.repeat,
            triggerAt = next.timeInMillis,
            requestCode = BASE + task.id,
            snoozed = false
        )
    }

    fun scheduleSnooze(
        context: Context,
        taskId: Int,
        title: String,
        repeat: String,
        minutes: Int = 10
    ) {
        if (taskId < 0 || title.isBlank()) return

        val triggerAt = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L

        setAlarm(
            context = context,
            taskId = taskId,
            title = title,
            repeat = repeat,
            triggerAt = triggerAt,
            requestCode = SNOOZE_BASE + taskId,
            snoozed = true
        )
    }

    fun cancelTask(context: Context, id: Int) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAlarm(context, alarm, BASE + id)
        cancelAlarm(context, alarm, SNOOZE_BASE + id)
    }

    private fun setAlarm(
        context: Context,
        taskId: Int,
        title: String,
        repeat: String,
        triggerAt: Long,
        requestCode: Int,
        snoozed: Boolean
    ) {
        val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
            putExtra(TaskNotificationReceiver.EXTRA_ID, taskId)
            putExtra(TaskNotificationReceiver.EXTRA_TITLE, title)
            putExtra(TaskNotificationReceiver.EXTRA_REPEAT, repeat)
            putExtra(TaskNotificationReceiver.EXTRA_SNOOZED, snoozed)
        }

        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pending
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pending
            )
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun cancelAlarm(
        context: Context,
        alarm: AlarmManager,
        requestCode: Int
    ) {
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, TaskNotificationReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarm.cancel(pending)
    }
}
