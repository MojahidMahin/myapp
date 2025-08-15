package com.localllm.myapplication.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
            Log.d(TAG, "Refreshing geofences for active workflows")

            // Get all active workflows with geofencing triggers
            val workflows = workflowRepository.getAllWorkflows().getOrNull() ?: emptyList()
            val geofenceConfigs = mutableListOf<GeofenceConfig>()

            workflows.forEach { workflow ->
                if (workflow is MultiUserWorkflow && workflow.isEnabled) {
                    workflow.triggers.forEach { trigger ->
                        when (trigger) {
                            is MultiUserTrigger.GeofenceEnterTrigger -> {
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
                }
            }

            Log.d(TAG, "Found ${geofenceConfigs.size} geofence configurations")

            // Remove existing geofences and add new ones
            if (geofenceConfigs.isNotEmpty()) {
                removeAllGeofences()
                val addResult = addGeofences(geofenceConfigs)
                addResult.fold(
                    onSuccess = { count ->
                        Log.i(TAG, "Successfully added $count geofences")
                        Result.success(count)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to add geofences", error)
                        Result.failure(error)
                    }
                )
            } else {
                Log.d(TAG, "No geofences to add")
                Result.success(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing geofences", e)
            Result.failure(e)
        }
    }

    /**
     * Add multiple geofences
     */
    private suspend fun addGeofences(configs: List<GeofenceConfig>): Result<Int> = withContext(Dispatchers.IO) {
        if (!hasLocationPermissions()) {
            return@withContext Result.failure(Exception("Location permissions not granted"))
        }

        return@withContext suspendCoroutine { continuation ->
            try {
                val geofences = configs.map { config ->
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

                geofencingClient.addGeofences(geofenceRequest, getGeofencePendingIntent())
                    .addOnSuccessListener {
                        Log.d(TAG, "Geofences added successfully")
                        continuation.resume(Result.success(geofences.size))
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to add geofences", exception)
                        continuation.resume(Result.failure(exception))
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding geofences", e)
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * Remove all geofences
     */
    suspend fun removeAllGeofences(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext suspendCoroutine { continuation ->
            try {
                geofencingClient.removeGeofences(getGeofencePendingIntent())
                    .addOnSuccessListener {
                        Log.d(TAG, "All geofences removed successfully")
                        continuation.resume(Result.success(Unit))
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to remove geofences", exception)
                        continuation.resume(Result.failure(exception))
                    }
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
                geofencingClient.removeGeofences(geofenceIds)
                    .addOnSuccessListener {
                        Log.d(TAG, "Geofences removed: ${geofenceIds.joinToString()}")
                        continuation.resume(Result.success(Unit))
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to remove geofences: ${geofenceIds.joinToString()}", exception)
                        continuation.resume(Result.failure(exception))
                    }
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
            if (geofencingEvent.hasError()) {
                Log.e(TAG, "Geofence error: ${geofencingEvent.errorCode}")
                return
            }

            val geofenceTransition = geofencingEvent.geofenceTransition
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            Log.d(TAG, "Geofence transition detected: $geofenceTransition")
            Log.d(TAG, "Triggering geofences: ${triggeringGeofences?.map { it.requestId }}")

            if (!isValidTransition(geofenceTransition)) {
                Log.w(TAG, "Invalid geofence transition: $geofenceTransition")
                return
            }

            triggeringGeofences?.forEach { geofence ->
                val geofenceId = geofence.requestId
                Log.d(TAG, "Processing geofence trigger: $geofenceId (transition: $geofenceTransition)")

                // Find and execute matching workflows using coroutines
                CoroutineScope(Dispatchers.IO).launch {
                    val matchingWorkflows = findWorkflowsForGeofence(geofenceId, geofenceTransition)
                    matchingWorkflows.forEach { (workflow, trigger) ->
                        executeGeofenceWorkflow(workflow, trigger, geofenceTransition, geofenceId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling geofence transition", e)
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

            Log.i(TAG, "Executing geofence workflow: ${workflow.name}")
            Log.i(TAG, "Trigger: User $transitionName $locationName")

            val result = workflowEngine.executeWorkflow(
                workflowId = workflow.id,
                triggerUserId = triggerUserId,
                triggerData = triggerData
            )

            result.fold(
                onSuccess = { executionResult ->
                    Log.i(TAG, "Geofence workflow executed successfully: ${executionResult.message}")
                },
                onFailure = { error ->
                    Log.e(TAG, "Geofence workflow execution failed: ${error.message}")
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
}