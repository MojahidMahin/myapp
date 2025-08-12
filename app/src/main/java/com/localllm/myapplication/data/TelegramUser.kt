package com.localllm.myapplication.data

import org.json.JSONObject

data class TelegramUser(
    val id: Long,
    val firstName: String?,
    val lastName: String?,
    val username: String?,
    val displayName: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("firstName", firstName ?: "")
            put("lastName", lastName ?: "")
            put("username", username ?: "")
            put("displayName", displayName)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): TelegramUser {
            return TelegramUser(
                id = json.getLong("id"),
                firstName = json.optString("firstName").takeIf { it.isNotEmpty() },
                lastName = json.optString("lastName").takeIf { it.isNotEmpty() },
                username = json.optString("username").takeIf { it.isNotEmpty() },
                displayName = json.getString("displayName")
            )
        }
        
        fun fromChatInfo(id: Long, firstName: String?, lastName: String?, username: String?): TelegramUser {
            val displayName = buildString {
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
            
            return TelegramUser(
                id = id,
                firstName = firstName,
                lastName = lastName,
                username = username,
                displayName = displayName
            )
        }
    }
}