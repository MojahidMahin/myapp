package com.localllm.myapplication.command.chat

import android.util.Log
import com.localllm.myapplication.data.ChatHistoryRepository
import com.localllm.myapplication.data.ChatSession

/**
 * Command to create a new chat session
 * Following Command Design Pattern and Single Responsibility Principle
 */
class CreateSessionCommand(
    private val repository: ChatHistoryRepository,
    private val modelPath: String?
) : ChatCommand<ChatSession> {
    
    companion object {
        private const val TAG = "CreateSessionCommand"
    }
    
    override suspend fun executeChat(): Result<ChatSession> {
        return try {
            Log.d(TAG, "Creating new chat session with model: $modelPath")
            repository.createSession(modelPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session", e)
            Result.failure(e)
        }
    }
    
    override fun getDescription(): String = "Create new chat session with model: $modelPath"
    
    override fun canExecute(): Boolean = true
}