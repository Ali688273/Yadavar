package com.yadavar.app.core

import java.util.Calendar
import java.util.GregorianCalendar

data class JalaliDate(val year: Int, val month: Int, val day: Int)

object PersianCalendar {
    fun toJalali(year: Int, month: Int, day: Int): JalaliDate {
        val gy = year - 1600
        val gm = month - 1
        val gd = day - 1

        val gDayNo = 365 * gy +
            (gy + 3) / 4 -
            (gy + 99) / 100 +
            (gy + 399) / 400

        val gMonthDays = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var dayNo = gDayNo + gd
        for (i in 0 until gm) dayNo += gMonthDays[i]
        if (gm > 1 && ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0)) {
            dayNo++
        }

        var jDayNo = dayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461
        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        val jm: Int
        val jd: Int
        if (jDayNo < 186) {
            jm = 1 + jDayNo / 31
            jd = 1 + jDayNo % 31
        } else {
            jm = 7 + (jDayNo - 186) / 30
            jd = 1 + (jDayNo - 186) % 30
        }
        return JalaliDate(jy, jm, jd)
    }

    fun formatJalali(date: java.time.LocalDate): String {
        val j = toJalali(date.year, date.monthValue, date.dayOfMonth)
        return "%04d/%02d/%02d".format(j.year, j.month, j.day)
    }

    fun formatJalaliMonthDay(month: Int, day: Int): String =
        "%02d/%02d".format(day, month)

    fun isValidJalaliDate(month: Int, day: Int): Boolean {
        if (month !in 1..12) return false
        val maxDay = if (month <= 6) 31 else 30
        return day in 1..maxDay
    }

    fun jalaliToGregorianCalendar(year: Int, month: Int, day: Int, hour: Int, minute: Int): Calendar {
        require(isValidJalaliDate(month, day))
        val gregorian = jalaliToGregorian(year, month, day)
        return GregorianCalendar(gregorian.first, gregorian.second - 1, gregorian.third, hour, minute, 0).apply {
            set(Calendar.MILLISECOND, 0)
        }
    }

    fun jalaliToLocalDate(year: Int, month: Int, day: Int): java.time.LocalDate {
        val g = jalaliToGregorianCalendar(year, month, day, 0, 0)
        return java.time.LocalDate.of(
            g.get(Calendar.YEAR),
            g.get(Calendar.MONTH) + 1,
            g.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        var jyWork = jy - 979
        val jDayNo = 365 * jyWork +
            (jyWork / 33) * 8 +
            ((jyWork % 33) + 3) / 4

        var dayNo = jDayNo
        dayNo += if (jm <= 6) {
            (jm - 1) * 31 + (jd - 1)
        } else {
            186 + (jm - 7) * 30 + (jd - 1)
        }
        dayNo += 79

        var gy = 1600 + 400 * (dayNo / 146097)
        dayNo %= 146097

        var leap = true
        if (dayNo >= 36525) {
            dayNo--
            gy += 100 * (dayNo / 36524)
            dayNo %= 36524
            if (dayNo >= 365) dayNo++
            else leap = false
        }

        gy += 4 * (dayNo / 1461)
        dayNo %= 1461
        if (dayNo >= 366) {
            leap = false
            dayNo--
            gy += dayNo / 365
            dayNo %= 365
        }

        val monthDays = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        var remaining = dayNo
        while (gm < 12 && remaining >= monthDays[gm]) {
            remaining -= monthDays[gm]
            gm++
        }
        return Triple(gy, gm + 1, remaining + 1)
    }
}
