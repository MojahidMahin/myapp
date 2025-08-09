package com.localllm.myapplication.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Implementation of ChatHistoryRepository using SharedPreferences and file storage
 * Following Single Responsibility Principle - only handles chat history persistence
 * Following Dependency Inversion Principle - implements the abstraction
 */
class ChatHistoryRepositoryImpl(private val context: Context) : ChatHistoryRepository {
    
    companion object {
        private const val TAG = "ChatHistoryRepository"
        private const val PREFS_NAME = "chat_history_prefs"
        private const val KEY_SESSIONS = "chat_sessions"
        private const val KEY_CURRENT_SESSION = "current_session_id"
        private const val IMAGES_DIR = "chat_images"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val imagesDir = File(context.filesDir, IMAGES_DIR)
    
    // State flows for reactive UI
    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    private val _messagesFlows = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()
    
    init {
        // Create images directory if it doesn't exist
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
        
        // Load current session on initialization
        loadCurrentSession()
    }
    
    override suspend fun saveMessage(sessionId: String, message: ChatMessage): Result<Unit> {
        return try {
            val sessions = loadSessions().toMutableList()
            val sessionIndex = sessions.indexOfFirst { it.id == sessionId }
            
            if (sessionIndex >= 0) {
                val session = sessions[sessionIndex]
                
                // Save images to disk if present
                val messageWithSavedImages = if (message.image != null) {
                    val imagePath = saveImageToDisk(message.image, message.id)
                    message.copy(image = null) // Don't store bitmap in preferences, use imagePath metadata
                } else {
                    message
                }
                
                session.messages.add(messageWithSavedImages)
                sessions[sessionIndex] = session
                
                saveSessions(sessions)
                
                // Update flow
                updateMessageFlow(sessionId, session.messages)
                
                Log.d(TAG, "Message saved to session $sessionId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Session not found: $sessionId"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save message", e)
            Result.failure(e)
        }
    }
    
    override suspend fun updateMessage(sessionId: String, messageId: String, content: String): Result<Unit> {
        return try {
            val sessions = loadSessions().toMutableList()
            val sessionIndex = sessions.indexOfFirst { it.id == sessionId }
            
            if (sessionIndex >= 0) {
                val session = sessions[sessionIndex]
                val messageIndex = session.messages.indexOfFirst { it.id == messageId }
                
                if (messageIndex >= 0) {
                    val oldMessage = session.messages[messageIndex]
                    val updatedMessage = oldMessage.copy(text = content)
                    session.messages[messageIndex] = updatedMessage
                    sessions[sessionIndex] = session
                    
                    saveSessions(sessions)
                    
                    // Update flow
                    updateMessageFlow(sessionId, session.messages)
                    
                    Log.d(TAG, "Message updated in session $sessionId")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Message not found: $messageId"))
                }
            } else {
                Result.failure(Exception("Session not found: $sessionId"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update message", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getMessages(sessionId: String): Result<List<ChatMessage>> {
        return try {
            val sessions = loadSessions()
            val session = sessions.find { it.id == sessionId }
            
            if (session != null) {
                // Load images from disk for messages that had images
                val messagesWithImages = session.messages.map { message ->
                    val imagePath = File(imagesDir, "${message.id}.jpg")
                    if (imagePath.exists()) {
                        val bitmap = BitmapFactory.decodeFile(imagePath.absolutePath)
                        message.copy(image = bitmap)
                    } else {
                        message
                    }
                }
                
                Result.success(messagesWithImages)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get messages", e)
            Result.failure(e)
        }
    }
    
    override suspend fun createSession(modelPath: String?): Result<ChatSession> {
        return try {
            val newSession = ChatSession(modelPath = modelPath, isModelLoaded = modelPath != null)
            val sessions = loadSessions().toMutableList()
            sessions.add(newSession)
            
            saveSessions(sessions)
            setCurrentSession(newSession.id)
            
            Log.d(TAG, "New session created: ${newSession.id}")
            Result.success(newSession)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getCurrentSession(): Result<ChatSession?> {
        return try {
            Result.success(_currentSession.value)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current session", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getAllSessions(): Result<List<ChatSession>> {
        return try {
            val sessions = loadSessions()
            Result.success(sessions)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all sessions", e)
            Result.failure(e)
        }
    }
    
    override suspend fun deleteSession(sessionId: String): Result<Unit> {
        return try {
            val sessions = loadSessions().toMutableList()
            val sessionToDelete = sessions.find { it.id == sessionId }
            
            if (sessionToDelete != null) {
                // Delete associated image files
                sessionToDelete.messages.forEach { message ->
                    val imagePath = File(imagesDir, "${message.id}.jpg")
                    if (imagePath.exists()) {
                        imagePath.delete()
                    }
                }
                
                sessions.removeAll { it.id == sessionId }
                saveSessions(sessions)
                
                // Clear current session if it was deleted
                if (_currentSession.value?.id == sessionId) {
                    _currentSession.value = null
                    prefs.edit().remove(KEY_CURRENT_SESSION).apply()
                }
                
                // Remove message flow
                _messagesFlows.remove(sessionId)
                
                Log.d(TAG, "Session deleted: $sessionId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Session not found: $sessionId"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete session", e)
            Result.failure(e)
        }
    }
    
    override suspend fun clearAllHistory(): Result<Unit> {
        return try {
            // Delete all image files
            imagesDir.listFiles()?.forEach { it.delete() }
            
            // Clear preferences
            prefs.edit().clear().apply()
            
            // Clear flows
            _currentSession.value = null
            _messagesFlows.clear()
            
            Log.d(TAG, "All chat history cleared")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all history", e)
            Result.failure(e)
        }
    }
    
    override fun getMessagesFlow(sessionId: String): Flow<List<ChatMessage>> {
        return _messagesFlows.getOrPut(sessionId) {
            MutableStateFlow(emptyList())
        }.asStateFlow()
    }
    
    override fun getCurrentSessionFlow(): Flow<ChatSession?> {
        return _currentSession.asStateFlow()
    }
    
    // Private helper methods
    
    private fun loadSessions(): List<ChatSession> {
        return try {
            val sessionsJson = prefs.getString(KEY_SESSIONS, null)
            if (sessionsJson != null) {
                val type = object : TypeToken<List<ChatSession>>() {}.type
                gson.fromJson(sessionsJson, type) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse sessions JSON", e)
            emptyList()
        }
    }
    
    private fun saveSessions(sessions: List<ChatSession>) {
        val sessionsJson = gson.toJson(sessions)
        prefs.edit().putString(KEY_SESSIONS, sessionsJson).apply()
    }
    
    private fun setCurrentSession(sessionId: String) {
        prefs.edit().putString(KEY_CURRENT_SESSION, sessionId).apply()
        
        // Load and set current session
        val sessions = loadSessions()
        val session = sessions.find { it.id == sessionId }
        _currentSession.value = session
        
        // Load messages for the current session
        session?.let { updateMessageFlow(sessionId, it.messages) }
    }
    
    private fun loadCurrentSession() {
        val sessionId = prefs.getString(KEY_CURRENT_SESSION, null)
        if (sessionId != null) {
            val sessions = loadSessions()
            _currentSession.value = sessions.find { it.id == sessionId }
        }
    }
    
    private fun updateMessageFlow(sessionId: String, messages: List<ChatMessage>) {
        val flow = _messagesFlows.getOrPut(sessionId) {
            MutableStateFlow(emptyList())
        }
        flow.value = messages
    }
    
    private fun saveImageToDisk(bitmap: Bitmap, messageId: String): String {
        return try {
            val imageFile = File(imagesDir, "$messageId.jpg")
            val fos = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            fos.close()
            imageFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save image to disk", e)
            ""
        }
    }
}