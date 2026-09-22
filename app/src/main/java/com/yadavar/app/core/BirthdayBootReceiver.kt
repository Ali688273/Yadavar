package com.yadavar.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BirthdayBootReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action!=Intent.ACTION_BOOT_COMPLETED)return
        val raw=context.getSharedPreferences("yadavar_data",Context.MODE_PRIVATE).getString("birthdays",null)?:return
        val list=raw.split("\n").mapNotNull{
            val p=it.split("\t",limit=4);if(p.size!=4)null else{
                val id=p[0].toIntOrNull();val m=p[2].toIntOrNull();val d=p[3].toIntOrNull()
                if(id==null||m==null||d==null||m !in 1..12||d !in 1..31)null else StoredBirthday(id,p[1],m,d)
            }
        }
        BirthdayNotificationScheduler.scheduleAll(context,list)
    }
}
