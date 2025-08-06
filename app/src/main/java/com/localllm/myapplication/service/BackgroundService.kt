package com.localllm.myapplication.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.localllm.myapplication.R
import com.localllm.myapplication.command.BackgroundCommand
import com.localllm.myapplication.ui.MainActivity
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

class BackgroundService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "background_service_channel"
        private const val TAG = "BackgroundService"

        fun startService(context: Context) {
            val intent = Intent(context, BackgroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BackgroundService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val commandQueue = ConcurrentLinkedQueue<BackgroundCommand>()
    private var isProcessingCommands = false
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): BackgroundService = this@BackgroundService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Background service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            startCommandProcessor()
            Log.d(TAG, "Background service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "Background service destroyed")
    }

    fun executeCommand(command: BackgroundCommand) {
        if (command.canExecuteInBackground()) {
            commandQueue.offer(command)
            Log.d(TAG, "Command queued: ${command.getExecutionTag()}")
        } else {
            Log.w(TAG, "Command cannot execute in background: ${command.getExecutionTag()}")
        }
    }

    private fun startCommandProcessor() {
        if (isProcessingCommands) return
        
        isProcessingCommands = true
        serviceScope.launch {
            while (isActive) {
                val command = commandQueue.poll()
                if (command != null) {
                    processCommand(command)
                } else {
                    delay(1000) // Wait for new commands
                }
            }
        }
    }

    private suspend fun processCommand(command: BackgroundCommand) {
        try {
            Log.d(TAG, "Executing command: ${command.getExecutionTag()}")
            withContext(Dispatchers.IO) {
                command.execute()
            }
            command.onExecutionComplete()
            Log.d(TAG, "Command completed: ${command.getExecutionTag()}")
        } catch (e: Exception) {
            Log.e(TAG, "Command failed: ${command.getExecutionTag()}", e)
            command.onExecutionFailed(e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Handles background workflows"
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("MyApp Background Service")
        .setContentText("Running background workflows")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setOngoing(true)
        .build()
}