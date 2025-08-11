package com.localllm.myapplication.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.localllm.myapplication.R

class ModelLoadingProgressService : Service() {
    
    companion object {
        private const val TAG = "ModelLoadingProgress"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "model_loading_channel"
        
        fun startLoading(context: Context, modelName: String) {
            val intent = Intent(context, ModelLoadingProgressService::class.java)
            intent.putExtra("model_name", modelName)
            intent.putExtra("action", "start")
            context.startForegroundService(intent)
        }
        
        fun updateProgress(context: Context, progress: Int, status: String) {
            val intent = Intent(context, ModelLoadingProgressService::class.java)
            intent.putExtra("progress", progress)
            intent.putExtra("status", status)
            intent.putExtra("action", "update")
            context.startService(intent)
        }
        
        fun stopLoading(context: Context) {
            val intent = Intent(context, ModelLoadingProgressService::class.java)
            intent.putExtra("action", "stop")
            context.startService(intent)
        }
    }
    
    private lateinit var notificationManager: NotificationManager
    private var modelName: String = "Model"
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ModelLoadingProgressService created")
        
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action") ?: "unknown"
        
        when (action) {
            "start" -> {
                modelName = intent?.getStringExtra("model_name") ?: "Model"
                Log.d(TAG, "Starting model loading progress for: $modelName")
                startForeground(NOTIFICATION_ID, createProgressNotification(0, "Starting model loading..."))
            }
            "update" -> {
                val progress = intent?.getIntExtra("progress", 0) ?: 0
                val status = intent?.getStringExtra("status") ?: "Loading..."
                Log.d(TAG, "Updating progress: $progress% - $status")
                notificationManager.notify(NOTIFICATION_ID, createProgressNotification(progress, status))
            }
            "stop" -> {
                Log.d(TAG, "Stopping model loading progress service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Loading Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress when loading AI models"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createProgressNotification(progress: Int, status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Loading $modelName")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ModelLoadingProgressService destroyed")
    }
}