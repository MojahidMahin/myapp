package com.localllm.myapplication.utils

import org.json.JSONException
import org.json.JSONObject

object TelegramUtils {
    
    data class TelegramError(
        val errorCode: Int,
        val description: String,
        val parameters: Map<String, Any>? = null
    )
    
    fun isValidBotToken(token: String): Boolean {
        if (token.isBlank()) return false
        
        val trimmedToken = token.trim()
        val parts = trimmedToken.split(":")
        
        // Must have exactly 2 parts (bot_id:token)
        if (parts.size != 2) return false
        
        val botId = parts[0]
        val tokenPart = parts[1]
        
        // Bot ID must be 8-10 digits
        if (!botId.matches(Regex("\\d{8,10}"))) return false
        
        // Token part must be at least 35 characters and contain only valid characters
        if (tokenPart.length < 35) return false
        if (!tokenPart.matches(Regex("[A-Za-z0-9_-]+"))) return false
        
        return true
    }
    
    fun formatTokenForDisplay(token: String?): String {
        return token?.let { 
            if (it.length > 10) {
                "${it.take(5)}...${it.takeLast(5)}"
            } else {
                "***"
            }
        } ?: "Not set"
    }
    
    fun parseApiError(errorBody: String?): TelegramError {
        if (errorBody.isNullOrBlank()) {
            return TelegramError(-1, "Unknown error occurred")
        }
        
        return try {
            val json = JSONObject(errorBody)
            val ok = json.optBoolean("ok", false)
            
            if (!ok) {
                val errorCode = json.optInt("error_code", -1)
                val description = json.optString("description", "Unknown error")
                val parameters = json.optJSONObject("parameters")?.let { params ->
                    val paramMap = mutableMapOf<String, Any>()
                    params.keys().forEach { key ->
                        paramMap[key] = params.get(key)
                    }
                    paramMap.toMap()
                }
                
                TelegramError(errorCode, description, parameters)
            } else {
                TelegramError(0, "Success")
            }
        } catch (e: JSONException) {
            TelegramError(-1, "Failed to parse error response: $errorBody")
        }
    }
    
    fun getCommonErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            400 -> "Bad Request - Invalid token or parameters"
            401 -> "Unauthorized - Invalid bot token"
            403 -> "Forbidden - Bot was blocked or doesn't have permission"
            404 -> "Not Found - Bot token not found or chat doesn't exist"
            429 -> "Too Many Requests - Rate limit exceeded, please wait"
            500 -> "Internal Server Error - Telegram server issue"
            502 -> "Bad Gateway - Telegram server temporarily unavailable"
            else -> "HTTP Error $errorCode"
        }
    }
    
    fun formatUserDisplayName(firstName: String?, lastName: String?, username: String?): String {
        return buildString {
            firstName?.let { append(it) }
            lastName?.let {
                if (isNotEmpty()) append(" ")
                append(it)
            }
            username?.let { user ->
                if (isEmpty()) {
                    append("@$user")
                } else {
                    append(" (@$user)")
                }
            }
            if (isEmpty()) append("Unknown User")
        }
    }
    
    fun validateTokenFormat(token: String): Pair<Boolean, String> {
        val trimmedToken = token.trim()
        
        if (trimmedToken.isEmpty()) {
            return false to "Token cannot be empty"
        }
        
        if (!trimmedToken.contains(':')) {
            return false to "Invalid token format. Should be BOT_ID:TOKEN"
        }
        
        val parts = trimmedToken.split(":")
        if (parts.size != 2) {
            return false to "Token must contain exactly one colon (:)"
        }
        
        val botId = parts[0]
        val tokenPart = parts[1]
        
        if (!botId.matches(Regex("\\d{8,10}"))) {
            return false to "Bot ID must be 8-10 digits. Found: ${botId.length} characters"
        }
        
        if (tokenPart.length < 35) {
            return false to "Token part too short. Need at least 35 characters, found: ${tokenPart.length}"
        }
        
        if (!tokenPart.matches(Regex("[A-Za-z0-9_-]+"))) {
            return false to "Token contains invalid characters. Only A-Z, a-z, 0-9, _ and - allowed"
        }
        
        return true to "Valid token format ✓"
    }
}