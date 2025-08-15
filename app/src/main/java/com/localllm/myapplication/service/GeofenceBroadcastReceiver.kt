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
        Log.d(TAG, "Received geofence broadcast")
        
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.e(TAG, "Geofencing event is null")
            return
        }

        // Use coroutine to handle the event asynchronously
        scope.launch {
            try {
                // Get geofencing service from AppContainer
                val workflowRepository = AppContainer.provideWorkflowRepository(context)
                val workflowEngine = AppContainer.provideWorkflowEngine(context)
                val geofencingService = GeofencingService(context, workflowRepository, workflowEngine)
                
                // Handle the geofence transition
                geofencingService.handleGeofenceTransition(geofencingEvent)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error handling geofence event", e)
            }
        }
    }
}