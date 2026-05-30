package com.tonnom.runmesh.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingDao {
    @Insert
    suspend fun insertPoint(point: LocationPoint)

    @Query("SELECT * FROM location_points ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LocationPoint>>
}
