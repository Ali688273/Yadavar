package com.yadavar.app.core

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import org.json.JSONArray
import org.json.JSONObject

object GeofenceManager {
    private const val PREFS = "yadavar_geofences"
    private const val KEY = "items"
    private const val ACTION = "com.yadavar.app.GEOFENCE_EVENT"

    data class Item(
        val taskId: Int,
        val title: String,
        val latitude: Double,
        val longitude: Double,
        val radius: Float
    )

    fun save(context: Context, item: Item) {
        val list = load(context).filterNot { it.taskId == item.taskId }.toMutableList()
        list += item
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("taskId",it.taskId).put("title",it.title).put("lat",it.latitude).put("lon",it.longitude).put("radius",it.radius)) }
        context.getSharedPreferences(PREFS,0).edit().putString(KEY,a.toString()).apply()
    }

    fun load(context: Context): List<Item> = runCatching {
        val a = JSONArray(context.getSharedPreferences(PREFS,0).getString(KEY,"[]"))
        buildList {
            for (i in 0 until a.length()) {
                val o=a.getJSONObject(i)
                add(Item(o.getInt("taskId"),o.optString("title"),o.getDouble("lat"),o.getDouble("lon"),o.optDouble("radius",150.0).toFloat()))
            }
        }
    }.getOrDefault(emptyList())

    fun remove(context: Context, taskId: Int) {
        val list=load(context).filterNot{it.taskId==taskId}
        val a=JSONArray(); list.forEach{a.put(JSONObject().put("taskId",it.taskId).put("title",it.title).put("lat",it.latitude).put("lon",it.longitude).put("radius",it.radius))}
        context.getSharedPreferences(PREFS,0).edit().putString(KEY,a.toString()).apply()
        if (hasLocationPermission(context)) LocationServices.getGeofencingClient(context).removeGeofences(pendingIntent(context))
    }

    fun registerAll(context: Context) {
        if (!hasLocationPermission(context)) return
        val client=LocationServices.getGeofencingClient(context)
        val fences=load(context).take(100).map { item ->
            Geofence.Builder().setRequestId(item.taskId.toString())
                .setCircularRegion(item.latitude,item.longitude,item.radius.coerceIn(50f,10000f))
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(120000)
                .build()
        }
        if(fences.isEmpty()) return
        val request=GeofencingRequest.Builder().setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER).addGeofences(fences).build()
        client.removeGeofences(pendingIntent(context)).addOnCompleteListener {
            client.addGeofences(request,pendingIntent(context))
        }
    }

    fun add(context: Context, item: Item) {
        save(context,item)
        registerAll(context)
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent=Intent(context,AroundLocationReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(context, 8100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
}
