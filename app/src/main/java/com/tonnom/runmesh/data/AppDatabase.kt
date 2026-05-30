package com.tonnom.runmesh.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 1. La Table (Ce qu'on sauvegarde)
@Entity(tableName = "location_points")
data class LocationPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timestamp: Long
)

// 2. Le DAO (Les requêtes pour écrire/lire)
@Dao
interface LocationDao {
    @Insert
    suspend fun insertPoint(point: LocationPoint)

    @Query("SELECT * FROM location_points ORDER BY timestamp DESC")
    fun getAllPoints(): Flow<List<LocationPoint>>
}

// 3. La Base de données
@Database(entities = [LocationPoint::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao

    // Un Singleton pour s'assurer qu'on n'ouvre qu'une seule base
    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "runmesh_offline_db"
                ).build().also { instance = it }
            }
        }
    }
}