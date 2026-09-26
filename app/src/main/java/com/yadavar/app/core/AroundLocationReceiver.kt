package com.yadavar.app.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.yadavar.app.TodoItem

class AroundLocationReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event=GeofencingEvent.fromIntent(intent) ?: return
        if(event.hasError()) return
        if(event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER && event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_DWELL) return
        val id=event.triggeringGeofences?.firstOrNull()?.requestId?.toIntOrNull() ?: return
        val task=loadTasks(context).firstOrNull{it.id==id} ?: return
        val channel="location_reminders"
        val nm=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(channel,"یادآوری مکانی",NotificationManager.IMPORTANCE_HIGH))
        if(android.os.Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED){
            nm.notify(70000+id,NotificationCompat.Builder(context,channel).setSmallIcon(android.R.drawable.ic_dialog_map).setContentTitle("یادآوری مکانی").setContentText(task.title).setAutoCancel(true).build())
        }
    }
    private fun loadTasks(context: Context): List<TodoItem> {
        val raw=context.getSharedPreferences("yadavar_data",0).getString("tasks",null) ?: return emptyList()
        return raw.split("\n").mapNotNull{r->
            val x=r.split("\t",limit=17)
            if(x.size<3) null else TodoItem(x[0].toIntOrNull()?:return@mapNotNull null,x[2],x.getOrNull(1)=="1")
        }
    }
}
