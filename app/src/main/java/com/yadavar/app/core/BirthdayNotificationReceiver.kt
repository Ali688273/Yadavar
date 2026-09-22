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

class BirthdayNotificationReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val name=intent.getStringExtra(EXTRA_NAME)?:return
        val month=intent.getIntExtra(EXTRA_MONTH,0);val day=intent.getIntExtra(EXTRA_DAY,0)
        val type=intent.getIntExtra(EXTRA_TYPE,TYPE_TODAY)
        val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID,"یادآوری تولدها",NotificationManager.IMPORTANCE_HIGH))
        val open=PendingIntent.getActivity(context,9000+month*100+day,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title=if(type==TYPE_TOMORROW)"تولد نزدیک است 🎂" else "امروز تولد است 🎉"
        val text=if(type==TYPE_TOMORROW)"فردا تولد "+name+" است." else "امروز تولد "+name+" است. تولدش مبارک!"
        manager.notify(9000+month*100+day+type,NotificationCompat.Builder(context,CHANNEL_ID).setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(text).setAutoCancel(true).setContentIntent(open).build())
        BirthdayNotificationScheduler.scheduleBirthday(context,name,month,day)
    }
    companion object{
        const val CHANNEL_ID="birthday_reminders";const val EXTRA_NAME="birthday_name";const val EXTRA_MONTH="birthday_month";const val EXTRA_DAY="birthday_day";const val EXTRA_TYPE="birthday_type"
        const val TYPE_TOMORROW=1;const val TYPE_TODAY=2
    }
}
