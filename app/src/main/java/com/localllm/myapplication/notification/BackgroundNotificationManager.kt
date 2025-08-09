package com.localllm.myapplication.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.localllm.myapplication.R
import com.localllm.myapplication.ui.ChatActivity

/**
 * Manages notifications for background task processing
 * Following Single Responsibility Principle - only handles background task notifications
 */
class BackgroundNotificationManager(private val context: Context) {

    companion object {
        private const val TAG = "BackgroundNotificationMgr"
        const val CHANNEL_BACKGROUND_TASKS = "background_tasks"
        const val CHANNEL_TASK_RESULTS = "task_results"
        
        private const val NOTIFICATION_ID_PROCESSING = 3001
        private const val NOTIFICATION_ID_COMPLETED = 3002
        private const val NOTIFICATION_ID_FAILED = 3003
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    /**
     * Create notification channels for different types of background notifications
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Background tasks channel
            val backgroundTasksChannel = NotificationChannel(
                CHANNEL_BACKGROUND_TASKS,
                "Background AI Tasks",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for ongoing background AI processing"
                setShowBadge(false)
            }

            // Task results channel
            val taskResultsChannel = NotificationChannel(
                CHANNEL_TASK_RESULTS,
                "AI Task Results",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for completed background AI tasks"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(backgroundTasksChannel)
            notificationManager.createNotificationChannel(taskResultsChannel)
            
            Log.d(TAG, "Notification channels created")
        }
    }

    /**
     * Show notification when a background task starts processing
     */
    fun showTaskProcessingNotification(taskId: String, taskType: String, prompt: String) {
        try {
            val intent = Intent(context, ChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                intent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_BACKGROUND_TASKS)
                .setContentTitle("Processing AI Task")
                .setContentText("$taskType: $prompt")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setProgress(0, 0, true)
                .addAction(
                    R.drawable.ic_notification,
                    "Open Chat",
                    pendingIntent
                )
                .build()

            notificationManager.notify(NOTIFICATION_ID_PROCESSING, notification)
            Log.d(TAG, "Processing notification shown for task: $taskId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show processing notification", e)
        }
    }

    /**
     * Show notification when a background task completes successfully
     */
    fun showTaskCompletedNotification(taskId: String, result: String) {
        try {
            // Cancel processing notification
            notificationManager.cancel(NOTIFICATION_ID_PROCESSING)

            val intent = Intent(context, ChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("show_result", true)
                putExtra("task_result", result)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 
                1, 
                intent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_TASK_RESULTS)
                .setContentTitle("✅ AI Task Completed")
                .setContentText(result)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(result)
                        .setBigContentTitle("✅ AI Task Completed")
                )
                .addAction(
                    R.drawable.ic_notification,
                    "View Result",
                    pendingIntent
                )
                .build()

            notificationManager.notify(NOTIFICATION_ID_COMPLETED, notification)
            Log.d(TAG, "Completion notification shown for task: $taskId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show completion notification", e)
        }
    }

    /**
     * Show notification when a background task fails
     */
    fun showTaskFailedNotification(taskId: String, error: String) {
        try {
            // Cancel processing notification
            notificationManager.cancel(NOTIFICATION_ID_PROCESSING)

            val intent = Intent(context, ChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 
                2, 
                intent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_TASK_RESULTS)
                .setContentTitle("❌ AI Task Failed")
                .setContentText(error)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Task failed: $error")
                        .setBigContentTitle("❌ AI Task Failed")
                )
                .addAction(
                    R.drawable.ic_notification,
                    "Retry",
                    pendingIntent
                )
                .build()

            notificationManager.notify(NOTIFICATION_ID_FAILED, notification)
            Log.d(TAG, "Failure notification shown for task: $taskId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show failure notification", e)
        }
    }

    /**
     * Show notification for scheduled task trigger
     */
    fun showScheduledTaskNotification(prompt: String, scheduledTime: Long) {
        try {
            val intent = Intent(context, ChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("scheduled_prompt", prompt)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 
                3, 
                intent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val timeStr = android.text.format.DateFormat.format("HH:mm", scheduledTime)
            
            val notification = NotificationCompat.Builder(context, CHANNEL_TASK_RESULTS)
                .setContentTitle("⏰ Scheduled AI Task")
                .setContentText("Scheduled for $timeStr: $prompt")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .addAction(
                    R.drawable.ic_notification,
                    "Process Now",
                    pendingIntent
                )
                .build()

            notificationManager.notify(scheduledTime.toInt(), notification)
            Log.d(TAG, "Scheduled task notification shown")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show scheduled task notification", e)
        }
    }

    /**
     * Show notification with quick response options
     */
    fun showInteractiveNotification(prompt: String, quickResponses: List<String>) {
        try {
            val intent = Intent(context, ChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("quick_prompt", prompt)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 
                4, 
                intent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_TASK_RESULTS)
                .setContentTitle("🤖 AI Quick Response")
                .setContentText(prompt)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(prompt)
                        .setBigContentTitle("🤖 AI Quick Response")
                )

            // Add quick response actions
            quickResponses.take(3).forEachIndexed { index, response ->
                val responseIntent = Intent(context, ChatActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("auto_response", response)
                    putExtra("original_prompt", prompt)
                }
                val responsePendingIntent = PendingIntent.getActivity(
                    context, 
                    10 + index, 
                    responseIntent, 
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                
                notificationBuilder.addAction(
                    R.drawable.ic_notification,
                    response.take(20),
                    responsePendingIntent
                )
            }

            notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
            Log.d(TAG, "Interactive notification shown")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show interactive notification", e)
        }
    }

    /**
     * Clear all background task notifications
     */
    fun clearAllNotifications() {
        try {
            notificationManager.cancel(NOTIFICATION_ID_PROCESSING)
            notificationManager.cancel(NOTIFICATION_ID_COMPLETED)
            notificationManager.cancel(NOTIFICATION_ID_FAILED)
            Log.d(TAG, "All notifications cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear notifications", e)
        }
    }

    /**
     * Show progress notification for long-running tasks
     */
    fun showProgressNotification(taskId: String, progress: Int, maxProgress: Int, status: String) {
        try {
            val intent = Intent(context, ChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 
                5, 
                intent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_BACKGROUND_TASKS)
                .setContentTitle("AI Processing Progress")
                .setContentText(status)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setProgress(maxProgress, progress, false)
                .build()

            notificationManager.notify(NOTIFICATION_ID_PROCESSING, notification)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show progress notification", e)
        }
    }
}