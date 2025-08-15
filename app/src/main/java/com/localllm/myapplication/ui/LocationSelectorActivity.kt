package com.localllm.myapplication.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*

@Parcelize
data class LocationSelection(
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val address: String = "",
    val radiusMeters: Float = 100f
) : Parcelable

class LocationSelectorActivity : ComponentActivity(), OnMapReadyCallback {
    
    companion object {
        const val EXTRA_SELECTED_LOCATION = "selected_location"
        const val EXTRA_INITIAL_LOCATION = "initial_location"
        const val EXTRA_INITIAL_RADIUS = "initial_radius"
        private const val DEFAULT_ZOOM = 15f
    }
    
    private var googleMap: GoogleMap? = null
    private var currentMarker: Marker? = null
    private var currentCircle: Circle? = null
    private lateinit var mapView: MapView
    private var geocoder: Geocoder? = null
    
    // State variables
    private var selectedLocation by mutableStateOf<LocationSelection?>(null)
    private var currentRadius by mutableStateOf(100f)
    private var isLoading by mutableStateOf(false)
    private var locationName by mutableStateOf("")
    
    // Location permission launcher
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            enableMyLocation()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        geocoder = if (Geocoder.isPresent()) Geocoder(this, Locale.getDefault()) else null
        
        // Get initial values from intent
        val initialLocation = intent.getParcelableExtra<LatLng>(EXTRA_INITIAL_LOCATION)
        currentRadius = intent.getFloatExtra(EXTRA_INITIAL_RADIUS, 100f)
        
        setContent {
            LocationSelectorScreen(
                onLocationSelected = { location ->
                    selectedLocation = location
                },
                onRadiusChanged = { radius ->
                    currentRadius = radius
                    updateCircleRadius()
                },
                onConfirm = {
                    selectedLocation?.let { location ->
                        val resultIntent = android.content.Intent().apply {
                            putExtra(EXTRA_SELECTED_LOCATION, location)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }
                },
                onCancel = {
                    setResult(RESULT_CANCELED)
                    finish()
                },
                initialRadius = currentRadius,
                initialLocation = initialLocation
            )
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LocationSelectorScreen(
        onLocationSelected: (LocationSelection) -> Unit,
        onRadiusChanged: (Float) -> Unit,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
        initialRadius: Float,
        initialLocation: LatLng?
    ) {
        var radiusText by remember { mutableStateOf(initialRadius.toInt().toString()) }
        var showAddressDialog by remember { mutableStateOf(false) }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Select Location") },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onConfirm,
                            enabled = selectedLocation != null
                        ) {
                            Icon(Icons.Default.Check, "Confirm")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Map View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(
                        factory = { context ->
                            MapView(context).apply {
                                mapView = this
                                onCreate(null)
                                getMapAsync(this@LocationSelectorActivity)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // My Location button
                    FloatingActionButton(
                        onClick = { getCurrentLocation() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.LocationOn, "My Location", tint = Color.White)
                    }
                    
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                
                // Controls Panel
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Location Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Selected location info
                        selectedLocation?.let { location ->
                            Text(
                                text = "Selected: ${location.locationName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (location.address.isNotEmpty()) {
                                Text(
                                    text = location.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        // Radius control
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Radius:",
                                modifier = Modifier.width(60.dp)
                            )
                            
                            OutlinedTextField(
                                value = radiusText,
                                onValueChange = { newValue ->
                                    radiusText = newValue
                                    newValue.toFloatOrNull()?.let { radius ->
                                        if (radius > 0) {
                                            onRadiusChanged(radius)
                                        }
                                    }
                                },
                                modifier = Modifier.width(100.dp),
                                singleLine = true,
                                suffix = { Text("m") }
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            // Preset radius buttons
                            Row {
                                listOf(50, 100, 200, 500).forEach { preset ->
                                    FilterChip(
                                        onClick = {
                                            radiusText = preset.toString()
                                            onRadiusChanged(preset.toFloat())
                                        },
                                        label = { Text("${preset}m") },
                                        selected = radiusText == preset.toString(),
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Instructions
                        Text(
                            text = "Tap on the map to select a location for your geofence trigger",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // Address input dialog
        if (showAddressDialog) {
            var tempLocationName by remember { mutableStateOf(locationName) }
            
            AlertDialog(
                onDismissRequest = { showAddressDialog = false },
                title = { Text("Location Name") },
                text = {
                    Column {
                        Text("Enter a name for this location:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tempLocationName,
                            onValueChange = { tempLocationName = it },
                            label = { Text("Location Name") },
                            placeholder = { Text("e.g., Home, Office, Gym") }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            locationName = tempLocationName
                            selectedLocation?.let { location ->
                                onLocationSelected(
                                    location.copy(locationName = tempLocationName.ifBlank { "Selected Location" })
                                )
                            }
                            showAddressDialog = false
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddressDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
    
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        // Set up map
        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = false // We have our own button
        }
        
        // Set map click listener
        map.setOnMapClickListener { latLng ->
            selectLocation(latLng)
        }
        
        // Request location permission and enable location
        checkLocationPermission()
        
        // Move to initial location if provided
        intent.getParcelableExtra<LatLng>(EXTRA_INITIAL_LOCATION)?.let { initialLocation ->
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLocation, DEFAULT_ZOOM))
            selectLocation(initialLocation)
        } ?: getCurrentLocation()
    }
    
    private fun selectLocation(latLng: LatLng) {
        googleMap?.let { map ->
            // Remove previous marker and circle
            currentMarker?.remove()
            currentCircle?.remove()
            
            // Add new marker
            currentMarker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Selected Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )
            
            // Add radius circle
            currentCircle = map.addCircle(
                CircleOptions()
                    .center(latLng)
                    .radius(currentRadius.toDouble())
                    .strokeColor(0x330000FF)
                    .fillColor(0x110000FF)
                    .strokeWidth(2f)
            )
            
            // Get address using geocoder
            lifecycleScope.launch {
                val address = getAddressFromLocation(latLng.latitude, latLng.longitude)
                val locationName = generateLocationName(address)
                
                selectedLocation = LocationSelection(
                    latitude = latLng.latitude,
                    longitude = latLng.longitude,
                    locationName = locationName,
                    address = address,
                    radiusMeters = currentRadius
                )
            }
        }
    }
    
    private fun updateCircleRadius() {
        currentCircle?.radius = currentRadius.toDouble()
        selectedLocation = selectedLocation?.copy(radiusMeters = currentRadius)
    }
    
    private suspend fun getAddressFromLocation(latitude: Double, longitude: Double): String {
        return try {
            geocoder?.getFromLocation(latitude, longitude, 1)?.firstOrNull()?.let { address ->
                buildString {
                    if (!address.thoroughfare.isNullOrEmpty()) {
                        append(address.thoroughfare)
                    }
                    if (!address.locality.isNullOrEmpty()) {
                        if (isNotEmpty()) append(", ")
                        append(address.locality)
                    }
                    if (!address.adminArea.isNullOrEmpty()) {
                        if (isNotEmpty()) append(", ")
                        append(address.adminArea)
                    }
                }
            } ?: "Unknown Address"
        } catch (e: IOException) {
            "Address not available"
        }
    }
    
    private fun generateLocationName(address: String): String {
        return when {
            address.contains("home", ignoreCase = true) -> "Home"
            address.contains("work", ignoreCase = true) || address.contains("office", ignoreCase = true) -> "Office"
            address.contains("school", ignoreCase = true) || address.contains("university", ignoreCase = true) -> "School"
            address.isNotEmpty() && address != "Unknown Address" -> address.split(",").firstOrNull()?.trim() ?: "Selected Location"
            else -> "Selected Location"
        }
    }
    
    private fun getCurrentLocation() {
        if (!hasLocationPermission()) {
            requestLocationPermission()
            return
        }
        
        isLoading = true
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                isLoading = false
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))
                }
            }.addOnFailureListener {
                isLoading = false
            }
        } catch (e: SecurityException) {
            isLoading = false
        }
    }
    
    private fun checkLocationPermission() {
        if (hasLocationPermission()) {
            enableMyLocation()
        } else {
            requestLocationPermission()
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    
    private fun enableMyLocation() {
        try {
            googleMap?.isMyLocationEnabled = hasLocationPermission()
        } catch (e: SecurityException) {
            // Handle permission error
        }
    }
    
    // MapView lifecycle methods
    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) {
            mapView.onResume()
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (::mapView.isInitialized) {
            mapView.onPause()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::mapView.isInitialized) {
            mapView.onDestroy()
        }
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        if (::mapView.isInitialized) {
            mapView.onLowMemory()
        }
    }
}