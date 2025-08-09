package com.localllm.myapplication.scheduler

import android.content.Context
import android.util.Log
import androidx.work.*
import com.localllm.myapplication.data.*
import com.localllm.myapplication.worker.BackgroundTaskWorker
import com.localllm.myapplication.notification.BackgroundNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Manages scheduling and execution of background tasks using WorkManager
 * Following Single Responsibility Principle - only handles task scheduling
 * Following Command Pattern - schedules task execution commands
 */
class BackgroundTaskScheduler(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "BackgroundTaskScheduler"
        private const val PERIODIC_WORK_TAG = "periodic_background_processor"
        private const val IMMEDIATE_WORK_TAG = "immediate_background_processor"
        private const val SCHEDULED_WORK_PREFIX = "scheduled_task_"
        
        // Work intervals
        private const val PERIODIC_INTERVAL_MINUTES = 60L // Reduced to 1 hour to avoid aggressive foreground service usage
        private const val IMMEDIATE_DELAY_SECONDS = 10L
    }
    
    private val workManager = WorkManager.getInstance(context)
    private val taskRepository: BackgroundTaskRepository = BackgroundTaskRepositoryImpl(context)
    private val notificationManager = BackgroundNotificationManager(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    init {
        // Start monitoring pending tasks
        startTaskMonitoring()
        
        // Schedule periodic task processing (optional - can be disabled)
        try {
            schedulePeriodicTaskProcessing()
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule periodic task processing", e)
        }
    }
    
    /**
     * Start monitoring for new tasks and schedule them automatically
     */
    private fun startTaskMonitoring() {
        scope.launch {
            taskRepository.getPendingTasksFlow().collectLatest { pendingTasks ->
                Log.d(TAG, "Found ${pendingTasks.size} pending tasks")
                
                pendingTasks.forEach { task ->
                    when {
                        // Immediate tasks
                        task.priority == Priority.URGENT || task.scheduledTime <= System.currentTimeMillis() -> {
                            scheduleImmediateTask(task)
                        }
                        
                        // Scheduled tasks
                        task.scheduledTime > System.currentTimeMillis() -> {
                            scheduleDelayedTask(task)
                        }
                        
                        // Regular priority tasks - will be picked up by periodic processor
                        else -> {
                            Log.d(TAG, "Task ${task.id} will be processed by periodic worker")
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Schedule periodic background task processing
     * Uses more relaxed constraints to avoid foreground service issues
     */
    private fun schedulePeriodicTaskProcessing() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // Allow offline processing
            .setRequiresBatteryNotLow(false) // Allow when battery is low
            .setRequiresCharging(false) // Don't require charging
            .setRequiresDeviceIdle(false) // Don't require device to be idle
            .build()
        
        val periodicWorkRequest = PeriodicWorkRequestBuilder<BackgroundTaskWorker>(
            PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(PERIODIC_WORK_TAG)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_TAG,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, // Cancel existing and start fresh
            periodicWorkRequest
        )
        
        Log.d(TAG, "Periodic task processing scheduled (every $PERIODIC_INTERVAL_MINUTES minutes)")
    }
    
    /**
     * Schedule an immediate task for processing
     */
    fun scheduleImmediateTask(task: BackgroundTask) {
        try {
            val inputData = Data.Builder()
                .putString(BackgroundTaskWorker.KEY_TASK_ID, task.id)
                .putString(BackgroundTaskWorker.KEY_TASK_TYPE, task.type.name)
                .putString(BackgroundTaskWorker.KEY_PROMPT, task.prompt)
                .putString(BackgroundTaskWorker.KEY_PRIORITY, task.priority.name)
                .build()
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val workRequest = OneTimeWorkRequestBuilder<BackgroundTaskWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .setInitialDelay(IMMEDIATE_DELAY_SECONDS, TimeUnit.SECONDS)
                .addTag(IMMEDIATE_WORK_TAG)
                .addTag(task.id)
                .build()
            
            workManager.enqueueUniqueWork(
                "immediate_${task.id}",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            
            // Show notification that task is scheduled
            notificationManager.showTaskProcessingNotification(
                taskId = task.id,
                taskType = task.type.name,
                prompt = task.prompt.take(50) + if (task.prompt.length > 50) "..." else ""
            )
            
            Log.d(TAG, "Immediate task scheduled: ${task.id}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule immediate task: ${task.id}", e)
        }
    }
    
    /**
     * Schedule a delayed task for future processing
     */
    fun scheduleDelayedTask(task: BackgroundTask) {
        try {
            val delay = task.scheduledTime - System.currentTimeMillis()
            if (delay <= 0) {
                // Task is overdue, schedule immediately
                scheduleImmediateTask(task)
                return
            }
            
            val inputData = Data.Builder()
                .putString(BackgroundTaskWorker.KEY_TASK_ID, task.id)
                .putString(BackgroundTaskWorker.KEY_TASK_TYPE, task.type.name)
                .putString(BackgroundTaskWorker.KEY_PROMPT, task.prompt)
                .putString(BackgroundTaskWorker.KEY_PRIORITY, task.priority.name)
                .build()
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val workRequest = OneTimeWorkRequestBuilder<BackgroundTaskWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag("${SCHEDULED_WORK_PREFIX}${task.id}")
                .build()
            
            workManager.enqueueUniqueWork(
                "${SCHEDULED_WORK_PREFIX}${task.id}",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            
            // Show notification about scheduled task
            notificationManager.showScheduledTaskNotification(
                prompt = task.prompt,
                scheduledTime = task.scheduledTime
            )
            
            Log.d(TAG, "Delayed task scheduled: ${task.id} (delay: ${delay}ms)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule delayed task: ${task.id}", e)
        }
    }
    
    /**
     * Add a new background task and schedule it
     */
    suspend fun addAndScheduleTask(task: BackgroundTask): Result<Unit> {
        return try {
            // Add task to repository
            val addResult = taskRepository.addTask(task)
            
            addResult.fold(
                onSuccess = {
                    // Schedule the task based on its properties
                    when {
                        task.priority == Priority.URGENT -> {
                            scheduleImmediateTask(task)
                        }
                        task.scheduledTime > System.currentTimeMillis() -> {
                            scheduleDelayedTask(task)
                        }
                        else -> {
                            // Will be picked up by periodic processor
                            Log.d(TAG, "Task ${task.id} added to queue for periodic processing")
                        }
                    }
                    
                    Log.d(TAG, "Task added and scheduled: ${task.id}")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to add task to repository", error)
                    Result.failure(error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add and schedule task", e)
            Result.failure(e)
        }
    }
    
    /**
     * Cancel a scheduled task
     */
    suspend fun cancelTask(taskId: String): Result<Unit> {
        return try {
            // Cancel WorkManager tasks
            workManager.cancelUniqueWork("immediate_$taskId")
            workManager.cancelUniqueWork("${SCHEDULED_WORK_PREFIX}$taskId")
            workManager.cancelAllWorkByTag(taskId)
            
            // Update task status in repository
            val cancelResult = taskRepository.cancelTask(taskId)
            
            cancelResult.fold(
                onSuccess = {
                    Log.d(TAG, "Task cancelled: $taskId")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to cancel task in repository", error)
                    Result.failure(error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel task", e)
            Result.failure(e)
        }
    }
    
    /**
     * Schedule a chat task for background processing
     */
    suspend fun scheduleChatTask(
        prompt: String,
        images: List<String> = emptyList(),
        priority: Priority = Priority.NORMAL,
        sessionId: String? = null
    ): Result<String> {
        return try {
            val task = BackgroundTask.createChatTask(
                prompt = prompt,
                images = images,
                sessionId = sessionId,
                priority = priority
            )
            
            val result = addAndScheduleTask(task)
            result.fold(
                onSuccess = {
                    Result.success(task.id)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule chat task", e)
            Result.failure(e)
        }
    }
    
    /**
     * Schedule an image analysis task
     */
    suspend fun scheduleImageAnalysisTask(
        prompt: String,
        images: List<String>,
        priority: Priority = Priority.HIGH
    ): Result<String> {
        return try {
            val task = BackgroundTask.createAnalysisTask(
                prompt = prompt,
                images = images,
                priority = priority
            )
            
            val result = addAndScheduleTask(task)
            result.fold(
                onSuccess = {
                    Result.success(task.id)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule image analysis task", e)
            Result.failure(e)
        }
    }
    
    /**
     * Schedule a task for a specific time
     */
    suspend fun scheduleTaskForTime(
        prompt: String,
        scheduledTime: Long,
        priority: Priority = Priority.NORMAL
    ): Result<String> {
        return try {
            val task = BackgroundTask.createScheduledTask(
                prompt = prompt,
                scheduledTime = scheduledTime,
                priority = priority
            )
            
            val result = addAndScheduleTask(task)
            result.fold(
                onSuccess = {
                    Result.success(task.id)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule timed task", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get task execution status
     */
    suspend fun getTaskStatus(taskId: String): Result<TaskStatus?> {
        return try {
            val tasks = taskRepository.getAllTasks()
            tasks.fold(
                onSuccess = { taskList ->
                    val task = taskList.find { it.id == taskId }
                    Result.success(task?.status)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get task status", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all pending tasks
     */
    suspend fun getPendingTasks(): Result<List<BackgroundTask>> {
        return taskRepository.getPendingTasks()
    }
    
    /**
     * Clear completed tasks to free up storage
     */
    suspend fun clearCompletedTasks(): Result<Unit> {
        return taskRepository.clearCompletedTasks()
    }
    
    /**
     * Get work info for debugging
     */
    fun getWorkInfo(): List<WorkInfo> {
        return try {
            val workInfos = mutableListOf<WorkInfo>()
            
            // Get periodic work info
            val periodicWork = workManager.getWorkInfosByTag(PERIODIC_WORK_TAG).get()
            workInfos.addAll(periodicWork)
            
            // Get immediate work info
            val immediateWork = workManager.getWorkInfosByTag(IMMEDIATE_WORK_TAG).get()
            workInfos.addAll(immediateWork)
            
            workInfos
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get work info", e)
            emptyList()
        }
    }
    
    /**
     * Cleanup and stop all background processing
     */
    fun cleanup() {
        try {
            // Cancel all work
            workManager.cancelAllWorkByTag(PERIODIC_WORK_TAG)
            workManager.cancelAllWorkByTag(IMMEDIATE_WORK_TAG)
            workManager.cancelAllWork()
            
            // Clear notifications
            notificationManager.clearAllNotifications()
            
            Log.d(TAG, "Background task scheduler cleaned up")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}