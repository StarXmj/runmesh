import kotlinx.coroutines.flow.Flow
import androidx.room.Query

// Dans ton interface TrackingDao :
@Query("SELECT * FROM location_points ORDER BY id DESC LIMIT 1")
fun getLastPosition(): Flow<LocationPoint>