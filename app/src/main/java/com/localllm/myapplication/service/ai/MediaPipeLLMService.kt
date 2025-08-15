package com.localllm.myapplication.service.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

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
    private var llmSession: LlmInferenceSession? = null
    private var isInitialized = false
    private val shouldStop = AtomicBoolean(false)
    
    suspend fun initialize(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🚀 Initializing MediaPipe LLM with model: $modelPath")
            
            // Verify model file exists
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                return@withContext Result.failure(Exception("Model file not found: $modelPath"))
            }
            
            val fileSizeGB = modelFile.length() / 1024.0 / 1024.0 / 1024.0
            Log.d(TAG, "📦 Model file size: ${String.format("%.2f", fileSizeGB)} GB")
            
            // Clean up existing instances
            llmSession?.close()
            llmInference?.close()
            
            // Create MediaPipe LLM Inference options (like the working Gallery app)
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .setPreferredBackend(LlmInference.Backend.GPU) // Try GPU first
                .setMaxNumImages(MAX_IMAGE_COUNT)
                .build()
            
            Log.d(TAG, "🔧 Creating LLM Inference instance...")
            // Create LLM Inference instance (this handles large model loading internally)
            llmInference = LlmInference.createFromOptions(context, options)
            
            Log.d(TAG, "🔧 Creating LLM Session...")
            // Create session with proper parameters
            llmSession = LlmInferenceSession.createFromOptions(
                llmInference,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(DEFAULT_TOP_K)
                    .setTopP(DEFAULT_TOP_P)
                    .setTemperature(DEFAULT_TEMPERATURE)
                    .build()
            )
            
            isInitialized = true
            Log.i(TAG, "✅ MediaPipe LLM initialized successfully with ${String.format("%.2f", fileSizeGB)} GB model")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize MediaPipe LLM", e)
            isInitialized = false
            Result.failure(e)
        }
    }
    
    suspend fun generateResponse(
        prompt: String, 
        images: List<Bitmap> = emptyList(),
        onPartialResult: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized || llmSession == null) {
                return@withContext Result.failure(Exception("LLM not initialized"))
            }
            
            Log.d(TAG, "🤖 Generating response with MediaPipe LLM for: ${prompt.take(50)}...")
            shouldStop.set(false)
            
            // Add text query
            if (prompt.trim().isNotEmpty()) {
                llmSession!!.addQueryChunk(prompt)
                Log.d(TAG, "📝 Added query chunk to session")
            }
            
            // Add images if any (following Gallery app pattern)
            for (image in images) {
                // Convert Bitmap to MediaPipe Image format if needed
                // For now, skip image support to focus on text generation
            }
            
            // Use suspendCancellableCoroutine to properly wait for async response
            val finalResponse = suspendCancellableCoroutine { continuation ->
                val responseBuilder = StringBuilder()
                var hasDeliveredAnyContent = false
                
                Log.d(TAG, "🚀 Starting async generation...")
                llmSession!!.generateResponseAsync { partialResult, done ->
                    if (shouldStop.get()) {
                        if (continuation.isActive) {
                            continuation.resume("Generation cancelled")
                        }
                        return@generateResponseAsync
                    }
                    
                    partialResult?.let { result ->
                        if (result.isNotBlank()) {
                            responseBuilder.append(result)
                            hasDeliveredAnyContent = true
                            onPartialResult?.invoke(result)
                            Log.d(TAG, "📝 LLM Partial Response: '$result'")
                        } else {
                            Log.d(TAG, "📝 LLM Partial Response: (empty - skipped)")
                        }
                    }
                    
                    if (done) {
                        Log.d(TAG, "✅ MediaPipe LLM response generation completed")
                        val response = responseBuilder.toString().trim()
                        Log.d(TAG, "📄 Final LLM Response (${response.length} chars): '$response'")
                        
                        // Always send the complete response once at the end if we have content
                        if (response.isNotBlank() && !hasDeliveredAnyContent) {
                            onPartialResult?.invoke(response)
                            Log.d(TAG, "📦 Delivered complete final response to UI")
                        } else if (response.isNotBlank()) {
                            Log.d(TAG, "📦 Final response already delivered via streaming")
                        } else {
                            Log.w(TAG, "⚠️ No response content generated")
                        }
                        
                        if (continuation.isActive) {
                            continuation.resume(response)
                        }
                    }
                }
                
                // Handle cancellation
                continuation.invokeOnCancellation {
                    shouldStop.set(true)
                }
            }
            
            Result.success(finalResponse)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to generate response with MediaPipe LLM", e)
            Result.failure(e)
        }
    }
    
    fun cancelGeneration() {
        Log.d(TAG, "Canceling generation")
        shouldStop.set(true)
    }
    
    fun isGenerating(): Boolean {
        return !shouldStop.get() && isInitialized
    }
    
    suspend fun resetSession(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Resetting MediaPipe LLM session")
            shouldStop.set(true)
            
            // Create new session with same parameters
            if (llmInference != null) {
                llmSession?.close()
                
                llmSession = LlmInferenceSession.createFromOptions(
                    llmInference,
                    LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTopK(DEFAULT_TOP_K)
                        .setTopP(DEFAULT_TOP_P)
                        .setTemperature(DEFAULT_TEMPERATURE)
                        .build()
                )
                
                Log.d(TAG, "MediaPipe LLM session reset successfully")
                Result.success(Unit)
            } else {
                Result.failure(Exception("No inference instance available"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset MediaPipe LLM session", e)
            Result.failure(e)
        }
    }
    
    fun cleanup() {
        Log.d(TAG, "Cleaning up MediaPipe LLM")
        shouldStop.set(true)
        
        try {
            llmSession?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LLM session", e)
        }
        
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LLM inference", e)
        }
        
        llmSession = null
        llmInference = null
        isInitialized = false
    }
    
    fun isModelLoaded(): Boolean = isInitialized && llmInference != null && llmSession != null
}