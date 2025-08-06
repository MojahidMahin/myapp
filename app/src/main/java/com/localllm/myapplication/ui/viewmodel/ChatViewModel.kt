package com.localllm.myapplication.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.localllm.myapplication.command.ChatCommand
import com.localllm.myapplication.command.LoadModelCommand
import com.localllm.myapplication.data.ChatMessage
import com.localllm.myapplication.data.ChatSession
import com.localllm.myapplication.data.LLMRepository
import com.localllm.myapplication.data.MessageType

class ChatViewModel(private val llmRepository: LLMRepository) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    val chatSession = mutableStateOf(ChatSession())
    val isModelLoading = mutableStateOf(false)
    val isGeneratingResponse = mutableStateOf(false)
    val currentModelPath = mutableStateOf<String?>(null)
    val modelLoadError = mutableStateOf<String?>(null)

    fun loadModel(modelPath: String) {
        isModelLoading.value = true
        currentModelPath.value = modelPath
        modelLoadError.value = null
        
        val loadCommand = LoadModelCommand(
            llmRepository = llmRepository,
            modelPath = modelPath,
            onSuccess = {
                Log.d(TAG, "Model loaded successfully")
                isModelLoading.value = false
                modelLoadError.value = null
                chatSession.value = chatSession.value.copy(
                    modelPath = modelPath,
                    isModelLoaded = true
                )
                // Add success message to chat
                val successMessage = ChatMessage(
                    text = "Model loaded successfully! You can now start chatting.",
                    isFromUser = false
                )
                addMessage(successMessage)
            },
            onError = { error ->
                Log.e(TAG, "Failed to load model", error)
                isModelLoading.value = false
                modelLoadError.value = error.message
                // Add error message to chat
                val errorMessage = ChatMessage(
                    text = "Error loading model: ${error.message}",
                    isFromUser = false
                )
                addMessage(errorMessage)
            }
        )
        
        loadCommand.execute()
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
        if (!llmRepository.isModelLoaded()) {
            val errorMessage = ChatMessage(
                text = "Please load a model first to start chatting.",
                isFromUser = false
            )
            addMessage(errorMessage)
            return
        }
        
        // Generate response
        isGeneratingResponse.value = true
        
        val chatCommand = ChatCommand(
            llmRepository = llmRepository,
            prompt = text,
            image = image,
            onResponse = { responseMessage ->
                addMessage(responseMessage)
                isGeneratingResponse.value = false
            },
            onError = { error ->
                Log.e(TAG, "Chat command failed", error)
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
        llmRepository.unloadModel()
        chatSession.value = chatSession.value.copy(
            modelPath = null,
            isModelLoaded = false
        )
        currentModelPath.value = null
    }

    private fun addMessage(message: ChatMessage) {
        val currentMessages = chatSession.value.messages.toMutableList()
        currentMessages.add(message)
        chatSession.value = chatSession.value.copy(messages = currentMessages)
    }

    override fun onCleared() {
        super.onCleared()
        llmRepository.unloadModel()
    }
}