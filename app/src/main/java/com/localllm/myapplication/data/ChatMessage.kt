package com.localllm.myapplication.data

import android.graphics.Bitmap
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val image: Bitmap? = null,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: MessageType = if (image != null) MessageType.MULTIMODAL else MessageType.TEXT_ONLY
)

enum class MessageType {
    TEXT_ONLY,
    MULTIMODAL
}

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val modelPath: String? = null,
    val isModelLoaded: Boolean = false
)