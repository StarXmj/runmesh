package com.tonnom.runmesh.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    // Insérer une nouvelle course et récupérer son ID unique
    @Insert
    suspend fun insertSession(session: Session): Long

    // Insérer un point GPS lié à une course
    @Insert
    suspend fun insertLocationPoint(point: LocationPoint)

    // Récupérer toutes les courses (Flow permet une mise à jour en direct de l'UI)
    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<Session>>
}