package com.localllm.myapplication.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log

/**
 * Service connection for workflow-related services
 */
class WorkflowServiceConnection : ServiceConnection {
    
    companion object {
        private const val TAG = "WorkflowServiceConnection"
    }
    
    var isConnected: Boolean = false
        private set
    
    private var serviceCallbacks: MutableList<(Boolean) -> Unit> = mutableListOf()
    
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        Log.d(TAG, "Service connected: $name")
        isConnected = true
        notifyCallbacks(true)
    }
    
    override fun onServiceDisconnected(name: ComponentName?) {
        Log.d(TAG, "Service disconnected: $name")
        isConnected = false
        notifyCallbacks(false)
    }
    
    fun addConnectionCallback(callback: (Boolean) -> Unit) {
        serviceCallbacks.add(callback)
    }
    
    fun removeConnectionCallback(callback: (Boolean) -> Unit) {
        serviceCallbacks.remove(callback)
    }
    
    private fun notifyCallbacks(connected: Boolean) {
        serviceCallbacks.forEach { callback ->
            try {
                callback(connected)
            } catch (e: Exception) {
                Log.e(TAG, "Error in connection callback", e)
            }
        }
    }
}