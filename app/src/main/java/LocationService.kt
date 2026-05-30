package com.tonnom.runmesh

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tonnom.runmesh.data.AppDatabase
import com.tonnom.runmesh.data.LocationPoint
import com.tonnom.runmesh.data.Session
import kotlinx.coroutines.*

class LocationService : Service() {
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    private lateinit var db: AppDatabase
    
    // Scope propre pour sécuriser l'écriture en base de données
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentSessionId: Long = -1L

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        // 1. Initialisation de l'API Native
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // 2. Écouteur natif des changements de position
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                serviceScope.launch {
                    if (currentSessionId != -1L) {
                        val point = LocationPoint(
                            sessionId = currentSessionId,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            altitude = location.altitude,
                            timestamp = location.time // Temps absolu fourni par le satellite
                        )
                        db.trackingDao().insertPoint(point)
                    }
                }
            }
            
            // Requis sur les anciennes API
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START") startTracking()
        if (intent?.action == "STOP") stopTracking()
        return START_STICKY // Redémarre si tué par le système
    }

    @SuppressLint("MissingPermission") // La vérification est faite dans MainActivity
    private fun startTracking() {
        val channelId = "runmesh_channel"
        val notifManager = getSystemService(NotificationManager::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifManager.createNotificationChannel(
                NotificationChannel(channelId, "Tracking", NotificationManager.IMPORTANCE_LOW)
            )
        }

        // ⚠️ Correction de l'icône pour éviter les crashs sur Xiaomi/Samsung
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("RunMesh")
            .setContentText("Enregistrement GPS (Natif) en cours...")
            .setSmallIcon(R.mipmap.ic_launcher) 
            .setOngoing(true)
            .build()

        // ⚠️ Obligatoire depuis Android 14
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }

        serviceScope.launch {
            // Création d'une nouvelle session en base de données
            currentSessionId = db.trackingDao().insertSession(Session(startTime = System.currentTimeMillis()))
            
            withContext(Dispatchers.Main) {
                try {
                    // Demande des mises à jour GPS via le composant puce physique (GPS_PROVIDER)
                    // Paramètres : 5000 ms (5s), 2f (2 mètres de déplacement minimum)
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        5000L,
                        2f,
                        locationListener
                    )
                } catch (e: SecurityException) {
                    e.printStackTrace() // L'utilisateur a refusé les permissions au dernier moment
                }
            }
        }
    }

    private fun stopTracking() {
        locationManager.removeUpdates(locationListener) // Coupe la puce GPS
        stopForeground(STOP_FOREGROUND_REMOVE) // Retire la notification
        stopSelf() // Arrête le service
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // Nettoie la RAM
    }

    override fun onBind(intent: Intent?): IBinder? = null
}