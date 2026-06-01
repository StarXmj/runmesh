package com.tonnom.runmesh

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tonnom.runmesh.data.AppDatabase
import com.tonnom.runmesh.data.LocationPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

class LocationService : Service(), LocationListener, SensorEventListener {

    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private lateinit var db: AppDatabase
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Stratégie hybride
    private var isGpsFixObtained = false
    
    // Suivi du temps pour la perte GPS
    private var lastGpsTime: Long = 0
    private val MAX_ESTIMATION_DURATION_MS = 15000L // 15 secondes max sans GPS
    
    // Dernières données GPS connues
    private var lastKnownLat = 0.0
    private var lastKnownLon = 0.0
    private var lastKnownSpeed = 0f
    private var lastKnownBearing = 0f

    // Capteurs
    private var barometerAltitude = 0.0
    private var currentAccel = FloatArray(3)
    private var currentMag = FloatArray(3)
    
    // Filtre de Kalman (Lat, Lon, Vitesse, Altitude)
    private var kLat = 0.0
    private var kLon = 0.0
    private var kAlt = 0.0
    private var kSpeed = 0f
    private var kVariance = -1.0

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(applicationContext)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "LOCATION_CHANNEL")
            .setContentTitle("RunMesh Tracking")
            .setContentText("Acquisition de précision en cours...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1, notification)

        // Phase 1 : Démarrage hybride (GPS + Network pour fix rapide)
        val minTimeMs = 2000L // Fréquence dynamique optimisée batterie
        val minDistanceM = 1.0f

        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, this)
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTimeMs, minDistanceM, this)

        // Activation des capteurs pour la fusion (seulement pendant l'activité)
        registerSensors()
        
        return START_STICKY
    }

    override fun onLocationChanged(location: Location) {
        // Phase 1 : Bascule Network -> GPS
        if (location.provider == LocationManager.GPS_PROVIDER) {
            if (!isGpsFixObtained) {
                isGpsFixObtained = true
                // On a le GPS, on coupe le Network provider pour économiser la batterie
                locationManager.removeUpdates(this) 
                @SuppressLint("MissingPermission")
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 1.0f, this)
            }
        } else if (isGpsFixObtained) {
            return // Ignorer les points réseau si on a déjà le GPS
        }

        // Phase 2 : Filtrage de la qualité
        if (!location.hasAccuracy()) return
        val accuracy = location.accuracy

        if (accuracy > 20f) {
            Log.d("RunMesh", "Point rejeté: Précision insuffisante ($accuracy m)")
            return // Rejeter purement le point
        }

        var measurementNoise = accuracy.toDouble()
        if (accuracy in 10f..20f) {
            // Accepter avec pénalité : on augmente le bruit de mesure pour que Kalman s'y fie moins
            measurementNoise *= 2.0 
        }

        // Sauvegarde des données pour la prédiction hors-ligne (perte GPS temporaire)
        lastGpsTime = System.currentTimeMillis()
        lastKnownLat = location.latitude
        lastKnownLon = location.longitude
        lastKnownSpeed = if (location.hasSpeed()) location.speed else lastKnownSpeed
        lastKnownBearing = if (location.hasBearing()) location.bearing else lastKnownBearing

        // Fusion Altitude (Baromètre + GPS)
        val fusedAltitude = if (barometerAltitude > 0) (location.altitude * 0.3 + barometerAltitude * 0.7) else location.altitude

        // Application du Filtre de Kalman
        applyKalmanFilter(location.latitude, location.longitude, fusedAltitude, lastKnownSpeed, measurementNoise)

        savePoint(kLat, kLon, kAlt, kSpeed, accuracy, false)
    }

    private fun applyKalmanFilter(lat: Double, lon: Double, alt: Double, speed: Float, noise: Double) {
        if (kVariance < 0) {
            // Initialisation
            kLat = lat; kLon = lon; kAlt = alt; kSpeed = speed
            kVariance = noise
        } else {
            // Prédiction et Mise à jour (Filtre simplifié)
            val processNoise = 1.0 // Bruit de process interne
            kVariance += processNoise

            val kalmanGain = kVariance / (kVariance + noise)
            kLat += kalmanGain * (lat - kLat)
            kLon += kalmanGain * (lon - kLon)
            kAlt += kalmanGain * (alt - kAlt)
            kSpeed += (kalmanGain * (speed - kSpeed)).toFloat()

            kVariance *= (1 - kalmanGain)
        }
    }

    // -- FUSION DE CAPTEURS & PERTE GPS TEMPORAIRE --

    private fun registerSensors() {
        val baro = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        
        baro?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        mag?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_PRESSURE -> {
                // Conversion pression atmosphérique -> Altitude (formule standard)
                barometerAltitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0]).toDouble()
            }
            Sensor.TYPE_ACCELEROMETER -> currentAccel = event.values.clone()
            Sensor.TYPE_MAGNETIC_FIELD -> {
                currentMag = event.values.clone()
                predictPathIfGpsLost()
            }
        }
    }

    private fun predictPathIfGpsLost() {
        val now = System.currentTimeMillis()
        val timeSinceLastGps = now - lastGpsTime

        // Si le GPS est perdu depuis plus de 2 sec, mais moins de MAX_ESTIMATION_DURATION
        if (isGpsFixObtained && timeSinceLastGps in 2000..MAX_ESTIMATION_DURATION_MS) {
            // Estimation simple par Dead Reckoning (Dernière vitesse connue + Capteurs)
            val deltaTimeS = 1.0 // On prédit pour la dernière seconde
            
            // Calcul simplifié de la nouvelle distance
            val distance = lastKnownSpeed * deltaTimeS
            val earthRadius = 6378137.0 // en mètres
            
            // Calcul de la dérive en coordonnées
            val dLat = distance * cos(Math.toRadians(lastKnownBearing.toDouble())) / earthRadius
            val dLon = distance * sin(Math.toRadians(lastKnownBearing.toDouble())) / (earthRadius * cos(Math.toRadians(lastKnownLat)))

            val predictedLat = lastKnownLat + Math.toDegrees(dLat)
            val predictedLon = lastKnownLon + Math.toDegrees(dLon)
            
            // On met à jour les dernières coordonnées connues pour avancer le point
            lastKnownLat = predictedLat
            lastKnownLon = predictedLon
            lastGpsTime = now // On reset le timer pour avancer pas à pas

            savePoint(predictedLat, predictedLon, barometerAltitude, lastKnownSpeed, 25f, true)
        }
    }

    private fun savePoint(lat: Double, lon: Double, alt: Double, speed: Float, accuracy: Float, isEstimated: Boolean) {
        serviceScope.launch {
            val point = LocationPoint(
                latitude = lat,
                longitude = lon,
                altitude = alt,
                speed = speed,
                accuracy = accuracy,
                isEstimated = isEstimated,
                timestamp = System.currentTimeMillis()
            )
            db.trackingDao().insertPoint(point)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onProviderDisabled(provider: String) {}
    override fun onProviderEnabled(provider: String) {}

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
        sensorManager.unregisterListener(this) // Arrêt auto des capteurs hors session
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("LOCATION_CHANNEL", "RunMesh Tracking", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}