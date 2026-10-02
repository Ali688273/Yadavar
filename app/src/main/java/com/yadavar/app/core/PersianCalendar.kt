package com.yadavar.app.core

import java.util.Calendar
import java.util.GregorianCalendar

data class JalaliDate(val year: Int, val month: Int, val day: Int)

object PersianCalendar {
    fun toJalali(year: Int, month: Int, day: Int): JalaliDate =
        toJalali(year, month, day, true)

    private fun toJalali(gy: Int, gm: Int, gd: Int, _: Boolean): JalaliDate {
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
        val maxDay = if (month <= 6) 31 else if (month <= 11) 30 else 30
        return day in 1..maxDay
    }

    fun jalaliToGregorianCalendar(year: Int, month: Int, day: Int, hour: Int, minute: Int): Calendar {
        require(isValidJalaliDate(month, day))
        val gy = year + 621
        val gregorian = jalaliToGregorian(gy, month, day)
        return GregorianCalendar(gregorian.first, gregorian.second - 1, gregorian.third, hour, minute, 0).apply {
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        var jy2 = jy
        var gy = jy2 + 621
        val days = if (jm <= 6) (jm - 1) * 31 + (jd - 1)
                   else 186 + (jm - 7) * 30 + (jd - 1)
        val base = jalaliToDayNumber(jy2, 1, 1) + days
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
        var j = jdn + 32044
        val g = j / 146097
        var dg = j % 146097
        val c = (dg / 36524 + 1) * 3 / 4
        dg -= c * 36524
        val y = g * 400 + dg / 1461
        dg %= 1461
        val y2 = y + (dg / 365)
        val doy = dg % 365
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = mp + 3 - 12 * (mp / 10)
        val year = y2 + mp / 10
        return Triple(year.toInt(), m.toInt(), d.toInt())
    }

    private fun mod(a: Int, b: Int): Int {
        val r = a % b
        return if (r < 0) r + b else r
    }
}
