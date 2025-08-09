package com.localllm.myapplication.service.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private var session: LlmInferenceSession? = null
    private var isInitialized = false
    
    // Simple cancellation flag - minimal change to avoid crashes
    private val shouldStop = AtomicBoolean(false)
    
    suspend fun initialize(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing MediaPipe LLM with model: $modelPath")
            
            // Clean up existing resources first
            cleanup()
            
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .setMaxNumImages(MAX_IMAGE_COUNT)
                // Temporarily disable graph options
                // .setGraphOptions(
                //     com.google.mediapipe.tasks.genai.llminference.GraphOptions.builder()
                //         .setEnableVisionModality(true)
                //         .build()
                // )
                .build()
                
            llmInference = LlmInference.createFromOptions(context, options)
            
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(DEFAULT_TOP_K)
                .setTopP(DEFAULT_TOP_P)
                .setTemperature(DEFAULT_TEMPERATURE)
                // Temporarily disable graph options
                // .setGraphOptions(
                //     com.google.mediapipe.tasks.genai.llminference.GraphOptions.builder()
                //         .setEnableVisionModality(true)
                //         .build()
                // )
                .build()
                
            session = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
            isInitialized = true
            
            // Force garbage collection after heavy initialization
            System.gc()
            
            Log.d(TAG, "MediaPipe LLM initialized successfully")
            Result.success(Unit)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory during MediaPipe LLM initialization", e)
            cleanup()
            Result.failure(Exception("Insufficient memory to load model", e))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe LLM", e)
            cleanup()
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
            
            // Reset stop flag for new generation
            shouldStop.set(false)
            
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
            
            // Wait for completion - keep original logic but add simple cancellation check
            while (!isComplete && !shouldStop.get()) {
                Thread.sleep(50)
            }
            
            if (shouldStop.get()) {
                Log.d(TAG, "Generation was stopped by user")
                return@withContext Result.failure(Exception("Generation was cancelled"))
            }
            
            val finalResponse = responseBuilder.toString()
            Log.d(TAG, "Generated response: ${finalResponse.take(100)}...")
            
            Result.success(finalResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate response", e)
            Result.failure(e)
        }
    }
    
    /**
     * Stop current generation
     */
    fun cancelGeneration() {
        Log.d(TAG, "Cancelling current generation")
        shouldStop.set(true)
    }
    
    /**
     * Check if generation is in progress
     */
    fun isGenerating(): Boolean {
        return !shouldStop.get() && isInitialized
    }
    
    suspend fun resetSession(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Stop any ongoing generation first
            shouldStop.set(true)
            Thread.sleep(100) // Give time for generation to stop
            
            session?.close()
            
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(DEFAULT_TOP_K)
                .setTopP(DEFAULT_TOP_P)
                .setTemperature(DEFAULT_TEMPERATURE)
                // Temporarily disable graph options
                // .setGraphOptions(
                //     com.google.mediapipe.tasks.genai.llminference.GraphOptions.builder()
                //         .setEnableVisionModality(true)
                //         .build()
                // )
                .build()
                
            session = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
            shouldStop.set(false)
            Log.d(TAG, "Session reset successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset session", e)
            Result.failure(e)
        }
    }
    
    fun cleanup() {
        try {
            Log.d(TAG, "Starting MediaPipe LLM cleanup")
            shouldStop.set(true)
            
            // Give time for any ongoing operations to stop
            Thread.sleep(100)
            
            // Clean up session first
            session?.let { s ->
                try {
                    s.close()
                    Log.d(TAG, "Session closed")
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing session", e)
                }
            }
            session = null
            
            // Clean up LLM inference
            llmInference?.let { llm ->
                try {
                    llm.close()
                    Log.d(TAG, "LLM inference closed")
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing LLM inference", e)
                }
            }
            llmInference = null
            
            isInitialized = false
            
            // Force garbage collection to free memory
            System.gc()
            
            Log.d(TAG, "MediaPipe LLM cleaned up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    fun isModelLoaded(): Boolean = isInitialized
}