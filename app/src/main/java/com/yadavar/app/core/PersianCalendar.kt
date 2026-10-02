package com.yadavar.app.core

import java.util.Calendar
import java.util.GregorianCalendar

data class JalaliDate(val year: Int, val month: Int, val day: Int)

object PersianCalendar {
    fun toJalali(year: Int, month: Int, day: Int): JalaliDate =
        toJalaliInternal(year, month, day)

    private fun toJalaliInternal(gy: Int, gm: Int, gd: Int): JalaliDate {
        val gdm = intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334)
        var gy2 = gy
        if (gm > 2) gy2++
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
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
        if (day !in 1..maxDay) return false
        if (month < 12 || day < 30) return true
        val g = jalaliToGregorian(1400, month, day)
        val back = toJalaliInternal(g.first, g.second, g.third)
        return back.month == month && back.day == day
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
        return java.time.LocalDate.of(g.get(Calendar.YEAR), g.get(Calendar.MONTH) + 1, g.get(Calendar.DAY_OF_MONTH))
    }

    private fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        val days = if (jm <= 6) (jm - 1) * 31 + (jd - 1)
                   else 186 + (jm - 7) * 30 + (jd - 1)
        val base = jalaliToDayNumber(jy, 1, 1) + days
        return dayNumberToGregorian(base)
    }

    private fun jalaliToDayNumber(jy: Int, jm: Int, jd: Int): Long {
        val epBase = jy - (if (jy >= 0) 474 else 473)
        val epYear = 474 + mod(epBase, 2820)
        return (jd + if (jm <= 7) (jm - 1) * 31 else (jm - 1) * 30 + 6) +
            ((epYear * 682 - 110) / 2816).toLong() +
            (epYear - 1) * 365L +
            (epBase / 2820) * 1029983L +
            1948320L - 1
    }

    private fun dayNumberToGregorian(jdn: Long): Triple<Int, Int, Int> {
        val j = jdn + 32044
        val g = j / 146097
        var dg = j % 146097
        val c = (dg / 36524 + 1) * 3 / 4
        dg -= c * 36524
        val b = dg / 1461
        val db = dg % 1461
        val a = (db / 365).coerceAtMost(3)
        val year = 400 * g + 100 * c + 4 * b + a
        val doy = db - 365 * a
        val mp = (5 * doy + 2) / 153
        val day = doy - (153 * mp + 2) / 5 + 1
        val month = mp + 3 - 12 * (mp / 10)
        return Triple((year + mp / 10).toInt(), month.toInt(), day.toInt())
    }

    private fun mod(a: Int, b: Int): Int {
        val r = a % b
        return if (r < 0) r + b else r
    }
}
