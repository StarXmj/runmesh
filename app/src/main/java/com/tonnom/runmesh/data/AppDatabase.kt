import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long
)

@Entity(
    tableName = "location_points",
    foreignKeys = [
        ForeignKey(
            entity = Session::class, 
            parentColumns = ["id"], 
            childColumns = ["sessionId"], 
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class LocationPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long, // Indispensable pour séparer les courses
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timestamp: Long // Temps absolu du satellite
)

@Dao
interface TrackingDao {
    @Insert
    suspend fun insertSession(session: Session): Long // Retourne l'ID généré

    @Insert
    suspend fun insertPoint(point: LocationPoint)
}

@Database(entities = [Session::class, LocationPoint::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackingDao(): TrackingDao
    
    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun getDatabase(context: android.content.Context): AppDatabase =
            instance ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "runmesh_db")
                    .fallbackToDestructiveMigration() // Reset la DB en dev
                    .build().also { instance = it }
            }
    }
}