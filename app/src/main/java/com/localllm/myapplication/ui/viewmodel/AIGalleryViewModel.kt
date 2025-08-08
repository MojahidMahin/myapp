package com.localllm.myapplication.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localllm.myapplication.command.ai.AudioTranscriptionCommand
import com.localllm.myapplication.command.ai.PromptLabCommand
import com.localllm.myapplication.command.ai.PromptLabResult
import com.localllm.myapplication.command.ai.TranscriptionMode
import com.localllm.myapplication.data.AIFeature
import com.localllm.myapplication.data.AIFeatureType
import com.localllm.myapplication.data.ChatMessage
import com.localllm.myapplication.data.MessageType
import com.localllm.myapplication.data.PromptTemplate
import com.localllm.myapplication.data.PromptTemplateFactory
import com.localllm.myapplication.service.ai.MediaPipeLLMService
import kotlinx.coroutines.launch

class AIGalleryViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = "AIGalleryViewModel"
    }

    // Core state
    val isModelLoading = mutableStateOf(false)
    val currentModelPath = mutableStateOf<String?>(null)
    val modelLoadError = mutableStateOf<String?>(null)
    val isProcessing = mutableStateOf(false)
    
    // Feature selection
    val selectedFeature = mutableStateOf<AIFeatureType?>(null)
    val availableFeatures = mutableStateOf(
        listOf(
            AIFeature(AIFeatureType.LLM_CHAT),
            AIFeature(AIFeatureType.ASK_IMAGE),
            AIFeature(AIFeatureType.AUDIO_TRANSCRIPTION),
            AIFeature(AIFeatureType.PROMPT_LAB)
        )
    )
    
    // Chat feature state (inherits from ChatViewModel)
    val chatMessages = mutableStateOf<List<ChatMessage>>(emptyList())
    
    // Prompt Lab state
    val availableTemplates = mutableStateOf(PromptTemplateFactory.getAllTemplates())
    val selectedTemplate = mutableStateOf<PromptTemplate?>(null)
    val templateParameters = mutableStateOf<Map<String, String>>(emptyMap())
    val promptLabResults = mutableStateOf<List<PromptLabResult>>(emptyList())
    
    // Audio transcription state
    val transcriptionMode = mutableStateOf(TranscriptionMode.TRANSCRIBE_ONLY)
    val transcriptionResults = mutableStateOf<List<String>>(emptyList())
    
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
                        
                        addChatMessage(
                            ChatMessage(
                                text = "AI Gallery model loaded successfully! All features are now available.",
                                isFromUser = false
                            )
                        )
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to load MediaPipe model", error)
                        isModelLoading.value = false
                        modelLoadError.value = error.message
                        
                        addChatMessage(
                            ChatMessage(
                                text = "Error loading model: ${error.message}",
                                isFromUser = false
                            )
                        )
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during model loading", e)
                isModelLoading.value = false
                modelLoadError.value = e.message
                
                addChatMessage(
                    ChatMessage(
                        text = "Exception loading model: ${e.message}",
                        isFromUser = false
                    )
                )
            }
        }
    }

    fun selectFeature(feature: AIFeatureType) {
        selectedFeature.value = feature
        Log.d(TAG, "Selected feature: ${feature.label}")
    }

    // Chat feature methods
    fun sendChatMessage(text: String, image: Bitmap? = null) {
        if (text.isBlank() || !isModelLoaded()) return
        
        // Add user message
        addChatMessage(
            ChatMessage(
                text = text,
                image = image,
                isFromUser = true,
                messageType = if (image != null) MessageType.MULTIMODAL else MessageType.TEXT_ONLY
            )
        )
        
        // Generate response using existing ChatCommand logic
        isProcessing.value = true
        
        viewModelScope.launch {
            try {
                val images = if (image != null) listOf(image) else emptyList()
                
                var currentResponse = ""
                var responseMessage: ChatMessage? = null
                
                val result = mediaPipeLLMService.generateResponse(
                    prompt = text,
                    images = images,
                    onPartialResult = { partialText ->
                        currentResponse = partialText
                        if (responseMessage == null) {
                            responseMessage = ChatMessage(
                                text = partialText,
                                isFromUser = false,
                                messageType = if (image != null) MessageType.MULTIMODAL else MessageType.TEXT_ONLY
                            )
                            addChatMessage(responseMessage!!)
                        } else {
                            updateLastChatMessage(partialText)
                        }
                    }
                )
                
                result.fold(
                    onSuccess = { finalResponse ->
                        if (responseMessage == null) {
                            addChatMessage(
                                ChatMessage(
                                    text = finalResponse,
                                    isFromUser = false,
                                    messageType = if (image != null) MessageType.MULTIMODAL else MessageType.TEXT_ONLY
                                )
                            )
                        }
                        isProcessing.value = false
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Chat failed", error)
                        addChatMessage(
                            ChatMessage(
                                text = "Error: ${error.message}",
                                isFromUser = false
                            )
                        )
                        isProcessing.value = false
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Chat exception", e)
                addChatMessage(
                    ChatMessage(
                        text = "Error: ${e.message}",
                        isFromUser = false
                    )
                )
                isProcessing.value = false
            }
        }
    }

    // Prompt Lab methods
    fun selectPromptTemplate(template: PromptTemplate) {
        selectedTemplate.value = template
        templateParameters.value = template.parameters.associate { 
            it.key to it.defaultValue 
        }
        Log.d(TAG, "Selected prompt template: ${template.type.label}")
    }

    fun updateTemplateParameter(key: String, value: String) {
        val current = templateParameters.value.toMutableMap()
        current[key] = value
        templateParameters.value = current
    }

    fun executePromptTemplate(userInput: String) {
        val template = selectedTemplate.value ?: return
        if (!isModelLoaded() || userInput.isBlank()) return
        
        isProcessing.value = true
        
        val command = PromptLabCommand(
            mediaPipeLLMService = mediaPipeLLMService,
            promptTemplate = template,
            userInput = userInput,
            templateParameters = templateParameters.value,
            onResult = { result ->
                val currentResults = promptLabResults.value.toMutableList()
                currentResults.add(0, result) // Add to beginning
                promptLabResults.value = currentResults
                isProcessing.value = false
                Log.d(TAG, "Prompt lab result: ${result.response.take(100)}...")
            },
            onError = { error ->
                Log.e(TAG, "Prompt lab failed", error)
                isProcessing.value = false
            }
        )
        
        command.execute()
    }

    // Audio transcription methods
    fun setTranscriptionMode(mode: TranscriptionMode) {
        transcriptionMode.value = mode
    }

    fun transcribeAudio(audioData: ByteArray) {
        if (!isModelLoaded()) return
        
        isProcessing.value = true
        
        val command = AudioTranscriptionCommand(
            mediaPipeLLMService = mediaPipeLLMService,
            audioData = audioData,
            transcriptionMode = transcriptionMode.value,
            onResult = { transcription ->
                val currentResults = transcriptionResults.value.toMutableList()
                currentResults.add(0, transcription) // Add to beginning
                transcriptionResults.value = currentResults
                isProcessing.value = false
                Log.d(TAG, "Audio transcription result: ${transcription.take(100)}...")
            },
            onError = { error ->
                Log.e(TAG, "Audio transcription failed", error)
                isProcessing.value = false
            }
        )
        
        command.execute()
    }

    // Utility methods
    fun clearChatHistory() {
        chatMessages.value = emptyList()
    }

    fun clearPromptLabResults() {
        promptLabResults.value = emptyList()
    }

    fun clearTranscriptionResults() {
        transcriptionResults.value = emptyList()
    }

    fun resetSession() {
        viewModelScope.launch {
            mediaPipeLLMService.resetSession()
            clearChatHistory()
            clearPromptLabResults()
            clearTranscriptionResults()
        }
    }

    fun unloadModel() {
        mediaPipeLLMService.cleanup()
        currentModelPath.value = null
        clearChatHistory()
        clearPromptLabResults() 
        clearTranscriptionResults()
        selectedFeature.value = null
    }

    private fun isModelLoaded(): Boolean = mediaPipeLLMService.isModelLoaded()

    private fun addChatMessage(message: ChatMessage) {
        val currentMessages = chatMessages.value.toMutableList()
        currentMessages.add(message)
        chatMessages.value = currentMessages
    }

    private fun updateLastChatMessage(newText: String) {
        val currentMessages = chatMessages.value.toMutableList()
        if (currentMessages.isNotEmpty()) {
            val lastMessage = currentMessages.last()
            if (!lastMessage.isFromUser) {
                currentMessages[currentMessages.lastIndex] = lastMessage.copy(text = newText)
                chatMessages.value = currentMessages
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPipeLLMService.cleanup()
    }
}