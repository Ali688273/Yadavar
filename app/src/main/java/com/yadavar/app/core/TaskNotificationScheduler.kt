package com.yadavar.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object TaskNotificationScheduler {
    private const val BASE = 300000

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
            "weekly" -> while (next.timeInMillis <= now.timeInMillis) next.add(Calendar.WEEK_OF_YEAR, 1)
            "monthly" -> while (next.timeInMillis <= now.timeInMillis) next.add(Calendar.MONTH, 1)
            "daily" -> if (next.timeInMillis <= now.timeInMillis) next.add(Calendar.DAY_OF_YEAR, 1)
            else -> if (next.timeInMillis <= now.timeInMillis) return
        }

        val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
            putExtra(TaskNotificationReceiver.EXTRA_ID, task.id)
            putExtra(TaskNotificationReceiver.EXTRA_TITLE, task.title)
            putExtra(TaskNotificationReceiver.EXTRA_REPEAT, task.repeat)
        }

        val pending = PendingIntent.getBroadcast(
            context, BASE + task.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
        }
    }

    fun cancelTask(context: Context, id: Int) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(
            context, BASE + id, Intent(context, TaskNotificationReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarm.cancel(pending)
    }
}
