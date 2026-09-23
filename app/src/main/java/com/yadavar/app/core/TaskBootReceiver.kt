package com.yadavar.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TaskBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val raw = context.getSharedPreferences("yadavar_data", Context.MODE_PRIVATE)
            .getString("tasks", null) ?: return

        val tasks = raw.split("\n").mapNotNull { row ->
            val x = row.split("\t", limit = 8)
            if (x.size < 3) null else {
                val id = x[0].toIntOrNull()
                val hour = x.getOrNull(3)?.toIntOrNull()
                val minute = x.getOrNull(4)?.toIntOrNull()
                val repeat = x.getOrNull(5) ?: "none"
                if (id == null) null else com.yadavar.app.TodoItem(
                    id, x[2], x[1] == "1",
                    hour?.takeIf { it in 0..23 },
                    minute?.takeIf { it in 0..59 },
                    repeat,
                    x.getOrNull(6)?.ifBlank { "عمومی" } ?: "عمومی",
                    x.getOrNull(7)?.takeIf { it == "low" || it == "normal" || it == "high" } ?: "normal"
                )
            }
        }

        TaskNotificationScheduler.scheduleAll(context, tasks)
    }
}
