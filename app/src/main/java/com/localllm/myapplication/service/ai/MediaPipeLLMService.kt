package com.localllm.myapplication.service.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        private const val MAX_IMAGE_COUNT = 8
    }
    
    private var llmInference: LlmInference? = null
    private var llmSession: LlmInferenceSession? = null
    private var isInitialized = false
    private val shouldStop = AtomicBoolean(false)
    
    // Mutex to serialize access to the LLM session (MediaPipe can't handle concurrent requests)
    private val sessionMutex = Mutex()
    
    
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
            
            // Create MediaPipe LLM Inference options with vision support
            Log.d(TAG, "🔧 Configuring LLM options with vision support...")
            Log.d(TAG, "   - Model path: $modelPath")
            Log.d(TAG, "   - Max tokens: $MAX_TOKENS")
            Log.d(TAG, "   - Max images: $MAX_IMAGE_COUNT")
            Log.d(TAG, "   - Backend: GPU")
            
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .setPreferredBackend(LlmInference.Backend.GPU) // Try GPU first
                .setMaxNumImages(MAX_IMAGE_COUNT) // Enable vision modality with higher limit
                .build()
            
            Log.d(TAG, "🔧 Creating LLM Inference instance with vision support...")
            // Create LLM Inference instance (this handles large model loading internally)
            llmInference = LlmInference.createFromOptions(context, options)
            Log.d(TAG, "✅ LLM Inference instance created successfully")
            
            Log.d(TAG, "🔧 Creating LLM Session with vision modality enabled...")
            // Create session with GraphOptions for vision modality (like working Gallery app)
            llmSession = LlmInferenceSession.createFromOptions(
                llmInference,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(DEFAULT_TOP_K)
                    .setTopP(DEFAULT_TOP_P)
                    .setTemperature(DEFAULT_TEMPERATURE)
                    .setGraphOptions(
                        GraphOptions.builder()
                            .setEnableVisionModality(true) // KEY: Enable vision modality in session
                            .build()
                    )
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
        // Use mutex to serialize access to the MediaPipe LLM session
        sessionMutex.withLock {
            try {
                if (!isInitialized || llmSession == null) {
                    return@withContext Result.failure(Exception("LLM not initialized"))
                }
                
                Log.d(TAG, "🤖 Generating response with MediaPipe LLM for: ${prompt.take(50)}...")
                Log.d(TAG, "🔒 Acquired session lock for request")
                shouldStop.set(false)
            
            // Prepare basic prompt - image handling will be done separately  
            val enhancedPrompt = prompt
            
            // Add enhanced query to session
            if (enhancedPrompt.trim().isNotEmpty()) {
                llmSession!!.addQueryChunk(enhancedPrompt)
                Log.d(TAG, "📝 Added enhanced query chunk to session (${enhancedPrompt.length} chars)")
                Log.d(TAG, "📝 Query preview: ${enhancedPrompt.take(100)}...")
            }
            
            // Add images to session for multimodal support
            if (images.isNotEmpty()) {
                Log.d(TAG, "📸 Adding ${images.size} image(s) to LLM session for multimodal input")
                for ((index, image) in images.withIndex()) {
                    try {
                        val mpImage: MPImage = BitmapImageBuilder(image).build()
                        llmSession!!.addImage(mpImage)
                        Log.d(TAG, "✅ Added image $index to session (${image.width}x${image.height})")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to add image $index to session", e)
                    }
                }
            } else {
                Log.d(TAG, "📝 No images provided - text-only query")
            }
            
            // Use suspendCancellableCoroutine to properly wait for async response
            val finalResponse = suspendCancellableCoroutine { continuation ->
                val responseBuilder = StringBuilder()
                var hasDeliveredAnyContent = false
                
                // Common response handler
                val responseHandler = { partialResult: String?, done: Boolean ->
                    if (shouldStop.get()) {
                        if (continuation.isActive) {
                            continuation.resume("Generation cancelled")
                        }
                    } else {
                    
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
                }
                
                Log.d(TAG, "🚀 Starting async generation...")
                // Generate response using standard MediaPipe API
                llmSession!!.generateResponseAsync(responseHandler)
                
                // Handle cancellation
                continuation.invokeOnCancellation {
                    shouldStop.set(true)
                }
            }
            
            Log.d(TAG, "🔓 Releasing session lock")
            Result.success(finalResponse)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to generate response with MediaPipe LLM", e)
                Log.d(TAG, "🔓 Releasing session lock due to error")
                Result.failure(e)
            }
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
                        .setGraphOptions(
                            GraphOptions.builder()
                                .setEnableVisionModality(true) // Enable vision modality in reset session too
                                .build()
                        )
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