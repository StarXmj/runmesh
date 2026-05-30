package com.tonnom.runmesh.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long, // Timestamp du début
    val endTime: Long = 0,
    val totalDistanceMeters: Float = 0f,
    val averageSpeedKmh: Float = 0f,
    val isSyncedP2P: Boolean = false // Pour savoir si on l'a déjà partagée
)