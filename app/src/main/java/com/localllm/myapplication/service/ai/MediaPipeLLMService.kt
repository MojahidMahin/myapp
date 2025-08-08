package com.localllm.myapplication.service.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaPipeLLMService(private val context: Context) {
    
    companion object {
        private const val TAG = "MediaPipeLLMService"
        private const val MAX_TOKENS = 1024
        private const val DEFAULT_TEMPERATURE = 0.8f
        private const val DEFAULT_TOP_K = 40
        private const val DEFAULT_TOP_P = 0.95f
        private const val MAX_IMAGE_COUNT = 4
    }
    
    private var llmInference: LlmInference? = null
    private var session: LlmInferenceSession? = null
    private var isInitialized = false
    
    suspend fun initialize(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing MediaPipe LLM with model: $modelPath")
            
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .setMaxNumImages(MAX_IMAGE_COUNT)
                .build()
                
            llmInference = LlmInference.createFromOptions(context, options)
            
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(DEFAULT_TOP_K)
                .setTopP(DEFAULT_TOP_P)
                .setTemperature(DEFAULT_TEMPERATURE)
                .setGraphOptions(
                    com.google.mediapipe.tasks.genai.llminference.GraphOptions.builder()
                        .setEnableVisionModality(true)
                        .build()
                )
                .build()
                
            session = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
            isInitialized = true
            
            Log.d(TAG, "MediaPipe LLM initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe LLM", e)
            Result.failure(e)
        }
    }
    
    suspend fun generateResponse(
        prompt: String, 
        images: List<Bitmap> = emptyList(),
        onPartialResult: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized || session == null) {
            return@withContext Result.failure(Exception("MediaPipe LLM not initialized"))
        }
        
        try {
            Log.d(TAG, "Generating response for prompt: ${prompt.take(50)}...")
            if (images.isNotEmpty()) {
                Log.d(TAG, "Including ${images.size} image(s)")
            }
            
            val currentSession = session!!
            
            // Add text query first
            if (prompt.trim().isNotEmpty()) {
                currentSession.addQueryChunk(prompt)
            }
            
            // Add images using BitmapImageBuilder
            for (image in images) {
                val mpImage = BitmapImageBuilder(image).build()
                currentSession.addImage(mpImage)
            }
            
            // Generate response
            val responseBuilder = StringBuilder()
            var isComplete = false
            
            val resultListener: (String, Boolean) -> Unit = { partialResult, done ->
                responseBuilder.append(partialResult)
                onPartialResult?.invoke(responseBuilder.toString())
                
                if (done) {
                    isComplete = true
                }
            }
            
            currentSession.generateResponseAsync(resultListener)
            
            // Wait for completion
            while (!isComplete) {
                Thread.sleep(50)
            }
            
            val finalResponse = responseBuilder.toString()
            Log.d(TAG, "Generated response: ${finalResponse.take(100)}...")
            
            Result.success(finalResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate response", e)
            Result.failure(e)
        }
    }
    
    suspend fun resetSession(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            session?.close()
            
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(DEFAULT_TOP_K)
                .setTopP(DEFAULT_TOP_P)
                .setTemperature(DEFAULT_TEMPERATURE)
                .setGraphOptions(
                    com.google.mediapipe.tasks.genai.llminference.GraphOptions.builder()
                        .setEnableVisionModality(true)
                        .build()
                )
                .build()
                
            session = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
            Log.d(TAG, "Session reset successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset session", e)
            Result.failure(e)
        }
    }
    
    fun cleanup() {
        try {
            session?.close()
            llmInference?.close()
            session = null
            llmInference = null
            isInitialized = false
            Log.d(TAG, "MediaPipe LLM cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    fun isModelLoaded(): Boolean = isInitialized
}