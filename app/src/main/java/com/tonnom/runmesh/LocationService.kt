package com.tonnom.runmesh

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tonnom.runmesh.data.AppDatabase
import com.tonnom.runmesh.data.LocationPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocationService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var db: AppDatabase
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Initialisation de la base de données
        db = AppDatabase.getDatabase(applicationContext)
        // Initialisation du GPS 100% Natif (sans Google Services)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    @SuppressLint("MissingPermission") // Les permissions sont gérées dans MainActivity
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        
        // 1. Démarrer le service au premier plan avec une notification (Obligatoire pour le GPS en fond)
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "LOCATION_CHANNEL")
            .setContentTitle("RunMesh")
            .setContentText("Enregistrement de la position GPS actif...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // Icône système
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
            
        startForeground(1, notification)

        // 2. Lancer la détection GPS native
        try {
            // Mise à jour si : temps > 2000 ms ET distance > 2 mètres
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,
                2f,
                this
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return START_STICKY
    }

    // 3. Cette fonction se déclenche TOUTE SEULE dès que tu bouges de plus de 2 mètres
    override fun onLocationChanged(location: Location) {
        serviceScope.launch {
            val point = LocationPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                timestamp = System.currentTimeMillis()
            )
            // Enregistre en base de données (ce qui mettra à jour l'écran automatiquement)
            db.trackingDao().insertPoint(point)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "LOCATION_CHANNEL",
                "Service GPS Natif",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}