package com.localllm.myapplication.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.GeofencingEvent
import com.localllm.myapplication.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Broadcast receiver for handling geofence transition events
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "GeofenceBroadcastReceiver"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "🌍 === LOCATION TRIGGER DETECTED ===")
        Log.i(TAG, "📡 Received geofence broadcast at ${System.currentTimeMillis()}")
        
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.e(TAG, "❌ Geofencing event is null - location trigger failed")
            return
        }

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "❌ Geofencing event has error: ${geofencingEvent.errorCode}")
            return
        }

        val transition = geofencingEvent.geofenceTransition
        val transitionName = when (transition) {
            com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTERED"
            com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT -> "EXITED" 
            com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELLING"
            else -> "UNKNOWN($transition)"
        }
        
        val triggeringGeofences = geofencingEvent.triggeringGeofences
        Log.i(TAG, "📍 Location Transition: $transitionName")
        Log.i(TAG, "🎯 Triggering Geofences: ${triggeringGeofences?.size ?: 0}")
        
        triggeringGeofences?.forEach { geofence ->
            Log.i(TAG, "   └── Geofence ID: ${geofence.requestId}")
        }

        // Use coroutine to handle the event asynchronously
        scope.launch {
            try {
                Log.d(TAG, "🔄 Processing geofence event asynchronously...")
                
                // Get geofencing service from AppContainer
                val workflowRepository = AppContainer.provideWorkflowRepository(context)
                val workflowEngine = AppContainer.provideWorkflowEngine(context)
                val geofencingService = GeofencingService(context, workflowRepository, workflowEngine)
                
                // Handle the geofence transition
                geofencingService.handleGeofenceTransition(geofencingEvent)
                
                Log.i(TAG, "✅ Geofence event processing completed")
                
            } catch (e: Exception) {
                Log.e(TAG, "💥 Error handling geofence event: ${e.message}", e)
            }
        }
    }
}