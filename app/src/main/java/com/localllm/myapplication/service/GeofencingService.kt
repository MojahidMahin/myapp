package com.localllm.myapplication.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.localllm.myapplication.data.*
import com.localllm.myapplication.service.integration.GeofenceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Service for managing Android geofencing for workflow triggers
 */
class GeofencingService(
    private val context: Context,
    private val workflowRepository: WorkflowRepository,
    private val workflowEngine: MultiUserWorkflowEngine
) {
    companion object {
        private const val TAG = "GeofencingService"
        private const val GEOFENCE_REQUEST_CODE = 1001
    }

    private var geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
    private var geofencePendingIntent: PendingIntent? = null

    /**
     * Add geofences for all active workflow geofencing triggers
     */
    suspend fun refreshGeofences(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🔄 === REFRESHING LOCATION TRIGGERS ===")
            Log.d(TAG, "🔍 Scanning for active workflows with location triggers...")

            // Get all active workflows with geofencing triggers
            val workflows = workflowRepository.getAllWorkflows().getOrNull() ?: emptyList()
            val geofenceConfigs = mutableListOf<GeofenceConfig>()

            var workflowCount = 0
            workflows.forEach { workflow ->
                if (workflow is MultiUserWorkflow && workflow.isEnabled) {
                    var hasLocationTriggers = false
                    workflow.triggers.forEach { trigger ->
                        when (trigger) {
                            is MultiUserTrigger.GeofenceEnterTrigger -> {
                                hasLocationTriggers = true
                                Log.i(TAG, "📍 Found ENTER trigger: ${trigger.locationName} (${trigger.latitude}, ${trigger.longitude})")
                                geofenceConfigs.add(
                                    GeofenceConfig(
                                        id = trigger.geofenceId,
                                        name = trigger.locationName,
                                        latitude = trigger.latitude,
                                        longitude = trigger.longitude,
                                        radiusMeters = trigger.radiusMeters,
                                        transitionTypes = Geofence.GEOFENCE_TRANSITION_ENTER,
                                        placeId = trigger.placeId
                                    )
                                )
                            }
                            is MultiUserTrigger.GeofenceExitTrigger -> {
                                hasLocationTriggers = true
                                Log.i(TAG, "📍 Found EXIT trigger: ${trigger.locationName} (${trigger.latitude}, ${trigger.longitude})")
                                geofenceConfigs.add(
                                    GeofenceConfig(
                                        id = trigger.geofenceId,
                                        name = trigger.locationName,
                                        latitude = trigger.latitude,
                                        longitude = trigger.longitude,
                                        radiusMeters = trigger.radiusMeters,
                                        transitionTypes = Geofence.GEOFENCE_TRANSITION_EXIT,
                                        placeId = trigger.placeId
                                    )
                                )
                            }
                            is MultiUserTrigger.GeofenceDwellTrigger -> {
                                hasLocationTriggers = true
                                Log.i(TAG, "📍 Found DWELL trigger: ${trigger.locationName} (${trigger.latitude}, ${trigger.longitude})")
                                geofenceConfigs.add(
                                    GeofenceConfig(
                                        id = trigger.geofenceId,
                                        name = trigger.locationName,
                                        latitude = trigger.latitude,
                                        longitude = trigger.longitude,
                                        radiusMeters = trigger.radiusMeters,
                                        transitionTypes = Geofence.GEOFENCE_TRANSITION_DWELL,
                                        placeId = trigger.placeId
                                    )
                                )
                            }
                            else -> {
                                // Skip non-geofence triggers
                            }
                        }
                    }
                    if (hasLocationTriggers) {
                        workflowCount++
                        Log.i(TAG, "   └── Workflow: ${workflow.name} (ID: ${workflow.id})")
                    }
                }
            }

            Log.i(TAG, "📊 Summary: Found $workflowCount workflows with ${geofenceConfigs.size} location trigger(s)")

            // Remove existing geofences and add new ones
            if (geofenceConfigs.isNotEmpty()) {
                Log.i(TAG, "🗑️ Removing existing geofences...")
                removeAllGeofences()
                
                Log.i(TAG, "➕ Adding ${geofenceConfigs.size} new geofence(s)...")
                geofenceConfigs.forEach { config ->
                    Log.d(TAG, "   └── ${config.name}: radius ${config.radiusMeters}m at (${config.latitude}, ${config.longitude})")
                }
                
                val addResult = addGeofences(geofenceConfigs)
                addResult.fold(
                    onSuccess = { count ->
                        Log.i(TAG, "✅ LOCATION TRIGGERS ACTIVATED!")
                        Log.i(TAG, "📍 Successfully registered $count location-based geofence(s)")
                        Log.i(TAG, "🎯 Your workflows will now trigger when you enter/exit these locations")
                        Result.success(count)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "❌ FAILED TO REGISTER LOCATION TRIGGERS!")
                        Log.e(TAG, "💥 Error: ${error.message}")
                        Log.e(TAG, "🔧 Check location permissions and Google Play Services")
                        Result.failure(error)
                    }
                )
            } else {
                Log.i(TAG, "ℹ️ No location-based workflows found - no geofences to register")
                Result.success(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error refreshing location triggers: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Add multiple geofences
     */
    private suspend fun addGeofences(configs: List<GeofenceConfig>): Result<Int> = withContext(Dispatchers.IO) {
        // Comprehensive pre-flight checks
        val diagnostics = checkGeofenceRequirements()
        if (diagnostics.isNotEmpty()) {
            val errorMessage = "Geofence requirements not met: ${diagnostics.joinToString(", ")}"
            Log.e(TAG, "❌ $errorMessage")
            return@withContext Result.failure(Exception(errorMessage))
        }

        return@withContext suspendCoroutine { continuation ->
            try {
                Log.i(TAG, "🔧 Building ${configs.size} geofence(s)...")
                
                val geofences = configs.map { config ->
                    Log.d(TAG, "   └── Building geofence: ${config.name} at (${config.latitude}, ${config.longitude}) radius ${config.radiusMeters}m")
                    Geofence.Builder()
                        .setRequestId(config.id)
                        .setCircularRegion(config.latitude, config.longitude, config.radiusMeters)
                        .setTransitionTypes(config.transitionTypes)
                        .setExpirationDuration(config.expirationDuration)
                        .setNotificationResponsiveness(config.notificationResponsiveness)
                        .build()
                }

                val geofenceRequest = GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
                    .addGeofences(geofences)
                    .build()

                Log.i(TAG, "📡 Registering geofences with Google Play Services...")
                
                // Check permission before making the call
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED) {
                    val error = "Location permission not granted for geofence registration"
                    Log.e(TAG, "❌ $error")
                    continuation.resume(Result.failure(SecurityException(error)))
                    return@suspendCoroutine
                }
                
                try {
                    geofencingClient.addGeofences(geofenceRequest, getGeofencePendingIntent())
                        .addOnSuccessListener {
                            Log.i(TAG, "✅ Geofences registered successfully with Google Play Services")
                            continuation.resume(Result.success(geofences.size))
                        }
                        .addOnFailureListener { exception ->
                            val errorCode = (exception as? com.google.android.gms.common.api.ApiException)?.statusCode
                            val errorMessage = getGeofenceErrorMessage(errorCode)
                            Log.e(TAG, "❌ Failed to register geofences: $errorMessage")
                            Log.e(TAG, "🔍 Exception details: ${exception.message}")
                            continuation.resume(Result.failure(Exception("$errorMessage (Code: $errorCode)")))
                        }
                } catch (e: SecurityException) {
                    Log.e(TAG, "❌ SecurityException: Location permission required", e)
                    continuation.resume(Result.failure(e))
                }
            } catch (e: Exception) {
                Log.e(TAG, "💥 Exception while building geofences: ${e.message}", e)
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * Check all requirements for geofencing to work
     */
    private fun checkGeofenceRequirements(): List<String> {
        val issues = mutableListOf<String>()

        Log.d(TAG, "🔍 Running pre-flight geofencing checks...")

        // Check location permissions
        if (!hasLocationPermissions()) {
            issues.add("Location permissions not granted")
            Log.w(TAG, "❌ Location permissions check failed")
        } else {
            Log.d(TAG, "✅ Location permissions check passed")
        }

        // Check background location permission (Android 10+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val hasBackground = androidx.core.app.ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!hasBackground) {
                issues.add("Background location permission not granted (required for reliable geofencing)")
                Log.w(TAG, "⚠️ Background location permission missing - geofencing may be unreliable")
            } else {
                Log.d(TAG, "✅ Background location permission check passed")
            }
        }

        // Check if Google Play Services is available
        val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(context)
        if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            issues.add("Google Play Services not available (code: $resultCode)")
            Log.e(TAG, "❌ Google Play Services check failed with code: $resultCode")
        } else {
            Log.d(TAG, "✅ Google Play Services check passed")
        }

        // Check if location is enabled
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        
        if (!gpsEnabled && !networkEnabled) {
            issues.add("Location services disabled")
            Log.e(TAG, "❌ Location services check failed - both GPS and Network disabled")
        } else {
            Log.d(TAG, "✅ Location services check passed (GPS: $gpsEnabled, Network: $networkEnabled)")
        }

        // Check if device supports geofencing
        val hasLocationFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)
        if (!hasLocationFeature) {
            issues.add("Device does not support location features")
            Log.e(TAG, "❌ Device location feature check failed")
        } else {
            Log.d(TAG, "✅ Device location feature check passed")
        }

        // Check if running on emulator (emulators often have geofencing issues)
        if (isRunningOnEmulator()) {
            issues.add("Running on emulator - geofencing may not work reliably")
            Log.w(TAG, "⚠️ Running on emulator - geofencing issues are common")
        }

        Log.i(TAG, "🔍 Pre-flight checks complete: ${issues.size} issues found")
        if (issues.isNotEmpty()) {
            Log.w(TAG, "❌ Issues: ${issues.joinToString(", ")}")
        }

        return issues
    }

    /**
     * Check if running on Android emulator
     */
    private fun isRunningOnEmulator(): Boolean {
        return (android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                || "google_sdk" == android.os.Build.PRODUCT)
    }

    /**
     * Get human-readable error message for geofence error codes
     */
    private fun getGeofenceErrorMessage(errorCode: Int?): String {
        return when (errorCode) {
            1000 -> "GEOFENCE_NOT_AVAILABLE - Geofencing is not available on this device"
            1001 -> "GEOFENCE_TOO_MANY_GEOFENCES - Too many geofences registered (max 100)"
            1002 -> "GEOFENCE_TOO_MANY_PENDING_INTENTS - Too many pending intents"
            1003 -> "GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION - Location permission insufficient"
            1004 -> "GEOFENCE_NOT_AVAILABLE - Device doesn't support geofencing or location services disabled"
            else -> "Unknown geofence error"
        }
    }

    /**
     * Remove all geofences
     */
    suspend fun removeAllGeofences(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext suspendCoroutine { continuation ->
            try {
                // Check permission before making the call
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED) {
                    val error = "Location permission not granted for geofence removal"
                    Log.e(TAG, "❌ $error")
                    continuation.resume(Result.failure(SecurityException(error)))
                    return@suspendCoroutine
                }
                
                geofencingClient.removeGeofences(getGeofencePendingIntent())
                    .addOnSuccessListener {
                        Log.d(TAG, "All geofences removed successfully")
                        continuation.resume(Result.success(Unit))
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to remove geofences", exception)
                        continuation.resume(Result.failure(exception))
                    }
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ SecurityException: Location permission required", e)
                continuation.resume(Result.failure(e))
            } catch (e: Exception) {
                Log.e(TAG, "Error removing geofences", e)
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * Remove specific geofences by ID
     */
    suspend fun removeGeofences(geofenceIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext suspendCoroutine { continuation ->
            try {
                // Check permission before making the call
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED) {
                    val error = "Location permission not granted for geofence removal"
                    Log.e(TAG, "❌ $error")
                    continuation.resume(Result.failure(SecurityException(error)))
                    return@suspendCoroutine
                }
                
                geofencingClient.removeGeofences(geofenceIds)
                    .addOnSuccessListener {
                        Log.d(TAG, "Geofences removed: ${geofenceIds.joinToString()}")
                        continuation.resume(Result.success(Unit))
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to remove geofences: ${geofenceIds.joinToString()}", exception)
                        continuation.resume(Result.failure(exception))
                    }
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ SecurityException: Location permission required", e)
                continuation.resume(Result.failure(e))
            } catch (e: Exception) {
                Log.e(TAG, "Error removing specific geofences", e)
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * Handle geofence transitions (called by GeofenceBroadcastReceiver)
     */
    suspend fun handleGeofenceTransition(geofencingEvent: GeofencingEvent) {
        try {
            Log.i(TAG, "🔍 === PROCESSING GEOFENCE TRANSITION ===")
            
            if (geofencingEvent.hasError()) {
                Log.e(TAG, "❌ Geofence error: ${geofencingEvent.errorCode}")
                return
            }

            val geofenceTransition = geofencingEvent.geofenceTransition
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            val transitionName = when (geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
                Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
                Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELL"
                else -> "UNKNOWN($geofenceTransition)"
            }

            Log.i(TAG, "📍 Geofence transition: $transitionName")
            Log.i(TAG, "🎯 Triggering geofences: ${triggeringGeofences?.map { it.requestId }}")

            if (!isValidTransition(geofenceTransition)) {
                Log.w(TAG, "⚠️ Invalid geofence transition: $geofenceTransition")
                return
            }

            if (triggeringGeofences.isNullOrEmpty()) {
                Log.w(TAG, "⚠️ No triggering geofences found")
                return
            }

            triggeringGeofences.forEach { geofence ->
                val geofenceId = geofence.requestId
                Log.i(TAG, "🔄 Processing geofence: $geofenceId (transition: $transitionName)")

                // Find and execute matching workflows using coroutines
                CoroutineScope(Dispatchers.IO).launch {
                    Log.d(TAG, "🔍 Searching for workflows matching geofence: $geofenceId")
                    val matchingWorkflows = findWorkflowsForGeofence(geofenceId, geofenceTransition)
                    
                    if (matchingWorkflows.isEmpty()) {
                        Log.w(TAG, "⚠️ No workflows found for geofence: $geofenceId")
                    } else {
                        Log.i(TAG, "✅ Found ${matchingWorkflows.size} matching workflow(s) for geofence: $geofenceId")
                        matchingWorkflows.forEach { (workflow, trigger) ->
                            Log.i(TAG, "   └── Executing workflow: ${workflow.name} (ID: ${workflow.id})")
                            executeGeofenceWorkflow(workflow, trigger, geofenceTransition, geofenceId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error handling geofence transition: ${e.message}", e)
        }
    }

    /**
     * Find workflows that match a geofence trigger
     */
    private suspend fun findWorkflowsForGeofence(
        geofenceId: String,
        transitionType: Int
    ): List<Pair<MultiUserWorkflow, MultiUserTrigger>> {
        return try {
            val workflows = workflowRepository.getAllWorkflows().getOrNull() ?: emptyList()
            val matches = mutableListOf<Pair<MultiUserWorkflow, MultiUserTrigger>>()

            workflows.forEach { workflow ->
                if (workflow is MultiUserWorkflow && workflow.isEnabled) {
                    workflow.triggers.forEach { trigger ->
                        val isMatch = when (trigger) {
                            is MultiUserTrigger.GeofenceEnterTrigger -> {
                                trigger.geofenceId == geofenceId && 
                                transitionType == Geofence.GEOFENCE_TRANSITION_ENTER
                            }
                            is MultiUserTrigger.GeofenceExitTrigger -> {
                                trigger.geofenceId == geofenceId && 
                                transitionType == Geofence.GEOFENCE_TRANSITION_EXIT
                            }
                            is MultiUserTrigger.GeofenceDwellTrigger -> {
                                trigger.geofenceId == geofenceId && 
                                transitionType == Geofence.GEOFENCE_TRANSITION_DWELL
                            }
                            else -> false
                        }

                        if (isMatch) {
                            matches.add(workflow to trigger)
                            Log.d(TAG, "Found matching workflow: ${workflow.name} for geofence: $geofenceId")
                        }
                    }
                }
            }

            matches
        } catch (e: Exception) {
            Log.e(TAG, "Error finding workflows for geofence", e)
            emptyList()
        }
    }

    /**
     * Execute workflow triggered by geofence event
     */
    private suspend fun executeGeofenceWorkflow(
        workflow: MultiUserWorkflow,
        trigger: MultiUserTrigger,
        transitionType: Int,
        geofenceId: String
    ) {
        try {
            val triggerUserId = when (trigger) {
                is MultiUserTrigger.GeofenceEnterTrigger -> trigger.userId
                is MultiUserTrigger.GeofenceExitTrigger -> trigger.userId
                is MultiUserTrigger.GeofenceDwellTrigger -> trigger.userId
                else -> workflow.createdBy
            }

            val transitionName = when (transitionType) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> "entered"
                Geofence.GEOFENCE_TRANSITION_EXIT -> "exited"
                Geofence.GEOFENCE_TRANSITION_DWELL -> "dwelling_in"
                else -> "unknown"
            }

            val locationName = when (trigger) {
                is MultiUserTrigger.GeofenceEnterTrigger -> trigger.locationName
                is MultiUserTrigger.GeofenceExitTrigger -> trigger.locationName
                is MultiUserTrigger.GeofenceDwellTrigger -> trigger.locationName
                else -> "Unknown Location"
            }

            val triggerData = mapOf(
                "source" to "geofence",
                "geofence_id" to geofenceId,
                "transition_type" to transitionName,
                "location_name" to locationName,
                "timestamp" to System.currentTimeMillis().toString(),
                "type" to "location_trigger"
            )

            Log.i(TAG, "🚀 === EXECUTING LOCATION WORKFLOW ===")
            Log.i(TAG, "📋 Workflow: ${workflow.name} (ID: ${workflow.id})")
            Log.i(TAG, "👤 Trigger User: $triggerUserId")
            Log.i(TAG, "📍 Location: $locationName")
            Log.i(TAG, "🔄 Action: User $transitionName the location")
            Log.i(TAG, "⏰ Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")

            val result = workflowEngine.executeWorkflow(
                workflowId = workflow.id,
                triggerUserId = triggerUserId,
                triggerData = triggerData
            )

            result.fold(
                onSuccess = { executionResult ->
                    Log.i(TAG, "🎉 LOCATION WORKFLOW EXECUTED SUCCESSFULLY!")
                    Log.i(TAG, "✅ Result: ${executionResult.message}")
                    Log.i(TAG, "🏁 Location trigger workflow completed for: $locationName")
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ LOCATION WORKFLOW EXECUTION FAILED!")
                    Log.e(TAG, "💥 Error: ${error.message}")
                    Log.e(TAG, "📍 Failed location: $locationName")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing geofence workflow", e)
        }
    }

    /**
     * Check if location permissions are granted
     */
    private fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get pending intent for geofence transitions
     */
    private fun getGeofencePendingIntent(): PendingIntent {
        if (geofencePendingIntent != null) {
            return geofencePendingIntent!!
        }

        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        geofencePendingIntent = PendingIntent.getBroadcast(
            context,
            GEOFENCE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return geofencePendingIntent!!
    }

    /**
     * Check if geofence transition type is valid
     */
    private fun isValidTransition(transitionType: Int): Boolean {
        return transitionType == Geofence.GEOFENCE_TRANSITION_ENTER ||
                transitionType == Geofence.GEOFENCE_TRANSITION_EXIT ||
                transitionType == Geofence.GEOFENCE_TRANSITION_DWELL
    }

    /**
     * Create a geofence ID for workflow triggers
     */
    fun createGeofenceId(workflowId: String, userId: String, locationName: String): String {
        return "${workflowId}_${userId}_${locationName.replace(" ", "_")}_${System.currentTimeMillis()}"
    }

    /**
     * Request background location permission through settings
     */
    fun requestBackgroundLocationThroughSettings(): android.content.Intent? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val hasBackground = androidx.core.app.ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!hasBackground) {
                Log.i(TAG, "🔧 Creating intent to open app-specific settings for background location")
                val intent = android.content.Intent()
                intent.action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                intent.data = android.net.Uri.fromParts("package", context.packageName, null)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                intent
            } else {
                null
            }
        } else {
            null
        }
    }

    /**
     * Comprehensive diagnostic check for geofencing capability
     */
    fun diagnoseGeofencingStatus(): String {
        val report = StringBuilder()
        report.appendLine("🔍 === GEOFENCING DIAGNOSTIC REPORT ===")
        
        // 1. Location Permissions
        if (hasLocationPermissions()) {
            report.appendLine("✅ Location permissions: GRANTED")
        } else {
            report.appendLine("❌ Location permissions: NOT GRANTED")
            report.appendLine("   Fix: Go to Settings > Apps > YourApp > Permissions > Location > Allow")
        }

        // 2. Background location permission (Android 10+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val hasBackground = androidx.core.app.ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (hasBackground) {
                report.appendLine("✅ Background location: GRANTED")
            } else {
                report.appendLine("❌ Background location: NOT GRANTED (CRITICAL FOR GEOFENCING)")
                report.appendLine("   📱 MANUAL FIX REQUIRED:")
                report.appendLine("   1. Open Android Settings > Apps > ${context.applicationInfo.loadLabel(context.packageManager)}")
                report.appendLine("   2. Tap 'Permissions' > 'Location'")
                report.appendLine("   3. Select 'Allow all the time' (NOT just 'Allow only while using app')")
                report.appendLine("   ⚠️  Without this, location triggers will NOT work reliably!")
            }
        }

        // 3. Google Play Services
        val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
        val gpsResult = availability.isGooglePlayServicesAvailable(context)
        if (gpsResult == com.google.android.gms.common.ConnectionResult.SUCCESS) {
            report.appendLine("✅ Google Play Services: AVAILABLE")
        } else {
            report.appendLine("❌ Google Play Services: NOT AVAILABLE (code: $gpsResult)")
            report.appendLine("   Fix: Update Google Play Services from Play Store")
        }

        // 4. Location Services
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        
        if (gpsEnabled || networkEnabled) {
            report.appendLine("✅ Location services: ENABLED")
            if (gpsEnabled) report.appendLine("   └── GPS: ON")
            if (networkEnabled) report.appendLine("   └── Network: ON")
        } else {
            report.appendLine("❌ Location services: DISABLED")
            report.appendLine("   Fix: Settings > Location > Turn on")
        }

        // 5. Battery optimization
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                report.appendLine("✅ Battery optimization: DISABLED (Good)")
            } else {
                report.appendLine("⚠️ Battery optimization: ENABLED")
                report.appendLine("   Tip: Disable battery optimization for reliable location triggers")
                report.appendLine("   Fix: Settings > Battery > Battery optimization > YourApp > Don't optimize")
            }
        }

        // 6. Device capability
        val hasGeofencing = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)
        if (hasGeofencing) {
            report.appendLine("✅ Device location features: SUPPORTED")
        } else {
            report.appendLine("❌ Device location features: NOT SUPPORTED")
        }

        // 7. Overall status
        val issues = checkGeofenceRequirements()
        if (issues.isEmpty()) {
            report.appendLine("\n🎉 OVERALL STATUS: READY FOR LOCATION TRIGGERS!")
        } else {
            report.appendLine("\n⚠️ OVERALL STATUS: ${issues.size} ISSUE(S) FOUND")
            issues.forEach { issue ->
                report.appendLine("   - $issue")
            }
        }

        val result = report.toString()
        Log.i(TAG, result)
        return result
    }
}