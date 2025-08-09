package com.localllm.myapplication.command.chat

import android.util.Log
import com.localllm.myapplication.data.ChatHistoryRepository

/**
 * Command to update a chat message content
 * Following Command Design Pattern and Single Responsibility Principle
 */
class UpdateMessageCommand(
    private val repository: ChatHistoryRepository,
    private val sessionId: String,
    private val messageId: String,
    private val content: String
) : ChatCommand<Unit> {
    
    companion object {
        private const val TAG = "UpdateMessageCommand"
    }
    
    override suspend fun executeChat(): Result<Unit> {
        return try {
            Log.d(TAG, "Updating message $messageId in session $sessionId")
            repository.updateMessage(sessionId, messageId, content)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update message", e)
            Result.failure(e)
        }
    }
    
    override fun getDescription(): String = "Update message $messageId in session $sessionId"
    
    override fun canExecute(): Boolean = sessionId.isNotBlank() && messageId.isNotBlank()
}