package com.localllm.myapplication.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.localllm.myapplication.command.BackgroundCommand

class BackgroundServiceManager(private val context: Context) {

    companion object {
        private const val TAG = "BackgroundServiceManager"
    }

    private var backgroundService: BackgroundService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "ServiceConnection.onServiceConnected called")
            val binder = service as? BackgroundService.LocalBinder
            backgroundService = binder?.getService()
            isServiceBound = true
            Log.d(TAG, "Service connected successfully - isServiceBound: $isServiceBound, service: ${backgroundService != null}")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "ServiceConnection.onServiceDisconnected called")
            backgroundService = null
            isServiceBound = false
            Log.d(TAG, "Service disconnected")
        }
    }

    fun startBackgroundService() {
        try {
            Log.d(TAG, "Starting background service...")
            BackgroundService.startService(context)
            bindToService()
            Log.d(TAG, "Background service start initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start background service", e)
        }
    }

    fun stopBackgroundService() {
        try {
            unbindFromService()
            BackgroundService.stopService(context)
            Log.d(TAG, "Background service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop background service", e)
        }
    }

    fun executeBackgroundCommand(command: BackgroundCommand) {
        Log.d(TAG, "Attempting to execute command: ${command.getExecutionTag()}")
        Log.d(TAG, "Service bound: $isServiceBound, Service instance: ${backgroundService != null}")
        
        if (isServiceBound && backgroundService != null) {
            backgroundService?.executeCommand(command)
            Log.d(TAG, "Command executed via bound service: ${command.getExecutionTag()}")
        } else {
            Log.w(TAG, "Service not bound, cannot execute command: ${command.getExecutionTag()}")
            Log.w(TAG, "Try restarting the app or check service permissions")
        }
    }

    fun isServiceRunning(): Boolean = isServiceBound && backgroundService != null

    private fun bindToService() {
        try {
            Log.d(TAG, "Attempting to bind to service...")
            val intent = Intent(context, BackgroundService::class.java)
            val bindResult = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "Bind service result: $bindResult")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind to service", e)
        }
    }

    private fun unbindFromService() {
        if (isServiceBound) {
            context.unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}