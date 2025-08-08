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
import com.localllm.myapplication.service.ModelManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatViewModel(
    private val context: Context,
    private val modelManager: ModelManager
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    val chatSession = mutableStateOf(ChatSession())
    val isModelLoading = mutableStateOf(false)
    val isGeneratingResponse = mutableStateOf(false)
    val currentModelPath = mutableStateOf<String?>(null)
    val modelLoadError = mutableStateOf<String?>(null)
    val canStopGeneration = mutableStateOf(false)
    
    init {
        // Observe model manager state
        viewModelScope.launch {
            modelManager.isModelLoaded.collectLatest { isLoaded ->
                chatSession.value = chatSession.value.copy(isModelLoaded = isLoaded)
            }
        }
        
        viewModelScope.launch {
            modelManager.isModelLoading.collectLatest { loading ->
                isModelLoading.value = loading
            }
        }
        
        viewModelScope.launch {
            modelManager.currentModelPath.collectLatest { path ->
                currentModelPath.value = path
            }
        }
        
        viewModelScope.launch {
            modelManager.modelLoadError.collectLatest { error ->
                modelLoadError.value = error
            }
        }
        
        viewModelScope.launch {
            modelManager.generationInProgress.collectLatest { generating ->
                isGeneratingResponse.value = generating
                canStopGeneration.value = generating
            }
        }
    }

    fun loadModel(modelPath: String) {
        modelManager.loadModel(modelPath) { result ->
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Model loaded successfully")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to load model", error)
                }
            )
        }
    }
    
    fun stopGeneration() {
        modelManager.stopGeneration()
        canStopGeneration.value = false
    }
    
    fun unloadModel() {
        modelManager.unloadModel { result ->
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Model unloaded successfully")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to unload model", error)
                }
            )
        }
    }
    
    fun getAvailableModels(): List<String> {
        return modelManager.getAvailableModels()
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
        if (!modelManager.isModelLoaded.value) {
            val errorMessage = ChatMessage(
                text = "Please load a model first to start chatting.",
                isFromUser = false
            )
            addMessage(errorMessage)
            return
        }
        
        // Generate response using ModelManager
        isGeneratingResponse.value = true
        canStopGeneration.value = true
        
        var currentResponseMessage: ChatMessage? = null
        val images = if (image != null) listOf(image) else emptyList()
        
        modelManager.generateResponse(
            prompt = text,
            images = images,
            onPartialResult = { partialText ->
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
            }
        ) { result ->
            result.fold(
                onSuccess = { finalResponse ->
                    if (currentResponseMessage == null) {
                        val responseMessage = ChatMessage(
                            text = finalResponse,
                            isFromUser = false,
                            messageType = if (image != null) MessageType.MULTIMODAL else MessageType.TEXT_ONLY
                        )
                        addMessage(responseMessage)
                    }
                    isGeneratingResponse.value = false
                    canStopGeneration.value = false
                },
                onFailure = { error ->
                    Log.e(TAG, "Chat generation failed", error)
                    val errorMessage = ChatMessage(
                        text = "Error generating response: ${error.message}",
                        isFromUser = false
                    )
                    addMessage(errorMessage)
                    isGeneratingResponse.value = false
                    canStopGeneration.value = false
                }
            )
        }
    }

    fun clearChat() {
        chatSession.value = chatSession.value.copy(
            messages = mutableListOf()
        )
    }

    fun resetSession() {
        modelManager.resetSession()
        clearChat()
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
        // ModelManager is singleton and handles its own cleanup
    }
}