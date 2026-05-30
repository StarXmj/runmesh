import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun RealTimeLocationScreen(dao: TrackingDao) {
    // Collecte les données en temps réel :
    val currentPosition by dao.getLastPosition().collectAsState(initial = null)

    if (currentPosition != null) {
        Text(text = "Position actuelle : ${currentPosition?.latitude}, ${currentPosition?.longitude}")
        // C'est ici que tu mettras ta carte !
    } else {
        Text(text = "En attente du signal GPS...")
    }
}