package com.localllm.myapplication.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localllm.myapplication.command.ChatCommand
import com.localllm.myapplication.data.ChatMessage
import com.localllm.myapplication.data.ChatSession
import com.localllm.myapplication.data.MessageType
import com.localllm.myapplication.service.ai.MediaPipeLLMService
import kotlinx.coroutines.launch

class ChatViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    val chatSession = mutableStateOf(ChatSession())
    val isModelLoading = mutableStateOf(false)
    val isGeneratingResponse = mutableStateOf(false)
    val currentModelPath = mutableStateOf<String?>(null)
    val modelLoadError = mutableStateOf<String?>(null)
    
    private val mediaPipeLLMService = MediaPipeLLMService(context)

    fun loadModel(modelPath: String) {
        isModelLoading.value = true
        currentModelPath.value = modelPath
        modelLoadError.value = null
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading MediaPipe model: $modelPath")
                
                val result = mediaPipeLLMService.initialize(modelPath)
                
                result.fold(
                    onSuccess = {
                        Log.d(TAG, "MediaPipe model loaded successfully")
                        isModelLoading.value = false
                        modelLoadError.value = null
                        chatSession.value = chatSession.value.copy(
                            modelPath = modelPath,
                            isModelLoaded = true
                        )
                        
                        val successMessage = ChatMessage(
                            text = "MediaPipe LLM model loaded successfully! You can now start chatting with image support.",
                            isFromUser = false
                        )
                        addMessage(successMessage)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to load MediaPipe model", error)
                        isModelLoading.value = false
                        modelLoadError.value = error.message
                        
                        val errorMessage = ChatMessage(
                            text = "Error loading MediaPipe model: ${error.message}",
                            isFromUser = false
                        )
                        addMessage(errorMessage)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during model loading", e)
                isModelLoading.value = false
                modelLoadError.value = e.message
                
                val errorMessage = ChatMessage(
                    text = "Exception loading model: ${e.message}",
                    isFromUser = false
                )
                addMessage(errorMessage)
            }
        }
    }

    fun sendMessage(text: String, image: Bitmap? = null) {
        if (text.isBlank()) return
        
        // Add user message to chat
        val userMessage = ChatMessage(
            text = text,
            image = image,
            isFromUser = true,
            messageType = if (image != null) MessageType.MULTIMODAL else MessageType.TEXT_ONLY
        )
        addMessage(userMessage)
        
        // Check if model is loaded
        if (!mediaPipeLLMService.isModelLoaded()) {
            val errorMessage = ChatMessage(
                text = "Please load a model first to start chatting.",
                isFromUser = false
            )
            addMessage(errorMessage)
            return
        }
        
        // Generate response using MediaPipe
        isGeneratingResponse.value = true
        
        // Add loading message that gets updated with streaming response
        var currentResponseMessage: ChatMessage? = null
        
        val chatCommand = ChatCommand(
            mediaPipeLLMService = mediaPipeLLMService,
            prompt = text,
            image = image,
            onPartialResponse = { partialText ->
                // Update the current response message with streaming text
                if (currentResponseMessage == null) {
                    currentResponseMessage = ChatMessage(
                        text = partialText,
                        isFromUser = false,
                        messageType = if (image != null) MessageType.MULTIMODAL else MessageType.TEXT_ONLY
                    )
                    addMessage(currentResponseMessage!!)
                } else {
                    updateLastMessage(partialText)
                }
            },
            onResponse = { responseMessage ->
                // Final response received
                if (currentResponseMessage == null) {
                    addMessage(responseMessage)
                }
                isGeneratingResponse.value = false
            },
            onError = { error ->
                Log.e(TAG, "MediaPipe chat command failed", error)
                val errorMessage = ChatMessage(
                    text = "Error generating response: ${error.message}",
                    isFromUser = false
                )
                addMessage(errorMessage)
                isGeneratingResponse.value = false
            }
        )
        
        chatCommand.execute()
    }

    fun clearChat() {
        chatSession.value = chatSession.value.copy(
            messages = mutableListOf()
        )
    }

    fun unloadModel() {
        mediaPipeLLMService.cleanup()
        chatSession.value = chatSession.value.copy(
            modelPath = null,
            isModelLoaded = false
        )
        currentModelPath.value = null
    }
    
    fun resetSession() {
        viewModelScope.launch {
            mediaPipeLLMService.resetSession()
            clearChat()
        }
    }

    private fun addMessage(message: ChatMessage) {
        val currentMessages = chatSession.value.messages.toMutableList()
        currentMessages.add(message)
        chatSession.value = chatSession.value.copy(messages = currentMessages)
    }
    
    private fun updateLastMessage(newText: String) {
        val currentMessages = chatSession.value.messages.toMutableList()
        if (currentMessages.isNotEmpty()) {
            val lastMessage = currentMessages.last()
            if (!lastMessage.isFromUser) {
                currentMessages[currentMessages.lastIndex] = lastMessage.copy(text = newText)
                chatSession.value = chatSession.value.copy(messages = currentMessages)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPipeLLMService.cleanup()
    }
}