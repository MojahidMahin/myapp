package com.localllm.myapplication.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hybrid LLM repository that tries MediaPipe first, then falls back to TensorFlow Lite
 * This provides better reliability when MediaPipe has compatibility issues
 */
class HybridLLMRepository(private val context: Context) : LLMRepository {
    
    companion object {
        private const val TAG = "HybridLLMRepository"
    }
    
    private val mediaPipeRepository = MediaPipeLLMRepository(context)
    private val tensorFlowLiteRepository = TensorFlowLiteLLMRepository(context)
    
    private var activeRepository: LLMRepository = mediaPipeRepository
    private var usingFallback = false
    private var currentModelPath: String? = null
    
    override suspend fun loadModel(modelPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "🌍 Universal hybrid model loading for all devices")
            Log.d(TAG, "Model path: $modelPath")
            currentModelPath = modelPath
            
            // Universal device detection
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
            val deviceCategory = when {
                maxMemory > 1024 -> "High-end"
                maxMemory > 512 -> "Mid-range"
                else -> "Budget"
            }
            
            Log.d(TAG, "📱 Device: $deviceCategory (${maxMemory}MB heap)")
            
            // Check model type for optimization
            val isGemma3N = modelPath.contains("gemma-3n", ignoreCase = true) || 
                           modelPath.contains("gemma3n", ignoreCase = true)
            
            if (isGemma3N) {
                Log.d(TAG, "Detected Gemma3N model - optimizing for multimodal support")
            }
            
            // Universal MediaPipe attempt (optimized for all devices)
            Log.d(TAG, "🚀 Trying MediaPipe LLM repository - UNIVERSAL MODE")
            Log.d(TAG, "🎯 Target: Load model on $deviceCategory device (${maxMemory}MB)")
            
            val mediaPipeSuccess = try {
                Log.d(TAG, "📍 MediaPipe attempt: Universal loading for $deviceCategory device")
                val success = mediaPipeRepository.loadModel(modelPath)
                if (success) {
                    Log.d(TAG, "🎉 MediaPipe successfully loaded model on $deviceCategory device!")
                } else {
                    Log.w(TAG, "MediaPipe returned false (but no exception) on $deviceCategory device")
                }
                success
            } catch (e: Exception) {
                Log.w(TAG, "MediaPipe failed on $deviceCategory device (will try fallback)", e)
                Log.w(TAG, "Exception details: ${e.javaClass.simpleName}: ${e.message}")
                false
            }
            
            if (mediaPipeSuccess) {
                Log.d(TAG, "✅ MediaPipe successfully loaded the model on $deviceCategory device")
                activeRepository = mediaPipeRepository
                usingFallback = false
                return@withContext true
            }
            
            // Universal fallback analysis for all devices
            Log.w(TAG, "MediaPipe failed to load model on $deviceCategory device")
            if (isGemma3N) {
                Log.w(TAG, "Large model detection: This may exceed $deviceCategory device limits")
                Log.w(TAG, "Universal recommendations:")
                Log.w(TAG, "1. Use smaller quantized models (<2GB)")
                Log.w(TAG, "2. Try Gemma 2B for better compatibility")
                Log.w(TAG, "3. Consider TensorFlow Lite fallback")
            } else {
                Log.w(TAG, "Model loading failed - trying universal TensorFlow Lite fallback")
            }
            
            Log.w(TAG, "Trying universal TensorFlow Lite fallback for $deviceCategory device...")
            val tensorFlowSuccess = try {
                tensorFlowLiteRepository.loadModel(modelPath)
            } catch (e: Exception) {
                Log.e(TAG, "TensorFlow Lite fallback also failed on $deviceCategory device", e)
                false
            }
            
            if (tensorFlowSuccess) {
                Log.d(TAG, "✅ TensorFlow Lite fallback successfully loaded model on $deviceCategory device")
                activeRepository = tensorFlowLiteRepository
                usingFallback = true
                return@withContext true
            }
            
            Log.e(TAG, "❌ Both MediaPipe and TensorFlow Lite failed on $deviceCategory device")
            Log.e(TAG, "Universal recommendation: Use smaller models (<2GB) for better compatibility")
            false
        }
    }
    
    override suspend fun generateTextResponse(prompt: String): String {
        return withContext(Dispatchers.IO) {
            if (usingFallback) {
                Log.d(TAG, "Using TensorFlow Lite fallback for text generation")
            } else {
                Log.d(TAG, "Using MediaPipe for text generation")
            }
            
            try {
                activeRepository.generateTextResponse(prompt)
            } catch (e: Exception) {
                Log.e(TAG, "Active repository failed, trying to switch", e)
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}, message: ${e.message}")
                
                // Check if this is a MediaPipe compatibility error
                val isMediaPipeCompatibilityError = e.message?.contains("POSITION_ENCODING_NOT_SUPPORTED") == true ||
                                                  e.message?.contains("input_pos != nullptr") == true ||
                                                  e.message?.contains("Input name args_1 was not found") == true ||
                                                  e.message?.contains("Calculator::Open() for node") == true
                
                // Try switching repositories if current one fails
                if (!usingFallback && isMediaPipeCompatibilityError) {
                    Log.w(TAG, "MediaPipe compatibility error detected, switching to TensorFlow Lite")
                    
                    // Need to ensure TensorFlowLite has the model loaded
                    val modelLoaded = if (!tensorFlowLiteRepository.isModelLoaded()) {
                        Log.d(TAG, "Loading model in TensorFlow Lite fallback repository...")
                        // Get the model path from MediaPipe (we need to store it)
                        val success = try {
                            tensorFlowLiteRepository.loadModel(getCurrentModelPath())
                        } catch (loadE: Exception) {
                            Log.e(TAG, "Failed to load model in TensorFlow Lite fallback", loadE)
                            false
                        }
                        success
                    } else {
                        true
                    }
                    
                    if (modelLoaded) {
                        activeRepository = tensorFlowLiteRepository
                        usingFallback = true
                        tensorFlowLiteRepository.generateTextResponse(prompt)
                    } else {
                        Log.e(TAG, "Failed to switch to TensorFlow Lite - model not loaded")
                        
                        // Provide helpful error message for large model issues
                        throw IllegalStateException("🚫 MODEL TOO LARGE FOR MOBILE DEVICE\n\nYour Gemma3N model (3.14GB) exceeds mobile memory limits.\n\n💡 SOLUTIONS:\n• Use Gemma 2B (works perfectly for text)\n• Use a smaller quantized model (<2GB)\n• Run on desktop/server with more RAM\n\nEven your Snapdragon 695 + 16GB RAM can't load 3.14GB models due to Android heap limits!")
                    }
                } else if (!usingFallback) {
                    // Not a known compatibility error, but still try fallback
                    Log.w(TAG, "Unknown MediaPipe error, attempting TensorFlow Lite fallback anyway")
                    val modelLoaded = if (!tensorFlowLiteRepository.isModelLoaded()) {
                        Log.d(TAG, "Loading model in TensorFlow Lite fallback repository...")
                        val success = try {
                            tensorFlowLiteRepository.loadModel(getCurrentModelPath())
                        } catch (loadE: Exception) {
                            Log.e(TAG, "Failed to load model in TensorFlow Lite fallback", loadE)
                            false
                        }
                        success
                    } else {
                        true
                    }
                    
                    if (modelLoaded) {
                        activeRepository = tensorFlowLiteRepository
                        usingFallback = true
                        tensorFlowLiteRepository.generateTextResponse(prompt)
                    } else {
                        Log.e(TAG, "Failed to switch to TensorFlow Lite - model not loaded")
                        
                        // Provide helpful error message for large model issues
                        throw IllegalStateException("🚫 MODEL TOO LARGE FOR MOBILE DEVICE\n\nYour Gemma3N model (3.14GB) exceeds mobile memory limits.\n\n💡 SOLUTIONS:\n• Use Gemma 2B (works perfectly for text)\n• Use a smaller quantized model (<2GB)\n• Run on desktop/server with more RAM\n\nEven your Snapdragon 695 + 16GB RAM can't load 3.14GB models due to Android heap limits!")
                    }
                } else {
                    throw e
                }
            }
        }
    }
    
    override suspend fun generateMultimodalResponse(prompt: String, image: Bitmap): String {
        return withContext(Dispatchers.IO) {
            if (usingFallback) {
                Log.d(TAG, "Using TensorFlow Lite fallback for multimodal generation")
            } else {
                Log.d(TAG, "Using MediaPipe for multimodal generation")
            }
            
            try {
                activeRepository.generateMultimodalResponse(prompt, image)
            } catch (e: Exception) {
                Log.e(TAG, "Active repository failed during multimodal inference", e)
                
                // Try switching repositories if current one fails
                if (!usingFallback) {
                    Log.w(TAG, "MediaPipe failed during multimodal inference, switching to TensorFlow Lite")
                    
                    // Need to ensure TensorFlowLite has the model loaded
                    val modelLoaded = if (!tensorFlowLiteRepository.isModelLoaded()) {
                        Log.d(TAG, "Loading model in TensorFlow Lite fallback repository...")
                        val success = try {
                            tensorFlowLiteRepository.loadModel(getCurrentModelPath())
                        } catch (loadE: Exception) {
                            Log.e(TAG, "Failed to load model in TensorFlow Lite fallback", loadE)
                            false
                        }
                        success
                    } else {
                        true
                    }
                    
                    if (modelLoaded) {
                        activeRepository = tensorFlowLiteRepository
                        usingFallback = true
                        tensorFlowLiteRepository.generateMultimodalResponse(prompt, image)
                    } else {
                        Log.e(TAG, "Failed to switch to TensorFlow Lite - model not loaded")
                        
                        // Provide helpful error message for large model issues
                        throw IllegalStateException("🚫 MODEL TOO LARGE FOR MOBILE DEVICE\n\nYour Gemma3N model (3.14GB) exceeds mobile memory limits.\n\n💡 SOLUTIONS:\n• Use Gemma 2B (works perfectly for text)\n• Use a smaller quantized model (<2GB)\n• Run on desktop/server with more RAM\n\nEven your Snapdragon 695 + 16GB RAM can't load 3.14GB models due to Android heap limits!")
                    }
                } else {
                    throw e
                }
            }
        }
    }
    
    override fun isModelLoaded(): Boolean {
        return activeRepository.isModelLoaded()
    }
    
    override fun unloadModel() {
        try {
            mediaPipeRepository.unloadModel()
            tensorFlowLiteRepository.unloadModel()
            activeRepository = mediaPipeRepository
            usingFallback = false
            Log.d(TAG, "All repositories unloaded, reset to MediaPipe as primary")
        } catch (e: Exception) {
            Log.e(TAG, "Error during unloadModel", e)
        }
    }
    
    fun isUsingFallback(): Boolean = usingFallback
    
    fun getCurrentRepositoryType(): String {
        return if (usingFallback) "TensorFlow Lite (Fallback)" else "MediaPipe (Primary)"
    }
    
    private fun getCurrentModelPath(): String {
        return currentModelPath ?: throw IllegalStateException("No model path available")
    }
}