package com.yadavar.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BirthdayBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val raw = context
            .getSharedPreferences("yadavar_data", Context.MODE_PRIVATE)
            .getString("birthdays", null) ?: return

        val list = raw.split("\n").mapNotNull {
            val p = it.split("\t", limit = 4)
            if (p.size != 4) {
                null
            } else {
                val id = p[0].toIntOrNull()
                val month = p[2].toIntOrNull()
                val day = p[3].toIntOrNull()

                if (
                    id == null ||
                    month == null ||
                    day == null ||
                    !isValidDate(month, day)
                ) {
                    null
                } else {
                    StoredBirthday(id, p[1], month, day)
                }
            }
        }

        BirthdayNotificationScheduler.scheduleAll(context, list)
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
