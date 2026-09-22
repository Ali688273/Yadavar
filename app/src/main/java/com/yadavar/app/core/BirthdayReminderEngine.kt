package com.yadavar.app.core

import java.util.Calendar

data class BirthdayReminder(val name:String,val daysUntil:Int,val isToday:Boolean)

object BirthdayReminderEngine {
    fun reminder(name:String,month:Int,day:Int,today:Calendar=Calendar.getInstance()):BirthdayReminder {
        val now=Calendar.getInstance().apply {
            set(Calendar.YEAR,today.get(Calendar.YEAR));set(Calendar.MONTH,today.get(Calendar.MONTH));set(Calendar.DAY_OF_MONTH,today.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)
        }
        val birthday=Calendar.getInstance().apply {
            set(Calendar.YEAR,now.get(Calendar.YEAR));set(Calendar.MONTH,month-1);set(Calendar.DAY_OF_MONTH,day)
            set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)
        }
        if(birthday.before(now))birthday.add(Calendar.YEAR,1)
        val days=((birthday.timeInMillis-now.timeInMillis)/86400000L).toInt()
        return BirthdayReminder(name,days,days==0)
    }
}
