package com.yadavar.app.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.yadavar.app.MainActivity
import com.yadavar.app.R

class TaskNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val repeat = intent.getStringExtra(EXTRA_REPEAT) ?: "none"
        val snoozed = intent.getBooleanExtra(EXTRA_SNOOZED, false)
        val action = intent.getStringExtra(EXTRA_ACTION)
        if (id < 0) return

        if (action == ACTION_SNOOZE) {
            TaskNotificationScheduler.scheduleSnooze(
                context = context,
                taskId = id,
                title = title,
                repeat = repeat,
                minutes = 10
            )
            return
        }

        val savedTask = findSavedTask(context, id)
        if (savedTask != null && savedTask.done) {
            if (!snoozed && repeat != "none") {
                TaskNotificationScheduler.scheduleTask(context, savedTask)
            }
            return
        }

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "یادآوری کارها",
                NotificationManager.IMPORTANCE_HIGH
            )
        )

        val open = PendingIntent.getActivity(
            context,
            12000 + id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, TaskNotificationReceiver::class.java).apply {
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_REPEAT, repeat)
            putExtra(EXTRA_ACTION, ACTION_SNOOZE)
        }

        val snoozePending = PendingIntent.getBroadcast(
            context,
            13000 + id,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (snoozed) "یادآوری دوباره" else "یادآوری کار")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(
                0,
                "۱۰ دقیقه بعد",
                snoozePending
            )

        manager.notify(12000 + id, builder.build())

        if (!snoozed && repeat != "none" && savedTask != null) {
            TaskNotificationScheduler.scheduleTask(context, savedTask)
        }
    }

    private fun findSavedTask(
        context: Context,
        id: Int
    ): com.yadavar.app.TodoItem? {
        val raw = context
            .getSharedPreferences("yadavar_data", 0)
            .getString("tasks", null)
            ?: return null

        return raw.split("
").asSequence().mapNotNull { row ->
            val x = row.split("	", limit = 6)
            if (x.size < 3) return@mapNotNull null

            val taskId = x[0].toIntOrNull() ?: return@mapNotNull null
            if (taskId != id) return@mapNotNull null

            val hour = x.getOrNull(3)?.toIntOrNull()
            val minute = x.getOrNull(4)?.toIntOrNull()
            val taskRepeat = x.getOrNull(5) ?: "none"

            com.yadavar.app.TodoItem(
                id = taskId,
                title = x[2],
                done = x[1] == "1",
                reminderHour = hour?.takeIf { it in 0..23 },
                reminderMinute = minute?.takeIf { it in 0..59 },
                repeat = taskRepeat,
                category = x.getOrNull(6)?.ifBlank { "عمومی" } ?: "عمومی",
                priority = x.getOrNull(7)?.takeIf { it == "low" || it == "normal" || it == "high" } ?: "normal",
                startDate = x.getOrNull(8).orEmpty(),
                dueDate = x.getOrNull(9).orEmpty(),
                note = x.getOrNull(10).orEmpty(),
                tags = x.getOrNull(11).orEmpty(),
                subtasks = x.getOrNull(12).orEmpty(),
                location = x.getOrNull(13).orEmpty(),
                customEvery = x.getOrNull(14)?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                customUnit = x.getOrNull(15).orEmpty().ifBlank { "day" }
            )
        }.firstOrNull()
    }

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val EXTRA_ID = "task_id"
        const val EXTRA_TITLE = "task_title"
        const val EXTRA_REPEAT = "task_repeat"
        const val EXTRA_SNOOZED = "task_snoozed"
        const val EXTRA_ACTION = "task_action"
        const val ACTION_SNOOZE = "snooze"
    }
}
