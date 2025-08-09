package com.localllm.myapplication.command.chat

import android.util.Log
import com.localllm.myapplication.data.ChatHistoryRepository
import com.localllm.myapplication.data.ChatMessage

/**
 * Command to save a chat message
 * Following Command Design Pattern and Single Responsibility Principle
 */
class SaveMessageCommand(
    private val repository: ChatHistoryRepository,
    private val sessionId: String,
    private val message: ChatMessage
) : ChatCommand<Unit> {
    
    companion object {
        private const val TAG = "SaveMessageCommand"
    }
    
    override suspend fun executeChat(): Result<Unit> {
        return try {
            Log.d(TAG, "Saving message to session $sessionId")
            repository.saveMessage(sessionId, message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save message", e)
            Result.failure(e)
        }
    }
    
    override fun getDescription(): String = "Save message to session $sessionId"
    
    override fun canExecute(): Boolean = sessionId.isNotBlank() && message.text.isNotBlank()
}