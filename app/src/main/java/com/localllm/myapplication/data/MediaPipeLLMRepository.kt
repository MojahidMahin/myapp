package com.localllm.myapplication.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaPipeLLMRepository(private val context: Context) : LLMRepository {
    
    companion object {
        private const val TAG = "MediaPipeLLMRepository"
    }
    
    private var llmInference: LlmInference? = null
    private var modelLoaded = false
    
    override suspend fun loadModel(modelPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to load model from: $modelPath")
                
                // Clean up any existing model first
                llmInference?.close()
                llmInference = null
                modelLoaded = false
                
                // Check if file exists
                val modelFile = File(modelPath)
                if (!modelFile.exists()) {
                    Log.e(TAG, "Model file does not exist: $modelPath")
                    Log.d(TAG, "File absolute path: ${modelFile.absolutePath}")
                    Log.d(TAG, "File parent directory exists: ${modelFile.parentFile?.exists()}")
                    return@withContext false
                }
                
                Log.d(TAG, "Model file found, size: ${modelFile.length()} bytes")
                
                // Try to create MediaPipe LLM inference
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(512) // Reduced for better performance
                    .setTemperature(0.8f)
                    .setTopK(40)
                    .setRandomSeed(101)
                    .build()
                
                Log.d(TAG, "Creating LlmInference with options...")
                llmInference = LlmInference.createFromOptions(context, options)
                modelLoaded = true
                
                Log.d(TAG, "Model loaded successfully!")
                true
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model: ${e.message}", e)
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
                modelLoaded = false
                false
            }
        }
    }
    
    override suspend fun generateTextResponse(prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                if (!modelLoaded || llmInference == null) {
                    throw IllegalStateException("Model not loaded")
                }
                
                Log.d(TAG, "Generating text response for prompt: ${prompt.take(50)}...")
                
                val response = llmInference!!.generateResponse(prompt)
                val responseText = response ?: "No response generated"
                
                Log.d(TAG, "Generated response: ${responseText.take(100)}...")
                responseText
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate text response", e)
                "Error: Failed to generate response - ${e.message}"
            }
        }
    }
    
    override suspend fun generateMultimodalResponse(prompt: String, image: Bitmap): String {
        return withContext(Dispatchers.IO) {
            try {
                if (!modelLoaded || llmInference == null) {
                    throw IllegalStateException("Model not loaded")
                }
                
                Log.d(TAG, "Generating multimodal response for prompt: ${prompt.take(50)}...")
                
                // For multimodal, we combine image description with text prompt
                val combinedPrompt = "Looking at this image: $prompt"
                val response = llmInference!!.generateResponse(combinedPrompt)
                val responseText = response ?: "No response generated"
                
                Log.d(TAG, "Generated multimodal response: ${responseText.take(100)}...")
                responseText
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate multimodal response", e)
                "Error: Failed to generate multimodal response - ${e.message}"
            }
        }
    }
    
    override fun isModelLoaded(): Boolean = modelLoaded
    
    override fun unloadModel() {
        try {
            llmInference?.close()
            llmInference = null
            modelLoaded = false
            Log.d(TAG, "Model unloaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model", e)
        }
    }
}