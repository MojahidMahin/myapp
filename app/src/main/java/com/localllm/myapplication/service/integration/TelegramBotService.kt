package com.localllm.myapplication.service.integration

import android.content.Context
import android.util.Log
import com.localllm.myapplication.data.TelegramPreferences
import com.localllm.myapplication.utils.TelegramUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Telegram Bot integration service for workflow automation
 * Handles bot authentication, message monitoring, and message sending
 */
class TelegramBotService(private val context: Context) {
    
    companion object {
        private const val TAG = "TelegramBotService"
        private const val TELEGRAM_API_BASE = "https://api.telegram.org/bot"
    }
    
    private val telegramPrefs = TelegramPreferences(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private var botToken: String? = telegramPrefs.getBotToken()
    private var botUsername: String? = telegramPrefs.getBotUsername()
    private var lastUpdateId: Long = telegramPrefs.getLastUpdateId()
    
    data class TelegramMessage(
        val messageId: Long,
        val chatId: Long,
        val userId: Long,
        val username: String?,
        val firstName: String,
        val lastName: String?,
        val text: String,
        val timestamp: Long,
        val chatType: String, // "private", "group", "supergroup", "channel"
        val isBot: Boolean = false
    )
    
    data class TelegramCondition(
        val chatIdFilter: Long? = null,
        val userIdFilter: Long? = null,
        val usernameFilter: String? = null,
        val textContains: String? = null,
        val chatTypeFilter: String? = null, // "private", "group", etc.
        val isCommand: Boolean = false // Messages starting with /
    )
    
    /**
     * Initialize Telegram bot with token
     */
    suspend fun initializeBot(token: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val trimmedToken = token.trim()
                
                // Validate token format first
                if (!TelegramUtils.isValidBotToken(trimmedToken)) {
                    return@withContext Result.failure(
                        Exception("Invalid token format. Expected: BOT_ID:TOKEN")
                    )
                }
                
                botToken = trimmedToken
                
                // Test the bot token by getting bot info
                val botInfo = getBotInfo()
                botInfo.fold(
                    onSuccess = { username ->
                        botUsername = username
                        // Save to persistent storage
                        telegramPrefs.saveBotToken(trimmedToken)
                        telegramPrefs.saveBotUsername(username)
                        
                        Log.d(TAG, "Telegram bot initialized successfully: @$username")
                        Result.success("Bot @$username connected successfully")
                    },
                    onFailure = { error ->
                        botToken = null
                        botUsername = null
                        Log.e(TAG, "Failed to initialize Telegram bot", error)
                        Result.failure(error)
                    }
                )
            } catch (e: Exception) {
                botToken = null
                botUsername = null
                Log.e(TAG, "Exception initializing Telegram bot", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Helper method to handle API responses consistently
     */
    private fun handleApiResponse(response: Response, responseBody: String?): Result<JSONObject> {
        return try {
            if (!response.isSuccessful) {
                val telegramError = TelegramUtils.parseApiError(responseBody)
                val commonMessage = TelegramUtils.getCommonErrorMessage(response.code)
                return Result.failure(Exception("$commonMessage: ${telegramError.description}"))
            }
            
            val json = JSONObject(responseBody ?: "{}")
            if (!json.getBoolean("ok")) {
                val telegramError = TelegramUtils.parseApiError(responseBody)
                return Result.failure(Exception("Telegram API error: ${telegramError.description}"))
            }
            
            Result.success(json)
        } catch (e: JSONException) {
            Result.failure(Exception("Invalid JSON response from Telegram API"))
        }
    }
    
    /**
     * Get bot information to verify token
     */
    private suspend fun getBotInfo(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val token = botToken ?: return@withContext Result.failure(
                    Exception("Bot token not set")
                )
                
                val url = "$TELEGRAM_API_BASE$token/getMe"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()
                
                val apiResult = handleApiResponse(response, responseBody)
                apiResult.fold(
                    onSuccess = { json ->
                        val result = json.getJSONObject("result")
                        val username = result.getString("username")
                        Result.success(username)
                    },
                    onFailure = { error ->
                        Result.failure(error)
                    }
                )
                
            } catch (e: JSONException) {
                Log.e(TAG, "Failed to parse bot info response", e)
                Result.failure(Exception("Invalid response format from Telegram API"))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get bot info", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Check for new messages matching conditions
     */
    suspend fun checkForNewMessages(condition: TelegramCondition, limit: Int = 10): Result<List<TelegramMessage>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = botToken ?: return@withContext Result.failure(
                    Exception("Bot token not set. Please initialize bot first.")
                )
                
                // Get updates from Telegram
                val updates = getUpdates(limit)
                updates.fold(
                    onSuccess = { messages ->
                        // Filter messages based on conditions
                        val filteredMessages = messages.filter { message ->
                            matchesCondition(message, condition)
                        }
                        
                        Log.d(TAG, "Found ${filteredMessages.size} matching messages out of ${messages.size} total")
                        Result.success(filteredMessages)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to check for new messages", error)
                        Result.failure(error)
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception checking for new messages", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get updates from Telegram Bot API
     */
    private suspend fun getUpdates(limit: Int): Result<List<TelegramMessage>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = botToken ?: return@withContext Result.failure(
                    Exception("Bot token not set")
                )
                
                val url = "$TELEGRAM_API_BASE$token/getUpdates"
                val requestJson = JSONObject().apply {
                    put("offset", lastUpdateId + 1)
                    put("limit", limit)
                    put("timeout", 0) // Short polling for workflow automation
                    put("allowed_updates", JSONArray().apply {
                        put("message")
                        put("channel_post")
                    })
                }
                
                val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}: $responseBody")
                    )
                }
                
                val json = JSONObject(responseBody ?: "")
                if (!json.getBoolean("ok")) {
                    return@withContext Result.failure(
                        Exception("Telegram API error: ${json.optString("description")}")
                    )
                }
                
                val updates = json.getJSONArray("result")
                val messages = mutableListOf<TelegramMessage>()
                
                for (i in 0 until updates.length()) {
                    val update = updates.getJSONObject(i)
                    val updateId = update.getLong("update_id")
                    
                    // Update last update ID
                    if (updateId > lastUpdateId) {
                        lastUpdateId = updateId
                        telegramPrefs.saveLastUpdateId(updateId)
                    }
                    
                    // Parse message if present
                    if (update.has("message")) {
                        val messageJson = update.getJSONObject("message")
                        val telegramMessage = parseMessage(messageJson)
                        messages.add(telegramMessage)
                    }
                }
                
                Result.success(messages)
                
            } catch (e: JSONException) {
                Log.e(TAG, "Failed to parse updates response", e)
                Result.failure(Exception("Invalid response format from Telegram API"))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get updates", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Parse Telegram message JSON to TelegramMessage
     */
    private fun parseMessage(messageJson: JSONObject): TelegramMessage {
        val messageId = messageJson.getLong("message_id")
        val date = messageJson.getLong("date") * 1000 // Convert to milliseconds
        val text = messageJson.optString("text", "")
        
        val chat = messageJson.getJSONObject("chat")
        val chatId = chat.getLong("id")
        val chatType = chat.getString("type")
        
        val from = messageJson.getJSONObject("from")
        val userId = from.getLong("id")
        val username = from.optString("username")
        val firstName = from.getString("first_name")
        val lastName = from.optString("last_name")
        val isBot = from.optBoolean("is_bot", false)
        
        return TelegramMessage(
            messageId = messageId,
            chatId = chatId,
            userId = userId,
            username = if (username.isEmpty()) null else username,
            firstName = firstName,
            lastName = if (lastName.isEmpty()) null else lastName,
            text = text,
            timestamp = date,
            chatType = chatType,
            isBot = isBot
        )
    }
    
    /**
     * Check if message matches the given condition
     */
    private fun matchesCondition(message: TelegramMessage, condition: TelegramCondition): Boolean {
        // Filter by chat ID
        condition.chatIdFilter?.let { chatId ->
            if (message.chatId != chatId) return false
        }
        
        // Filter by user ID
        condition.userIdFilter?.let { userId ->
            if (message.userId != userId) return false
        }
        
        // Filter by username
        condition.usernameFilter?.let { username ->
            if (message.username != username) return false
        }
        
        // Filter by text content
        condition.textContains?.let { text ->
            if (!message.text.contains(text, ignoreCase = true)) return false
        }
        
        // Filter by chat type
        condition.chatTypeFilter?.let { chatType ->
            if (message.chatType != chatType) return false
        }
        
        // Filter for commands
        if (condition.isCommand) {
            if (!message.text.startsWith("/")) return false
        }
        
        return true
    }
    
    /**
     * Send a text message
     */
    suspend fun sendMessage(
        chatId: Long,
        text: String,
        parseMode: String? = null, // "Markdown", "MarkdownV2", "HTML"
        replyToMessageId: Long? = null
    ): Result<Long> {
        return withContext(Dispatchers.IO) {
            try {
                val token = botToken ?: return@withContext Result.failure(
                    Exception("Bot token not set. Please initialize bot first.")
                )
                
                val url = "$TELEGRAM_API_BASE$token/sendMessage"
                val requestJson = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", text)
                    parseMode?.let { put("parse_mode", it) }
                    replyToMessageId?.let { put("reply_to_message_id", it) }
                }
                
                val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}: $responseBody")
                    )
                }
                
                val json = JSONObject(responseBody ?: "")
                if (!json.getBoolean("ok")) {
                    return@withContext Result.failure(
                        Exception("Telegram API error: ${json.optString("description")}")
                    )
                }
                
                val result = json.getJSONObject("result")
                val messageId = result.getLong("message_id")
                
                Log.d(TAG, "Message sent successfully. Message ID: $messageId")
                Result.success(messageId)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Send a reply to a specific message
     */
    suspend fun replyToMessage(
        chatId: Long,
        replyToMessageId: Long,
        text: String,
        parseMode: String? = null
    ): Result<Long> {
        return sendMessage(chatId, text, parseMode, replyToMessageId)
    }
    
    /**
     * Forward a message to another chat
     */
    suspend fun forwardMessage(
        fromChatId: Long,
        toChatId: Long,
        messageId: Long
    ): Result<Long> {
        return withContext(Dispatchers.IO) {
            try {
                val token = botToken ?: return@withContext Result.failure(
                    Exception("Bot token not set. Please initialize bot first.")
                )
                
                val url = "$TELEGRAM_API_BASE$token/forwardMessage"
                val requestJson = JSONObject().apply {
                    put("chat_id", toChatId)
                    put("from_chat_id", fromChatId)
                    put("message_id", messageId)
                }
                
                val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}: $responseBody")
                    )
                }
                
                val json = JSONObject(responseBody ?: "")
                if (!json.getBoolean("ok")) {
                    return@withContext Result.failure(
                        Exception("Telegram API error: ${json.optString("description")}")
                    )
                }
                
                val result = json.getJSONObject("result")
                val forwardedMessageId = result.getLong("message_id")
                
                Log.d(TAG, "Message forwarded successfully. Message ID: $forwardedMessageId")
                Result.success(forwardedMessageId)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to forward message", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get chat information
     */
    suspend fun getChatInfo(chatId: Long): Result<JSONObject> {
        return withContext(Dispatchers.IO) {
            try {
                val token = botToken ?: return@withContext Result.failure(
                    Exception("Bot token not set. Please initialize bot first.")
                )
                
                val url = "$TELEGRAM_API_BASE$token/getChat"
                val requestJson = JSONObject().apply {
                    put("chat_id", chatId)
                }
                
                val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}: $responseBody")
                    )
                }
                
                val json = JSONObject(responseBody ?: "")
                if (!json.getBoolean("ok")) {
                    return@withContext Result.failure(
                        Exception("Telegram API error: ${json.optString("description")}")
                    )
                }
                
                Result.success(json.getJSONObject("result"))
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get chat info", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Check if bot is properly initialized
     */
    fun isInitialized(): Boolean = botToken != null && botUsername != null
    
    /**
     * Get bot username
     */
    fun getBotUsername(): String? = botUsername
    
    /**
     * Get bot token (masked for security)
     */
    fun getBotTokenMasked(): String? {
        return botToken?.let { token ->
            if (token.length > 10) {
                "${token.take(5)}...${token.takeLast(5)}"
            } else {
                "***"
            }
        }
    }
    
    /**
     * Clear all pending updates to resolve conflicts
     */
    suspend fun clearPendingUpdates(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val token = botToken ?: return@withContext Result.failure(
                    Exception("Bot token not set. Please initialize bot first.")
                )
                
                val url = "$TELEGRAM_API_BASE$token/getUpdates"
                val requestJson = JSONObject().apply {
                    put("offset", -1) // Clear all pending updates
                    put("limit", 1)
                    put("timeout", 0)
                }
                
                val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()
                
                val apiResult = handleApiResponse(response, responseBody)
                apiResult.fold(
                    onSuccess = { json ->
                        lastUpdateId = 0
                        telegramPrefs.saveLastUpdateId(0)
                        Log.d(TAG, "Pending updates cleared successfully")
                        Result.success("All pending updates cleared")
                    },
                    onFailure = { error ->
                        Result.failure(error)
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear pending updates", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Clear bot configuration
     */
    fun clearBotConfig() {
        botToken = null
        botUsername = null
        lastUpdateId = 0
        telegramPrefs.clearAll()
        Log.d(TAG, "Bot configuration cleared")
    }
}