package com.tonnom.runmesh.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_points")
data class LocationPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,      // Nouvelle statistique : vitesse (m/s)
    val accuracy: Float,   // Nouvelle statistique : précision du point (m)
    val isEstimated: Boolean, // Indique si le point vient de la fusion des capteurs
    val timestamp: Long
)