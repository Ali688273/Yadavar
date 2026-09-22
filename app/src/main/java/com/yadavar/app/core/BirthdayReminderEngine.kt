package com.yadavar.app.core
import java.time.LocalDate
import java.time.MonthDay
data class BirthdayReminder(val name:String,val daysUntil:Int,val isToday:Boolean)
object BirthdayReminderEngine{
 fun reminder(name:String,month:Int,day:Int,today:LocalDate=LocalDate.now()):BirthdayReminder{
  val b=MonthDay.of(month,day);var y=today.year;var n=b.atYear(y);if(n.isBefore(today))n=b.atYear(++y)
  val d=java.time.temporal.ChronoUnit.DAYS.between(today,n).toInt();return BirthdayReminder(name,d,d==0)
 }
}