package com.tonnom.runmesh

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonnom.runmesh.data.AppDatabase
import com.tonnom.runmesh.ui.theme.RunMeshTheme
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            RunMeshTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LogScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun LogScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(false) }

    // On écoute la Base de données en temps réel (Logs et Position courante)
    val db = remember { AppDatabase.getDatabase(context) }
    val logs by db.trackingDao().getAllLogs().collectAsState(initial = emptyList())
    val currentPosition by db.trackingDao().getLastPosition().collectAsState(initial = null)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasPermissions = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            
            // Si on a le GPS, on lance le service natif en fond DIRECTEMENT
            if (hasPermissions) {
                val intent = Intent(context, LocationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        if (!hasPermissions) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("En attente des permissions GPS...")
            }
        } else {
            // Affichage de la position en temps réel (Le bloc rouge en haut)
            if (currentPosition != null) {
                Text(text = "📡 Position actuelle :", fontSize = 18.sp, color = Color.Red, modifier = Modifier.padding(bottom = 4.dp))
                Text(text = "Lat: ${currentPosition?.latitude}", fontSize = 16.sp, modifier = Modifier.padding(bottom = 2.dp))
                Text(text = "Lon: ${currentPosition?.longitude}", fontSize = 16.sp, modifier = Modifier.padding(bottom = 16.dp))
            } else {
                Text(text = "📡 En attente du signal GPS...", fontSize = 18.sp, color = Color.Red, modifier = Modifier.padding(bottom = 16.dp))
            }

            // En-tête de l'historique
            Text(text = "📋 Historique (Filtre > 2m)", fontSize = 18.sp, modifier = Modifier.padding(bottom = 4.dp))
            Text(text = "Points enregistrés : ${logs.size}", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
            
            // La liste de type "Log"
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(logs) { point ->
                    val timeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(point.timestamp))
                    
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(text = "[$timeString]", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.Blue)
                        Text(text = "Lat: ${point.latitude}", fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        Text(text = "Lon: ${point.longitude}", fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        Text(text = "Alt: ${point.altitude}m", fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}