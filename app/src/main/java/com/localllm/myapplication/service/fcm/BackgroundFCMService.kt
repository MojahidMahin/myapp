package com.localllm.myapplication.service.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.localllm.myapplication.data.*
import com.localllm.myapplication.scheduler.BackgroundTaskScheduler
import com.localllm.myapplication.notification.BackgroundNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging service for triggering background LLM processing
 * Following Single Responsibility Principle - only handles FCM message processing
 * Following Command Pattern - converts FCM messages to background tasks
 */
class BackgroundFCMService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "BackgroundFCMService"
        
        // FCM message types
        const val TYPE_CHAT_TASK = "chat_task"
        const val TYPE_IMAGE_ANALYSIS = "image_analysis"
        const val TYPE_SCHEDULED_TASK = "scheduled_task"
        const val TYPE_NOTIFICATION_RESPONSE = "notification_response"
        const val TYPE_BATCH_PROCESSING = "batch_processing"
        
        // FCM message keys
        const val KEY_TYPE = "type"
        const val KEY_PROMPT = "prompt"
        const val KEY_PRIORITY = "priority"
        const val KEY_IMAGES = "images"
        const val KEY_SCHEDULED_TIME = "scheduled_time"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_BATCH_PROMPTS = "batch_prompts"
        const val KEY_QUICK_RESPONSES = "quick_responses"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var taskScheduler: BackgroundTaskScheduler
    private lateinit var notificationManager: BackgroundNotificationManager
    
    override fun onCreate() {
        super.onCreate()
        taskScheduler = BackgroundTaskScheduler(this)
        notificationManager = BackgroundNotificationManager(this)
        Log.d(TAG, "BackgroundFCMService created")
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")
        
        // Handle notification payload
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "FCM notification: ${notification.title} - ${notification.body}")
        }
        
        // Handle data payload for background processing
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "FCM data payload: ${remoteMessage.data}")
            processBackgroundTask(remoteMessage.data)
        }
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        
        // Send token to your server if needed
        // sendTokenToServer(token)
    }
    
    /**
     * Process FCM data payload and create background tasks
     */
    private fun processBackgroundTask(data: Map<String, String>) {
        scope.launch {
            try {
                val type = data[KEY_TYPE] ?: return@launch
                val prompt = data[KEY_PROMPT] ?: return@launch
                val priorityStr = data[KEY_PRIORITY] ?: Priority.NORMAL.name
                val priority = try {
                    Priority.valueOf(priorityStr.uppercase())
                } catch (e: Exception) {
                    Priority.NORMAL
                }
                
                Log.d(TAG, "Processing FCM background task: $type")
                
                when (type) {
                    TYPE_CHAT_TASK -> {
                        processChatTask(data, prompt, priority)
                    }
                    
                    TYPE_IMAGE_ANALYSIS -> {
                        processImageAnalysisTask(data, prompt, priority)
                    }
                    
                    TYPE_SCHEDULED_TASK -> {
                        processScheduledTask(data, prompt, priority)
                    }
                    
                    TYPE_NOTIFICATION_RESPONSE -> {
                        processNotificationResponse(data, prompt, priority)
                    }
                    
                    TYPE_BATCH_PROCESSING -> {
                        processBatchProcessing(data, prompt, priority)
                    }
                    
                    else -> {
                        Log.w(TAG, "Unknown FCM task type: $type")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing FCM background task", e)
            }
        }
    }
    
    /**
     * Process chat task from FCM
     */
    private suspend fun processChatTask(data: Map<String, String>, prompt: String, priority: Priority) {
        try {
            val sessionId = data[KEY_SESSION_ID]
            val imagesStr = data[KEY_IMAGES]
            val images = if (imagesStr.isNullOrEmpty()) {
                emptyList()
            } else {
                imagesStr.split(",").map { it.trim() }
            }
            
            val result = taskScheduler.scheduleChatTask(
                prompt = prompt,
                images = images,
                priority = priority,
                sessionId = sessionId
            )
            
            result.fold(
                onSuccess = { taskId ->
                    Log.d(TAG, "FCM chat task scheduled: $taskId")
                    
                    // Show notification
                    notificationManager.showTaskProcessingNotification(
                        taskId = taskId,
                        taskType = "Chat Task",
                        prompt = prompt.take(50) + if (prompt.length > 50) "..." else ""
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to schedule FCM chat task", error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing FCM chat task", e)
        }
    }
    
    /**
     * Process image analysis task from FCM
     */
    private suspend fun processImageAnalysisTask(data: Map<String, String>, prompt: String, priority: Priority) {
        try {
            val imagesStr = data[KEY_IMAGES] ?: ""
            val images = if (imagesStr.isEmpty()) {
                emptyList()
            } else {
                imagesStr.split(",").map { it.trim() }
            }
            
            if (images.isEmpty()) {
                Log.w(TAG, "No images provided for image analysis task")
                return
            }
            
            val result = taskScheduler.scheduleImageAnalysisTask(
                prompt = prompt,
                images = images,
                priority = priority
            )
            
            result.fold(
                onSuccess = { taskId ->
                    Log.d(TAG, "FCM image analysis task scheduled: $taskId")
                    
                    // Show notification
                    notificationManager.showTaskProcessingNotification(
                        taskId = taskId,
                        taskType = "Image Analysis",
                        prompt = prompt.take(50) + if (prompt.length > 50) "..." else ""
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to schedule FCM image analysis task", error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing FCM image analysis task", e)
        }
    }
    
    /**
     * Process scheduled task from FCM
     */
    private suspend fun processScheduledTask(data: Map<String, String>, prompt: String, priority: Priority) {
        try {
            val scheduledTimeStr = data[KEY_SCHEDULED_TIME] ?: System.currentTimeMillis().toString()
            val scheduledTime = try {
                scheduledTimeStr.toLong()
            } catch (e: Exception) {
                System.currentTimeMillis() + 300000 // Default to 5 minutes from now
            }
            
            val result = taskScheduler.scheduleTaskForTime(
                prompt = prompt,
                scheduledTime = scheduledTime,
                priority = priority
            )
            
            result.fold(
                onSuccess = { taskId ->
                    Log.d(TAG, "FCM scheduled task scheduled: $taskId")
                    
                    // Show scheduled notification
                    notificationManager.showScheduledTaskNotification(
                        prompt = prompt,
                        scheduledTime = scheduledTime
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to schedule FCM scheduled task", error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing FCM scheduled task", e)
        }
    }
    
    /**
     * Process notification response task from FCM
     */
    private suspend fun processNotificationResponse(data: Map<String, String>, prompt: String, priority: Priority) {
        try {
            val quickResponsesStr = data[KEY_QUICK_RESPONSES]
            val quickResponses = if (quickResponsesStr.isNullOrEmpty()) {
                listOf("Yes", "No", "Maybe")
            } else {
                quickResponsesStr.split(",").map { it.trim() }
            }
            
            // Show interactive notification first
            notificationManager.showInteractiveNotification(
                prompt = prompt,
                quickResponses = quickResponses
            )
            
            // Create background task for processing
            val task = BackgroundTask(
                type = TaskType.NOTIFICATION_RESPONSE,
                prompt = prompt,
                priority = priority,
                metadata = mapOf("quick_responses" to quickResponsesStr.orEmpty())
            )
            
            val result = taskScheduler.addAndScheduleTask(task)
            
            result.fold(
                onSuccess = {
                    Log.d(TAG, "FCM notification response task scheduled: ${task.id}")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to schedule FCM notification response task", error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing FCM notification response task", e)
        }
    }
    
    /**
     * Process batch processing task from FCM
     */
    private suspend fun processBatchProcessing(data: Map<String, String>, prompt: String, priority: Priority) {
        try {
            val batchPromptsStr = data[KEY_BATCH_PROMPTS] ?: prompt
            val batchPrompts = batchPromptsStr.split("|").map { it.trim() }
            
            val task = BackgroundTask(
                type = TaskType.BATCH_PROCESSING,
                prompt = prompt,
                priority = priority,
                metadata = mapOf("batch_prompts" to batchPromptsStr)
            )
            
            val result = taskScheduler.addAndScheduleTask(task)
            
            result.fold(
                onSuccess = {
                    Log.d(TAG, "FCM batch processing task scheduled: ${task.id}")
                    
                    // Show notification
                    notificationManager.showTaskProcessingNotification(
                        taskId = task.id,
                        taskType = "Batch Processing",
                        prompt = "Processing ${batchPrompts.size} prompts"
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to schedule FCM batch processing task", error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing FCM batch processing task", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BackgroundFCMService destroyed")
    }
}