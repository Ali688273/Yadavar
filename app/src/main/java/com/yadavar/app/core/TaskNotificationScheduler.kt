package com.yadavar.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object TaskNotificationScheduler {
    private const val BASE = 300000
    private const val SNOOZE_BASE = 400000
    private const val MAX_REMINDERS = 8

    fun scheduleAll(context: Context, tasks: List<com.yadavar.app.TodoItem>) {
        val enabled = context.getSharedPreferences("yadavar_data", Context.MODE_PRIVATE).getBoolean("task_notifications_enabled", true)
        if (!enabled) {
            tasks.forEach { cancelTask(context, it.id) }
            return
        }
        tasks.forEach { scheduleTask(context, it) }
    }

    fun scheduleTask(context: Context, task: com.yadavar.app.TodoItem) {
        cancelTask(context, task.id)

        val reminders = if (task.reminders.isNotEmpty()) {
            task.reminders.take(MAX_REMINDERS)
        } else if (task.hasReminder) {
            listOf(TaskReminder(task.reminderHour ?: return, task.reminderMinute ?: return))
        } else {
            emptyList()
        }
        if (reminders.isEmpty()) return

        reminders.forEachIndexed { index, reminder ->
            val next = nextTrigger(task, reminder.hour, reminder.minute) ?: return@forEachIndexed
            setAlarm(
                context = context,
                taskId = task.id,
                title = task.title,
                repeat = task.repeat,
                triggerAt = next,
                requestCode = BASE + task.id * MAX_REMINDERS + index,
                snoozed = false,
                reminderIndex = index
            )
        }
    }

    private fun nextTrigger(task: com.yadavar.app.TodoItem, hour: Int, minute: Int): Long? {
        if (hour !in 0..23 || minute !in 0..59) return null
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
            "yearly" -> {
                val base = runCatching { LocalDate.parse(task.dueDate, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() ?: return null
                next.set(Calendar.MONTH, base.monthValue - 1)
                next.set(Calendar.DAY_OF_MONTH, base.dayOfMonth)
                while (next.timeInMillis <= now.timeInMillis) next.add(Calendar.YEAR, 1)
            }
            "custom" -> {
                val base = runCatching { LocalDate.parse(task.dueDate, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() ?: return null
                next.set(Calendar.YEAR, base.year)
                next.set(Calendar.MONTH, base.monthValue - 1)
                next.set(Calendar.DAY_OF_MONTH, base.dayOfMonth)
                val every = task.customEvery.coerceAtLeast(1)
                while (next.timeInMillis <= now.timeInMillis) {
                    when (task.customUnit.lowercase()) {
                        "week", "هفته", "هفتگی" -> next.add(Calendar.WEEK_OF_YEAR, every)
                        "month", "ماه", "ماهانه" -> next.add(Calendar.MONTH, every)
                        else -> next.add(Calendar.DAY_OF_YEAR, every)
                    }
                }
            }
            "daily" -> if (next.timeInMillis <= now.timeInMillis) next.add(Calendar.DAY_OF_YEAR, 1)
            "weekdays" -> {
                while (next.timeInMillis <= now.timeInMillis ||
                    next.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                    next.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
                    next.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            "weekends" -> {
                while (next.timeInMillis <= now.timeInMillis ||
                    (next.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY &&
                     next.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY)) {
                    next.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            else -> if (next.timeInMillis <= now.timeInMillis) return null
        }
        return next.timeInMillis
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
        repeat(MAX_REMINDERS) { index ->
            cancelAlarm(context, alarm, BASE + id * MAX_REMINDERS + index)
        }
        cancelAlarm(context, alarm, SNOOZE_BASE + id)
    }

    private fun setAlarm(
        context: Context,
        taskId: Int,
        title: String,
        repeat: String,
        triggerAt: Long,
        requestCode: Int,
        snoozed: Boolean,
        reminderIndex: Int = 0
    ) {
        val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
            putExtra(TaskNotificationReceiver.EXTRA_ID, taskId)
            putExtra(TaskNotificationReceiver.EXTRA_TITLE, title)
            putExtra(TaskNotificationReceiver.EXTRA_REPEAT, repeat)
            putExtra(TaskNotificationReceiver.EXTRA_SNOOZED, snoozed)
            putExtra(TaskNotificationReceiver.EXTRA_REMINDER_INDEX, reminderIndex)
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
