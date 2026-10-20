package com.example.bikelog

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import java.util.Locale

data class Bike(val id: Int, val name: String, var totalDistanceKm: Double)

class MainActivity : ComponentActivity() {

    private class LocationServiceState {
        var currentSpeed by mutableStateOf(0.0)
        var maxSpeed by mutableStateOf(0.0)
        var tripDistance by mutableStateOf(0.0)
        var tripTimeSeconds by mutableStateOf(0L)
        var isTracking by mutableStateOf(false)
    }

    private val state = LocationServiceState()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastLocation: Location? = null

    private var bikes = mutableStateOf(listOf(
        Bike(1, "Горный", 125.5),
        Bike(2, "Шоссейный", 430.2),
        Bike(3, "Городской", 45.0)
    ))
    private var selectedBikeId by mutableStateOf(1)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) { startLocationTracking() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    if (state.isTracking) {
                        lastLocation?.let { prev ->
                            state.tripDistance += prev.distanceTo(location).toDouble()
                        }
                        lastLocation = location
                        val speedKmH = if (location.hasSpeed()) location.speed * 3.6 else 0.0
                        state.currentSpeed = if (speedKmH < 1.0) 0.0 else speedKmH
                        if (state.currentSpeed > state.maxSpeed) {
                            state.maxSpeed = state.currentSpeed
                        }
                    }
                }
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    BikeSpeedometerScreen(
                        currentSpeed = state.currentSpeed,
                        maxSpeed = state.maxSpeed,
                        tripDistanceKm = state.tripDistance / 1000.0,
                        tripTimeSeconds = state.tripTimeSeconds,
                        isTracking = state.isTracking,
                        bikes = bikes.value,
                        selectedBikeId = selectedBikeId,
                        onSelectBike = { selectedBikeId = it },
                        onUpdateOdometer = { bikeId, newKm ->
                            bikes.value = bikes.value.map {
                                if (it.id == bikeId) it.copy(totalDistanceKm = newKm) else it
                            }
                        },
                        onToggleTracking = {
                            if (state.isTracking) {
                                stopLocationTracking()
                            } else {
                                checkAndStartTracking()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun checkAndStartTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationTracking()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun startLocationTracking() {
        state.isTracking = true
        state.tripDistance = 0.0
        state.maxSpeed = 0.0
        lastLocation = null

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateDistanceMeters(1f)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    private fun stopLocationTracking() {
        state.isTracking = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        
        bikes.value = bikes.value.map { bike ->
            if (bike.id == selectedBikeId) {
                bike.copy(totalDistanceKm = bike.totalDistanceKm + (state.tripDistance / 1000.0))
            } else bike
        }
    }
}

@Composable
fun EditOdometerDialog(
    currentValue: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var textValue by remember { mutableStateOf(currentValue.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Корректировка одометра", color = Color.White) },
        text = {
            Column {
                Text(text = "Введите текущий общий пробег (км):", color = Color.LightGray)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newValue = textValue.toDoubleOrNull() ?: currentValue
                onConfirm(newValue)
            }) {
                Text("Сохранить", color = Color.Red)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1C1C1C)
    )
}

@Composable
fun BikeSpeedometerScreen(
    currentSpeed: Double,
    maxSpeed: Double,
    tripDistanceKm: Double,
    tripTimeSeconds: Long,
    isTracking: Boolean,
    bikes: List<Bike>,
    selectedBikeId: Int,
    onSelectBike: (Int) -> Unit,
    onUpdateOdometer: (Int, Double) -> Unit,
    onToggleTracking: () -> Unit
) {
    val selectedBike = bikes.find { it.id == selectedBikeId }
    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog && selectedBike != null) {
        EditOdometerDialog(
            currentValue = selectedBike.totalDistanceKm,
            onDismiss = { showEditDialog = false },
            onConfirm = { newKm ->
                onUpdateOdometer(selectedBike.id, newKm)
                showEditDialog = false
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                bikes.forEach { bike ->
                    Button(
                        onClick = { onSelectBike(bike.id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (bike.id == selectedBikeId) Color.Red else Color.DarkGray
                        )
                    ) {
                        Text(text = bike.name, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            TextButton(onClick = { showEditDialog = true }) {
                Text(
                    text = "Общий пробег: ${String.format(Locale.getDefault(), "%.1f", selectedBike?.totalDistanceKm ?: 0.0)} км ✎",
                    color = Color.LightGray,
                    fontSize = 15.sp
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format(Locale.getDefault(), "%.0f", currentSpeed),
                fontSize = 100.sp,
                color = Color.Red
            )
            Text(text = "КМ/Ч", fontSize = 18.sp, color = Color.Red)
        }

        Column(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1C1C1C), shape = MaterialTheme.shapes.medium).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem(label = "Дистанция", value = String.format(Locale.getDefault(), "%.2f км", tripDistanceKm))
                MetricItem(label = "Скорость текущая", value = String.format(Locale.getDefault(), "%.1f км/ч", currentSpeed))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem(label = "Длительность", value = formatTime(tripTimeSeconds))
                MetricItem(label = "Максимальная", value = String.format(Locale.getDefault(), "%.1f км/ч", maxSpeed))
            }
        }

        Button(
            onClick = onToggleTracking,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTracking) Color.DarkGray else Color.Red
            )
        ) {
            Text(text = if (isTracking) "СТОП" else "СТАРТ", fontSize = 18.sp, color = Color.White)
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column {
        Text(text = label, color = Color.Gray, fontSize = 12.sp)
        Text(text = value, color = Color.White, fontSize = 20.sp)
    }
}

fun formatTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
    }
}