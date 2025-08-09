package com.localllm.myapplication.data

import android.graphics.Bitmap
import java.util.UUID

/**
 * Data class representing a background processing task
 * Following Single Responsibility Principle - only holds task data
 */
data class BackgroundTask(
    val id: String = UUID.randomUUID().toString(),
    val type: TaskType,
    val prompt: String,
    val images: List<String> = emptyList(), // File paths to images
    val priority: Priority = Priority.NORMAL,
    val scheduledTime: Long = System.currentTimeMillis(),
    val maxRetries: Int = 3,
    val currentRetries: Int = 0,
    val status: TaskStatus = TaskStatus.PENDING,
    val sessionId: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        fun createChatTask(
            prompt: String, 
            images: List<String> = emptyList(),
            sessionId: String? = null,
            priority: Priority = Priority.NORMAL
        ): BackgroundTask {
            return BackgroundTask(
                type = TaskType.CHAT_GENERATION,
                prompt = prompt,
                images = images,
                sessionId = sessionId,
                priority = priority
            )
        }
        
        fun createAnalysisTask(
            prompt: String,
            images: List<String>,
            priority: Priority = Priority.HIGH
        ): BackgroundTask {
            return BackgroundTask(
                type = TaskType.IMAGE_ANALYSIS,
                prompt = prompt,
                images = images,
                priority = priority
            )
        }
        
        fun createScheduledTask(
            prompt: String,
            scheduledTime: Long,
            priority: Priority = Priority.LOW
        ): BackgroundTask {
            return BackgroundTask(
                type = TaskType.SCHEDULED_GENERATION,
                prompt = prompt,
                scheduledTime = scheduledTime,
                priority = priority
            )
        }
    }
}

enum class TaskType {
    CHAT_GENERATION,
    IMAGE_ANALYSIS,
    SCHEDULED_GENERATION,
    NOTIFICATION_RESPONSE,
    BATCH_PROCESSING
}

enum class Priority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

enum class TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    RETRY
}