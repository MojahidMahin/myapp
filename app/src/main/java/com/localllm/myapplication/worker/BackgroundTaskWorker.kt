package com.localllm.myapplication.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.localllm.myapplication.R
import com.localllm.myapplication.data.*
import com.localllm.myapplication.service.ModelManager
import com.localllm.myapplication.notification.BackgroundNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker for processing background LLM tasks
 * Following Single Responsibility Principle - only handles background task processing
 * Following Command Pattern - executes background task commands
 */
class BackgroundTaskWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "BackgroundTaskWorker"
        const val KEY_TASK_ID = "task_id"
        const val KEY_TASK_TYPE = "task_type"
        const val KEY_PROMPT = "prompt"
        const val KEY_PRIORITY = "priority"
        const val NOTIFICATION_ID = 2001
    }

    private val taskRepository: BackgroundTaskRepository = BackgroundTaskRepositoryImpl(context)
    private val modelManager: ModelManager = ModelManager.getInstance(context)
    private val notificationManager: BackgroundNotificationManager = 
        BackgroundNotificationManager(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        return@withContext try {
            Log.d(TAG, "BackgroundTaskWorker starting...")

            val taskId = inputData.getString(KEY_TASK_ID)
            
            // Only use foreground service for immediate/specific tasks, not periodic ones
            val isImmediate = taskId != null
            
            if (isImmediate) {
                try {
                    // Set foreground info only for immediate tasks
                    setForeground(createForegroundInfo())
                    Log.d(TAG, "Foreground service started for immediate task")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not start foreground service, continuing without it", e)
                    // Continue without foreground service
                }
            }

            if (taskId != null) {
                // Process specific task
                processSpecificTask(taskId)
            } else {
                // Process next available task from queue (periodic check)
                processNextTask()
            }

        } catch (e: Exception) {
            Log.e(TAG, "BackgroundTaskWorker failed", e)
            Result.failure()
        }
    }

    /**
     * Process a specific task by ID
     */
    private suspend fun processSpecificTask(taskId: String): ListenableWorker.Result {
        return try {
            val allTasksResult = taskRepository.getAllTasks()
            allTasksResult.fold(
                onSuccess = { tasks ->
                    val task = tasks.find { it.id == taskId }
                    if (task != null) {
                        processTask(task)
                    } else {
                        Log.w(TAG, "Task not found: $taskId")
                        Result.failure()
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to retrieve task: $taskId", error)
                    Result.failure()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing specific task: $taskId", e)
            Result.failure()
        }
    }

    /**
     * Process the next available task from the queue
     */
    private suspend fun processNextTask(): ListenableWorker.Result {
        return try {
            val nextTaskResult = taskRepository.getNextTask()
            nextTaskResult.fold(
                onSuccess = { task ->
                    if (task != null) {
                        processTask(task)
                    } else {
                        Log.d(TAG, "No pending tasks to process")
                        Result.success()
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to get next task", error)
                    Result.failure()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing next task", e)
            Result.failure()
        }
    }

    /**
     * Process an individual background task
     */
    private suspend fun processTask(task: BackgroundTask): ListenableWorker.Result {
        return try {
            Log.d(TAG, "Processing task: ${task.id} (${task.type})")

            // Update task status to running
            taskRepository.updateTaskStatus(task.id, TaskStatus.RUNNING)

            // Show processing notification
            notificationManager.showTaskProcessingNotification(
                taskId = task.id,
                taskType = task.type.name,
                prompt = task.prompt.take(50) + if (task.prompt.length > 50) "..." else ""
            )

            val result = when (task.type) {
                TaskType.CHAT_GENERATION -> processChatGeneration(task)
                TaskType.IMAGE_ANALYSIS -> processImageAnalysis(task)
                TaskType.SCHEDULED_GENERATION -> processScheduledGeneration(task)
                TaskType.NOTIFICATION_RESPONSE -> processNotificationResponse(task)
                TaskType.BATCH_PROCESSING -> processBatchProcessing(task)
            }

            result.fold(
                onSuccess = { response ->
                    // Mark task as completed
                    taskRepository.markTaskCompleted(task.id, response)
                    
                    // Show completion notification
                    notificationManager.showTaskCompletedNotification(
                        taskId = task.id,
                        result = response.take(100) + if (response.length > 100) "..." else ""
                    )
                    
                    Log.d(TAG, "Task completed successfully: ${task.id}")
                    Result.success()
                },
                onFailure = { error ->
                    // Mark task as failed
                    taskRepository.markTaskFailed(task.id, error.message ?: "Unknown error")
                    
                    // Show error notification
                    notificationManager.showTaskFailedNotification(
                        taskId = task.id,
                        error = error.message ?: "Processing failed"
                    )
                    
                    Log.e(TAG, "Task failed: ${task.id}", error)
                    Result.failure()
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error processing task: ${task.id}", e)
            
            // Mark task as failed
            taskRepository.markTaskFailed(task.id, e.message ?: "Unknown error")
            
            Result.failure()
        }
    }

    /**
     * Process chat generation task
     */
    private suspend fun processChatGeneration(task: BackgroundTask): kotlin.Result<String> {
        return try {
            // Check if model is loaded
            if (!modelManager.isModelLoaded.value) {
                return kotlin.Result.failure(Exception("No model loaded"))
            }

            // Generate response using ModelManager
            var response = ""
            val generationResult = kotlin.Result.runCatching {
                modelManager.generateResponse(
                    prompt = task.prompt,
                    images = emptyList(), // TODO: Load images from file paths
                    onPartialResult = { partial ->
                        response += partial
                    },
                    onResult = { result ->
                        result.fold(
                            onSuccess = { finalResponse ->
                                response = finalResponse
                            },
                            onFailure = { error ->
                                throw error
                            }
                        )
                    }
                )
            }

            // Wait for completion (simplified approach)
            kotlinx.coroutines.delay(5000) // Allow time for generation
            
            if (response.isNotEmpty()) {
                kotlin.Result.success(response)
            } else {
                kotlin.Result.failure(Exception("No response generated"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Chat generation failed", e)
            kotlin.Result.failure(e)
        }
    }

    /**
     * Process image analysis task
     */
    private suspend fun processImageAnalysis(task: BackgroundTask): kotlin.Result<String> {
        return try {
            // Load images from file paths
            val images = task.images.mapNotNull { imagePath ->
                try {
                    // TODO: Load bitmap from file path
                    null // Placeholder
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load image: $imagePath", e)
                    null
                }
            }

            if (images.isEmpty() && task.images.isNotEmpty()) {
                return kotlin.Result.failure(Exception("Failed to load images"))
            }

            // Process with image analysis prompt
            processChatGeneration(task.copy(prompt = "Analyze the following image(s): ${task.prompt}"))

        } catch (e: Exception) {
            Log.e(TAG, "Image analysis failed", e)
            kotlin.Result.failure(e)
        }
    }

    /**
     * Process scheduled generation task
     */
    private suspend fun processScheduledGeneration(task: BackgroundTask): kotlin.Result<String> {
        return try {
            // Check if it's time to execute
            if (task.scheduledTime > System.currentTimeMillis()) {
                return kotlin.Result.failure(Exception("Task not yet scheduled to run"))
            }

            // Execute as regular chat generation
            processChatGeneration(task)

        } catch (e: Exception) {
            Log.e(TAG, "Scheduled generation failed", e)
            kotlin.Result.failure(e)
        }
    }

    /**
     * Process notification response task
     */
    private suspend fun processNotificationResponse(task: BackgroundTask): kotlin.Result<String> {
        return try {
            // Add context about notification response
            val contextualPrompt = "Responding to notification: ${task.prompt}"
            processChatGeneration(task.copy(prompt = contextualPrompt))

        } catch (e: Exception) {
            Log.e(TAG, "Notification response failed", e)
            kotlin.Result.failure(e)
        }
    }

    /**
     * Process batch processing task
     */
    private suspend fun processBatchProcessing(task: BackgroundTask): kotlin.Result<String> {
        return try {
            // Process multiple prompts in the metadata
            val prompts = task.metadata["batch_prompts"]?.split("|") ?: listOf(task.prompt)
            val responses = mutableListOf<String>()

            for ((index, prompt) in prompts.withIndex()) {
                Log.d(TAG, "Processing batch item ${index + 1}/${prompts.size}")
                
                val batchTask = task.copy(prompt = prompt)
                val result = processChatGeneration(batchTask)
                
                result.fold(
                    onSuccess = { response ->
                        responses.add("${index + 1}: $response")
                    },
                    onFailure = { error ->
                        responses.add("${index + 1}: ERROR - ${error.message}")
                    }
                )
            }

            kotlin.Result.success(responses.joinToString("\n\n"))

        } catch (e: Exception) {
            Log.e(TAG, "Batch processing failed", e)
            kotlin.Result.failure(e)
        }
    }

    /**
     * Create foreground info for the worker
     */
    private fun createForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, BackgroundNotificationManager.CHANNEL_BACKGROUND_TASKS)
            .setContentTitle("Processing AI Task")
            .setContentText("Working on background AI processing...")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID, 
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}