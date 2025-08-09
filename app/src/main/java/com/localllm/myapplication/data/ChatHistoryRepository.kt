package com.localllm.myapplication.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for chat history operations
 * Following Dependency Inversion Principle - depend on abstractions
 * Following Interface Segregation Principle - focused interface for chat operations
 */
interface ChatHistoryRepository {
    suspend fun saveMessage(sessionId: String, message: ChatMessage): Result<Unit>
    suspend fun updateMessage(sessionId: String, messageId: String, content: String): Result<Unit>
    suspend fun getMessages(sessionId: String): Result<List<ChatMessage>>
    suspend fun createSession(modelPath: String?): Result<ChatSession>
    suspend fun getCurrentSession(): Result<ChatSession?>
    suspend fun getAllSessions(): Result<List<ChatSession>>
    suspend fun deleteSession(sessionId: String): Result<Unit>
    suspend fun clearAllHistory(): Result<Unit>
    
    // Reactive flows
    fun getMessagesFlow(sessionId: String): Flow<List<ChatMessage>>
    fun getCurrentSessionFlow(): Flow<ChatSession?>
}