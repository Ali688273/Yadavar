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
        if (id < 0) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "یادآوری کارها", NotificationManager.IMPORTANCE_HIGH)
        )

        val open = PendingIntent.getActivity(
            context, 12000 + id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        manager.notify(
            12000 + id,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("یادآوری کار")
                .setContentText(title)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        )

        if (repeat != "none") {
            val tasks = context.getSharedPreferences("yadavar_data", 0)
                .getString("tasks", null)
                ?.split("\n")
                ?.mapNotNull { row ->
                    val x = row.split("\t", limit = 6)
                    if (x.size < 3) null else {
                        val taskId = x[0].toIntOrNull()
                        val hour = x.getOrNull(3)?.toIntOrNull()
                        val minute = x.getOrNull(4)?.toIntOrNull()
                        val taskRepeat = x.getOrNull(5) ?: "none"
                        if (taskId == id && hour != null && minute != null) {
                            com.yadavar.app.TodoItem(taskId, x[2], x[1] == "1", hour, minute, taskRepeat)
                        } else null
                    }
                }?.firstOrNull()

            if (tasks != null) TaskNotificationScheduler.scheduleTask(context, tasks)
        }
    }

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val EXTRA_ID = "task_id"
        const val EXTRA_TITLE = "task_title"
        const val EXTRA_REPEAT = "task_repeat"
    }
}
