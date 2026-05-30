package com.tonnom.runmesh.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "location_points")
data class LocationPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timestamp: Long
)

@Dao
interface TrackingDao {
    @Insert
    suspend fun insertPoint(point: LocationPoint)

    // Permet d'écouter les logs en temps réel, du plus récent au plus ancien
    @Query("SELECT * FROM location_points ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LocationPoint>>
}

@Database(entities = [LocationPoint::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackingDao(): TrackingDao
    
    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "runmesh_db")
                    .fallbackToDestructiveMigration() 
                    .build().also { instance = it }
            }
    }
}