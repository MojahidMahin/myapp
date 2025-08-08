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
import com.localllm.myapplication.service.ModelManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AIGalleryViewModel(
    private val context: Context,
    private val modelManager: ModelManager
) : ViewModel() {

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
    
    init {
        // Observe model manager state
        viewModelScope.launch {
            modelManager.isModelLoaded.collectLatest { loaded ->
                if (loaded && chatMessages.value.isEmpty()) {
                    addChatMessage(
                        ChatMessage(
                            text = "Welcome to AI Gallery! Model is loaded and ready to use. Select a feature to begin.",
                            isFromUser = false
                        )
                    )
                } else if (!loaded && chatMessages.value.isEmpty()) {
                    addChatMessage(
                        ChatMessage(
                            text = "Welcome to AI Gallery! Please load a model using the 'Load Model' button to start using AI features.",
                            isFromUser = false
                        )
                    )
                }
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
            modelManager.generationInProgress.collectLatest { processing ->
                isProcessing.value = processing
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

    fun selectFeature(feature: AIFeatureType) {
        selectedFeature.value = feature
        Log.d(TAG, "Selected feature: ${feature.label}")
    }
    
    fun goBackToFeatureSelection() {
        selectedFeature.value = null
    }
    
    fun stopGeneration() {
        modelManager.stopGeneration()
    }

    // Chat feature methods
    fun sendChatMessage(text: String, image: Bitmap? = null) {
        if (text.isBlank() || !modelManager.isModelLoaded.value) return
        
        // Add user message
        addChatMessage(
            ChatMessage(
                text = text,
                image = image,
                isFromUser = true,
                messageType = if (image != null) MessageType.MULTIMODAL else MessageType.TEXT_ONLY
            )
        )
        
        // Generate response using ModelManager
        isProcessing.value = true
        
        val images = if (image != null) listOf(image) else emptyList()
        var responseMessage: ChatMessage? = null
        
        modelManager.generateResponse(
            prompt = text,
            images = images,
            onPartialResult = { partialText ->
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
        ) { result ->
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
        if (!modelManager.isModelLoaded.value || userInput.isBlank()) return
        
        isProcessing.value = true
        
        val fullPrompt = template.buildPrompt(userInput, templateParameters.value)
        val startTime = System.currentTimeMillis()
        
        modelManager.generateResponse(
            prompt = fullPrompt,
            images = emptyList()
        ) { result ->
            result.fold(
                onSuccess = { response ->
                    val endTime = System.currentTimeMillis()
                    val promptLabResult = PromptLabResult(
                        template = template,
                        userInput = userInput,
                        fullPrompt = fullPrompt,
                        response = response,
                        latencyMs = endTime - startTime,
                        parameters = templateParameters.value
                    )
                    
                    val currentResults = promptLabResults.value.toMutableList()
                    currentResults.add(0, promptLabResult)
                    promptLabResults.value = currentResults
                    isProcessing.value = false
                    Log.d(TAG, "Prompt lab result: ${response.take(100)}...")
                },
                onFailure = { error ->
                    Log.e(TAG, "Prompt lab failed", error)
                    isProcessing.value = false
                }
            )
        }
    }

    // Audio transcription methods
    fun setTranscriptionMode(mode: TranscriptionMode) {
        transcriptionMode.value = mode
    }

    fun transcribeAudio(audioData: ByteArray) {
        if (!modelManager.isModelLoaded.value) return
        
        isProcessing.value = true
        
        val prompt = when (transcriptionMode.value) {
            TranscriptionMode.TRANSCRIBE_ONLY -> "Please transcribe the following audio:"
            TranscriptionMode.TRANSCRIBE_AND_TRANSLATE -> "Please transcribe and translate the following audio to English:"
            TranscriptionMode.SUMMARIZE -> "Please transcribe the following audio and provide a brief summary:"
        }
        
        modelManager.generateResponse(
            prompt = prompt,
            images = emptyList()
        ) { result ->
            result.fold(
                onSuccess = { transcription ->
                    val currentResults = transcriptionResults.value.toMutableList()
                    currentResults.add(0, transcription)
                    transcriptionResults.value = currentResults
                    isProcessing.value = false
                    Log.d(TAG, "Audio transcription result: ${transcription.take(100)}...")
                },
                onFailure = { error ->
                    Log.e(TAG, "Audio transcription failed", error)
                    isProcessing.value = false
                }
            )
        }
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
        modelManager.resetSession()
        clearChatHistory()
        clearPromptLabResults()
        clearTranscriptionResults()
    }

    fun unloadModel() {
        modelManager.unloadModel()
        clearChatHistory()
        clearPromptLabResults() 
        clearTranscriptionResults()
        selectedFeature.value = null
    }

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
        // ModelManager is singleton and handles its own cleanup
    }
}