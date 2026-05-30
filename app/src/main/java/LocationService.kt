package com.tonnom.runmesh

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tonnom.runmesh.data.AppDatabase
import com.tonnom.runmesh.data.LocationPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LocationService : Service() {
    private var locationManager: LocationManager? = null
    private lateinit var db: AppDatabase

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // 1. On crée notre point de donnée
            val point = LocationPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                timestamp = System.currentTimeMillis()
            )

            // 2. On l'affiche dans le terminal pour que tu puisses vérifier
            Log.d("RUNMESH_HACKER", "Point capté & sauvé : Lat=${point.latitude}, Alt=${point.altitude}")

            // 3. On l'écrit dans la base de données hors ligne en tâche de fond
            CoroutineScope(Dispatchers.IO).launch {
                db.locationDao().insertPoint(point)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // On initialise la base de données quand le service démarre
        db = AppDatabase.getDatabase(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START") startTracking()
        if (intent?.action == "STOP") stopTracking()
        return START_STICKY
    }

    private fun startTracking() {
        val channelId = "runmesh_channel"
        val notifManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(channelId, "Tracking", NotificationManager.IMPORTANCE_LOW)
        notifManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("RunMesh")
            .setContentText("Enregistrement hors ligne (Toutes les 5s)...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            // Demande au satellite un point TOUTES LES 5 SECONDES (5000 millisecondes)
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 0f, locationListener)
        } catch (e: SecurityException) {
            Log.e("RUNMESH_HACKER", "Erreur permission GPS")
        }
    }

    private fun stopTracking() {
        locationManager?.removeUpdates(locationListener)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}