package com.yadavar.app.core

import java.util.Calendar

data class BirthdayReminder(
    val name: String,
    val daysUntil: Int,
    val isToday: Boolean
)

object BirthdayReminderEngine {
    fun reminder(name: String, month: Int, day: Int, today: Calendar = Calendar.getInstance()): BirthdayReminder {
        if (!PersianCalendar.isValidJalaliDate(month, day)) return BirthdayReminder(name, 0, false)

        val todayJalali = PersianCalendar.toJalali(
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH) + 1,
            today.get(Calendar.DAY_OF_MONTH)
        )
        var targetYear = todayJalali.year
        var birthday = PersianCalendar.jalaliToGregorianCalendar(targetYear, month, day, 0, 0)
        val todayStart = Calendar.getInstance().apply {
            timeInMillis = today.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (birthday.timeInMillis < todayStart.timeInMillis) {
            targetYear++
            birthday = PersianCalendar.jalaliToGregorianCalendar(targetYear, month, day, 0, 0)
        }
        val days = ((birthday.timeInMillis - todayStart.timeInMillis) / 86400000L).toInt()
        return BirthdayReminder(name, days, days == 0)
    }
}
