package com.localllm.myapplication.data

data class Contact(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val gmail: String? = null,
    val telegramId: String? = null,
    val telegramUsername: String? = null,
    val isAutoSaved: Boolean = false, // true if saved automatically from workflow
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun isValid(): Boolean {
        return name.isNotBlank() && (gmail?.isNotBlank() == true || telegramId?.isNotBlank() == true)
    }
    
    fun getDisplayInfo(): String {
        val parts = mutableListOf<String>()
        gmail?.let { parts.add("Email: $it") }
        telegramUsername?.let { parts.add("@$it") }
        telegramId?.let { if (telegramUsername == null) parts.add("Telegram ID: $it") }
        return parts.joinToString(" • ")
    }
}