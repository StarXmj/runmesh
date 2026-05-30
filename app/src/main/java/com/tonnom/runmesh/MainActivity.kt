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
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tonnom.runmesh.ui.theme.RunMeshTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            RunMeshTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TrackingScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun TrackingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(false) }

    // 1. Déclaration du contrat pour demander de multiples permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            // On vérifie si la localisation précise OU approximative est accordée
            val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
                                  permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            hasPermissions = locationGranted
        }
    )

    // 2. Demande automatique des permissions au lancement de l'écran
    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        // Depuis Android 13 (API 33), il faut demander l'autorisation pour la notification du Foreground Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    // 3. Interface Utilisateur
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!hasPermissions) {
            Text("Les permissions GPS sont requises pour tracker votre course.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                val permissionsToRequest = mutableListOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionLauncher.launch(permissionsToRequest.toTypedArray())
            }) {
                Text("Autoriser le GPS")
            }
        } else {
            Button(onClick = {
                val intent = Intent(context, LocationService::class.java).apply {
                    action = "START"
                }
                // ⚠️ CRITIQUE : Depuis Android 8.0, un Foreground Service DOIT être lancé via startForegroundService
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }) {
                Text("Démarrer la course")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                val intent = Intent(context, LocationService::class.java).apply {
                    action = "STOP"
                }
                context.startService(intent)
            }) {
                Text("Arrêter la course")
            }
        }
    }
}