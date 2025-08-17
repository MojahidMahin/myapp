package com.localllm.myapplication.service

import android.content.Context
import android.util.Log
import com.localllm.myapplication.data.database.ProcessedTelegramMessageDao
import com.localllm.myapplication.data.database.ProcessedTelegramMessageEntity
import com.localllm.myapplication.data.database.WorkflowDatabase
import com.localllm.myapplication.service.integration.TelegramBotService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service for managing Telegram message deduplication to prevent duplicate workflow triggers
 */
class TelegramDeduplicationService(context: Context) {
    
    companion object {
        private const val TAG = "TelegramDeduplicationService"
        private const val MESSAGE_RETENTION_DAYS = 30 // Keep processed messages for 30 days
    }
    
    private val database = WorkflowDatabase.getDatabase(context)
    private val processedMessageDao: ProcessedTelegramMessageDao = database.processedTelegramMessageDao()
    
    /**
     * Check if a Telegram message has already been processed for a specific workflow
     */
    suspend fun isTelegramMessageProcessed(
        telegramMessageId: Long, 
        chatId: Long, 
        workflowId: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val processed = processedMessageDao.isSpecificMessageProcessed(telegramMessageId, chatId, workflowId)
                val result = processed != null
                Log.d(TAG, "Telegram message $telegramMessageId in chat $chatId for workflow $workflowId - Already processed: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if Telegram message is processed", e)
                false // Assume not processed if error occurs
            }
        }
    }
    
    /**
     * Mark a Telegram message as processed for a specific workflow
     */
    suspend fun markTelegramMessageAsProcessed(
        telegramMessageId: Long,
        chatId: Long,
        workflowId: String,
        userId: String,
        senderName: String,
        senderUsername: String?,
        messageText: String,
        messageTimestamp: Long
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val messageId = "${chatId}_${telegramMessageId}_${workflowId}" // Unique composite key
                
                Log.d(TAG, "💾 STEP D2: Marking message as processed")
                Log.d(TAG, "🆔 Composite ID: $messageId")
                Log.d(TAG, "📨 Telegram Message ID: $telegramMessageId")
                Log.d(TAG, "💬 Chat ID: $chatId")
                Log.d(TAG, "🔧 Workflow ID: $workflowId")
                Log.d(TAG, "👤 User ID: $userId")
                Log.d(TAG, "👤 Sender Name: $senderName")
                Log.d(TAG, "📝 Message Text: '$messageText'")
                
                val entity = ProcessedTelegramMessageEntity(
                    messageId = messageId,
                    telegramMessageId = telegramMessageId,
                    chatId = chatId,
                    workflowId = workflowId,
                    userId = userId,
                    senderName = senderName,
                    senderUsername = senderUsername,
                    messageText = messageText,
                    triggerTimestamp = messageTimestamp
                )
                
                processedMessageDao.insertProcessedMessage(entity)
                Log.i(TAG, "✅ STEP D2: Telegram message marked as processed: $messageId")
                true
            } catch (e: Exception) {
                Log.e(TAG, "❌ STEP D2: Failed to mark Telegram message as processed", e)
                Log.e(TAG, "💥 Error details: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Filter new messages from a list, removing already processed ones
     */
    suspend fun filterNewTelegramMessages(
        messages: List<TelegramBotService.TelegramMessage>, 
        workflowId: String
    ): List<TelegramBotService.TelegramMessage> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 STEP D1: Filtering ${messages.size} messages for workflow $workflowId")
                
                val newMessages = messages.filter { message ->
                    val isProcessed = isTelegramMessageProcessed(message.messageId, message.chatId, workflowId)
                    Log.d(TAG, "📨 Message ${message.messageId} in chat ${message.chatId}: ${if (isProcessed) "ALREADY PROCESSED" else "NEW"}")
                    !isProcessed
                }
                
                Log.i(TAG, "✅ STEP D1: Filtered ${messages.size} messages to ${newMessages.size} new messages")
                Log.d(TAG, "📊 Statistics: ${newMessages.size} new, ${messages.size - newMessages.size} already processed")
                
                // Log details of new messages
                newMessages.forEach { message ->
                    Log.d(TAG, "🆕 NEW: Message ${message.messageId} from ${message.firstName} (@${message.username}): '${message.text}'")
                }
                
                newMessages
            } catch (e: Exception) {
                Log.e(TAG, "💥 STEP D1: Error filtering new messages", e)
                Log.e(TAG, "🔄 Returning all messages as fallback")
                messages // Return all messages if filtering fails
            }
        }
    }
    
    /**
     * Get processed message statistics for a workflow
     */
    suspend fun getProcessedMessageStats(workflowId: String): ProcessedMessageStats {
        return withContext(Dispatchers.IO) {
            try {
                val totalCount = processedMessageDao.getProcessedMessageCount(workflowId)
                val recentMessages = processedMessageDao.getProcessedMessages(workflowId, 10)
                
                ProcessedMessageStats(
                    totalProcessedCount = totalCount,
                    recentProcessedCount = recentMessages.size,
                    lastProcessedAt = recentMessages.firstOrNull()?.processedAt
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting processed message stats", e)
                ProcessedMessageStats(0, 0, null)
            }
        }
    }
    
    /**
     * Clean up old processed message records
     */
    suspend fun cleanupOldRecords() {
        withContext(Dispatchers.IO) {
            try {
                val cutoffTime = System.currentTimeMillis() - (MESSAGE_RETENTION_DAYS * 24 * 60 * 60 * 1000L)
                processedMessageDao.deleteOldProcessedMessages(cutoffTime)
                Log.d(TAG, "Cleaned up processed messages older than $MESSAGE_RETENTION_DAYS days")
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up old processed message records", e)
            }
        }
    }
    
    /**
     * Delete all processed message records for a specific workflow
     */
    suspend fun deleteProcessedMessagesForWorkflow(workflowId: String) {
        withContext(Dispatchers.IO) {
            try {
                processedMessageDao.deleteProcessedMessagesForWorkflow(workflowId)
                Log.d(TAG, "Deleted all processed messages for workflow: $workflowId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting processed messages for workflow", e)
            }
        }
    }
    
    data class ProcessedMessageStats(
        val totalProcessedCount: Int,
        val recentProcessedCount: Int,
        val lastProcessedAt: Long?
    )
}