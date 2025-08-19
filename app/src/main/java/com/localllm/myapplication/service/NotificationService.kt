package com.localllm.myapplication.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.localllm.myapplication.R

class NotificationService(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID = "workflow_channel"
        private const val CHANNEL_NAME = "Workflow Results"
        private const val CHANNEL_DESCRIPTION = "Notifications for workflow execution results"
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }
            
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showWorkflowNotification(title: String, message: String, notificationId: Int = 1) {
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
            
            // Try to use a default icon, fallback to system icon if app icon not available
            try {
                builder.setSmallIcon(R.drawable.ic_notification)
            } catch (e: Exception) {
                builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            }
            
            with(NotificationManagerCompat.from(context)) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) 
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    notify(notificationId, builder.build())
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NotificationService", "Failed to show notification", e)
        }
    }
}