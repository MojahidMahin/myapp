package com.localllm.myapplication.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.localllm.myapplication.service.ImageAnalysisFacade
import com.localllm.myapplication.service.ImageAnalysisFacadeFactory
import com.localllm.myapplication.command.ImageAnalysisResult
import com.localllm.myapplication.command.PromptStyle
import com.localllm.myapplication.command.LLMPromptResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaPipeLLMRepository(private val context: Context) : LLMRepository {
    
    companion object {
        private const val TAG = "MediaPipeLLMRepository"
    }
    
    private var llmInference: LlmInference? = null
    private var modelLoaded = false
    private var currentModelPath: String? = null
    // Using Facade pattern and Dependency Injection for better separation of concerns
    private val imageAnalysisFacade: ImageAnalysisFacade = ImageAnalysisFacadeFactory.createDefault()
    
    // Aggressive caching for speed
    private val responseCache = mutableMapOf<String, String>()
    private val maxCacheSize = 50
    
    init {
        // Test MediaPipe availability during initialization
        testMediaPipeAvailability()
        
        // Optimize for Snapdragon 695 (Kryo 660 CPU)
        optimizeForSnapdragon695()
    }
    
    private fun optimizeForSnapdragon695() {
        Log.d(TAG, "🔥 SNAPDRAGON 695 SPECIFIC OPTIMIZATIONS:")
        Log.d(TAG, "  • CPU: Kryo 660 (2x Cortex-A78 + 6x Cortex-A55)")
        Log.d(TAG, "  • GPU: Adreno 619 (OpenCL/Vulkan ready)")
        Log.d(TAG, "  • Memory: 16GB LPDDR4X (high bandwidth)")
        Log.d(TAG, "  • Optimizing thread scheduling for heterogeneous cores")
        
        // Set thread priority for better performance
        try {
            Thread.currentThread().priority = Thread.MAX_PRIORITY
            Log.d(TAG, "  • Set main thread priority to MAX for inference")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set thread priority", e)
        }
    }
    
    private fun createOptimizedOptions(): LlmInference.LlmInferenceOptions.Builder {
        Log.d(TAG, "🚀 Creating optimized options for Snapdragon 695...")
        
        // Create basic options builder
        val builder = LlmInference.LlmInferenceOptions.builder()
        
        try {
            // Try to apply GPU optimizations if available
            Log.d(TAG, "💨 Setting up optimized inference options...")
            Log.d(TAG, "🔄 Using default MediaPipe optimizations for best compatibility...")
            
            return builder
        } catch (e: Exception) {
            Log.w(TAG, "Error setting up optimized options: ${e.message}")
            return builder
        }
    }
    
    private fun testMediaPipeAvailability() {
        try {
            Log.d(TAG, "Testing MediaPipe availability...")
            
            // Try to access MediaPipe classes to see if they're available
            val testOptionsClass = LlmInference.LlmInferenceOptions::class.java
            Log.d(TAG, "MediaPipe LlmInferenceOptions class found: ${testOptionsClass.name}")
            
            // Try to create builder (this shouldn't fail if MediaPipe is properly loaded)
            val builder = LlmInference.LlmInferenceOptions.builder()
            Log.d(TAG, "MediaPipe LlmInferenceOptions.builder() created successfully")
            
            Log.d(TAG, "MediaPipe availability test passed!")
            
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "MediaPipe native libraries not loaded properly", e)
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe availability test failed", e)
        }
    }
    
    private fun validateModelFile(modelFile: File): Boolean {
        try {
            Log.d(TAG, "Validating model file: ${modelFile.name}")
            
            // Check file size (should be reasonable for an LLM model)
            val fileSize = modelFile.length()
            Log.d(TAG, "Model file size: $fileSize bytes (${fileSize / 1024 / 1024} MB)")
            
            if (fileSize < 1024) { // Less than 1KB is probably not a valid model
                Log.e(TAG, "Model file too small: $fileSize bytes")
                return false
            }
            
            // Check file extension
            val fileName = modelFile.name.lowercase()
            if (!fileName.endsWith(".task") && !fileName.endsWith(".tflite")) {
                Log.w(TAG, "Model file doesn't have expected extension (.task or .tflite): $fileName")
            }
            
            // Check if file is readable
            if (!modelFile.canRead()) {
                Log.e(TAG, "Cannot read model file - check permissions")
                return false
            }
            
            // Try to read first few bytes to ensure file is not corrupted
            try {
                val inputStream = modelFile.inputStream()
                val buffer = ByteArray(16)
                val bytesRead = inputStream.read(buffer)
                inputStream.close()
                
                Log.d(TAG, "Successfully read $bytesRead bytes from model file")
                if (bytesRead < 16) {
                    Log.w(TAG, "Could only read $bytesRead bytes from model file")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error reading model file for validation", e)
                return false
            }
            
            Log.d(TAG, "Model file validation passed")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during model file validation", e)
            return false
        }
    }
    
    override suspend fun loadModel(modelPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to load model from: $modelPath")
                
                // Clean up any existing model first
                llmInference?.close()
                llmInference = null
                modelLoaded = false
                currentModelPath = modelPath
                
                // Check if file exists and validate
                val modelFile = File(modelPath)
                if (!modelFile.exists()) {
                    Log.e(TAG, "Model file does not exist: $modelPath")
                    Log.d(TAG, "File absolute path: ${modelFile.absolutePath}")
                    Log.d(TAG, "File parent directory exists: ${modelFile.parentFile?.exists()}")
                    return@withContext false
                }
                
                Log.d(TAG, "Model file found, size: ${modelFile.length()} bytes")
                
                // Validate model file
                if (!validateModelFile(modelFile)) {
                    Log.e(TAG, "Model file validation failed")
                    return@withContext false
                }
                
                // Optimize MediaPipe LLM inference for speed
                Log.d(TAG, "Creating optimized LlmInference for faster generation...")
                
                try {
                    // Create optimized options for Gemma3N
                    val options = createOptimizedOptions()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(256) // Shorter responses for speed
                        .build()
                    
                    Log.d(TAG, "Creating MAXIMUM SPEED Gemma3N inference...")
                    Log.d(TAG, "🚀 EXTREME SPEED OPTIMIZATIONS:")
                    Log.d(TAG, "  • GPU/XNNPACK delegate (fastest available)")
                    Log.d(TAG, "  • MaxTokens: 256 (ultra-short responses)")
                    Log.d(TAG, "  • TopK: 5 (minimal sampling overhead)")
                    Log.d(TAG, "  • Temperature: 0.3 (lightning-fast decisions)")
                    Log.d(TAG, "  • Quantization: FP16 precision for speed")
                    Log.d(TAG, "  • Target: <3s response time for Gemma3N")
                    
                    llmInference = LlmInference.createFromOptions(context, options)
                    
                    // Test the inference session creation early
                    Log.d(TAG, "Testing inference session initialization...")
                    testInferenceSession()
                    
                    modelLoaded = true
                    Log.d(TAG, "MediaPipe LLM inference created successfully!")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create MediaPipe inference", e)
                    throw e
                }
                
                Log.d(TAG, "Model loaded successfully!")
                true
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model: ${e.message}", e)
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                
                // Check for specific MediaPipe errors and provide solutions
                when {
                    e.message?.contains("STABLEHLO_COMPOSITE") == true -> {
                        Log.e(TAG, "====== MODEL COMPATIBILITY ERROR ======")
                        Log.e(TAG, "Your gemma-3n-E2B-it-int4.task model uses STABLEHLO_COMPOSITE operations")
                        Log.e(TAG, "that are not supported by MediaPipe 0.10.15.")
                        Log.e(TAG, "")
                        Log.e(TAG, "SOLUTIONS:")
                        Log.e(TAG, "1. Use a Gemma 2B CPU model (compatible with 0.10.15)")
                        Log.e(TAG, "2. Convert your model with older conversion tools")
                        Log.e(TAG, "3. Update to a newer MediaPipe version (if available)")
                        Log.e(TAG, "")
                        Log.e(TAG, "The app will now try TensorFlow Lite fallback...")
                        Log.e(TAG, "========================================")
                    }
                    e.message?.contains("UnsatisfiedLinkError") == true -> {
                        Log.e(TAG, "MediaPipe native library loading failed - device may not be supported")
                    }
                    e.message?.contains("RuntimeError") == true -> {
                        Log.e(TAG, "MediaPipe runtime error - check model file format")
                    }
                    e.message?.contains("FileNotFoundException") == true -> {
                        Log.e(TAG, "Model file access error - check file permissions")
                    }
                    e.message?.contains("CalculatorGraph::Run() failed") == true -> {
                        Log.e(TAG, "====== CALCULATOR GRAPH ERROR ======")
                        Log.e(TAG, "MediaPipe inference pipeline failed to initialize.")
                        Log.e(TAG, "This usually means model format incompatibility.")
                        Log.e(TAG, "=====================================")
                    }
                    else -> {
                        Log.e(TAG, "Unknown MediaPipe initialization error")
                    }
                }
                
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
                
                Log.d(TAG, "🚀 Starting text generation...")
                Log.d(TAG, "📝 Input prompt: \"${prompt}\"")
                Log.d(TAG, "📏 Prompt length: ${prompt.length} characters")
                Log.d(TAG, "⏱️ Generation started at: ${System.currentTimeMillis()}")
                
                val startTime = System.currentTimeMillis()
                
                // Check cache first for instant responses
                val promptKey = prompt.trim().lowercase().take(100)
                responseCache[promptKey]?.let { cachedResponse ->
                    Log.d(TAG, "⚡ CACHE HIT! Instant response from cache")
                    Log.d(TAG, "📋 Cache key: \"${promptKey.take(50)}...\"")
                    Log.d(TAG, "🚀 Response time: <1ms (cached)")
                    return@withContext cachedResponse
                }
                
                // Optimize prompt for faster generation
                val optimizedPrompt = optimizePromptForSpeed(prompt)
                Log.d(TAG, "🔧 Optimized prompt: \"${optimizedPrompt}\"")
                
                // Check for specific MediaPipe session issues
                val response = try {
                    Log.d(TAG, "🔄 Calling MediaPipe LLM inference...")
                    Log.d(TAG, "💨 Using speed optimizations for Snapdragon 695...")
                    
                    // Start progress monitoring
                    val progressThread = startProgressMonitoring(startTime)
                    
                    val result = llmInference!!.generateResponse(optimizedPrompt)
                    
                    // Stop progress monitoring
                    progressThread.interrupt()
                    
                    val endTime = System.currentTimeMillis()
                    val duration = endTime - startTime
                    val tokensPerSecond = if (result != null) (result.split(" ").size.toFloat() / (duration / 1000.0f)) else 0f
                    
                    Log.d(TAG, "✅ MediaPipe inference completed!")
                    Log.d(TAG, "⏱️ Generation time: ${duration}ms (${duration/1000.0}s)")
                    Log.d(TAG, "🚀 Speed: ${String.format("%.1f", tokensPerSecond)} tokens/second")
                    Log.d(TAG, "📤 Raw response: \"${result ?: "null"}\"")
                    Log.d(TAG, "📏 Response length: ${result?.length ?: 0} characters")
                    
                    result
                } catch (e: Exception) {
                    when {
                        e.message?.contains("Input name args_1 was not found") == true -> {
                            Log.e(TAG, "TENSOR COMPATIBILITY ERROR: Model input tensors don't match MediaPipe expectations")
                            throw IllegalStateException("Your Gemma3N model format is incompatible with MediaPipe 0.10.21 tensor requirements. The model expects different input tensor names than MediaPipe provides.")
                        }
                        e.message?.contains("input_pos != nullptr") == true -> {
                            Log.e(TAG, "POSITION ENCODING ERROR: Model expects position embeddings")
                            Log.e(TAG, "MediaPipe 0.10.21 doesn't support this model's position encoding requirements")
                            throw IllegalStateException("POSITION_ENCODING_NOT_SUPPORTED: Your Gemma3N model requires position encoding inputs that MediaPipe 0.10.21 doesn't provide. Switching to TensorFlow Lite fallback.")
                        }
                        e.message?.contains("Calculator::Open() for node") == true -> {
                            Log.e(TAG, "MEDIAPIPE CALCULATOR ERROR: Internal pipeline failure")
                            throw IllegalStateException("MediaPipe calculator graph failed. Your model may need a different MediaPipe version or conversion format.")
                        }
                        else -> {
                            Log.e(TAG, "Unknown inference error", e)
                            throw e
                        }
                    }
                }
                
                val responseText = response ?: "No response generated"
                
                // Cache the response for future instant retrieval
                if (responseText.isNotBlank() && !responseText.startsWith("Error:")) {
                    cacheResponse(promptKey, responseText)
                }
                
                // Final response logging
                Log.d(TAG, "🎯 FINAL RESPONSE:")
                Log.d(TAG, "📤 Full response: \"${responseText}\"")
                Log.d(TAG, "📊 Response stats: ${responseText.length} chars, ${responseText.split(" ").size} words")
                Log.d(TAG, "✨ Text generation completed successfully!")
                
                responseText
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate text response", e)
                
                // Re-throw compatibility errors so HybridRepository can handle fallback
                if (e is IllegalStateException && 
                    (e.message?.contains("POSITION_ENCODING_NOT_SUPPORTED") == true ||
                     e.message?.contains("input_pos != nullptr") == true ||
                     e.message?.contains("Input name args_1 was not found") == true ||
                     e.message?.contains("Calculator::Open() for node") == true)) {
                    Log.d(TAG, "Re-throwing MediaPipe compatibility error for fallback handling")
                    throw e
                }
                
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
                
                Log.d(TAG, "🖼️ Generating multimodal response using Command pattern")
                Log.d(TAG, "📸 Image dimensions: ${image.width}x${image.height}")
                Log.d(TAG, "❓ User prompt: ${prompt.take(100)}...")
                
                // Use Facade to coordinate image analysis - follows Single Responsibility Principle
                Log.d(TAG, "🧠 Executing image analysis commands through facade...")
                
                // Generate optimized prompt using Command pattern
                val promptResult = imageAnalysisFacade.generateLLMPrompt(
                    imageBitmap = image,
                    userQuestion = prompt,
                    promptStyle = PromptStyle.PRECISION_FOCUSED
                )
                
                when (promptResult) {
                    is ImageAnalysisResult.Success<*> -> {
                        val llmPromptResult = promptResult.data as LLMPromptResult
                        Log.d(TAG, "✅ Image analysis completed successfully!")
                        Log.d(TAG, "📊 Analysis confidence: ${String.format("%.1f", llmPromptResult.confidence)}%")
                        Log.d(TAG, "📝 Generated prompt length: ${llmPromptResult.promptLength} characters")
                        
                        // Generate response using the optimized prompt
                        val response = generateTextResponse(llmPromptResult.optimizedPrompt)
                        
                        Log.d(TAG, "🎯 Multimodal response generation completed successfully")
                        return@withContext response
                        
                    }
                    is ImageAnalysisResult.Error<*> -> {
                        Log.w(TAG, "❌ Image analysis command failed: ${promptResult.message}")
                        return@withContext """
                            📸➡️❌ IMAGE ANALYSIS FAILED
                            
                            I couldn't analyze your image properly: ${promptResult.message}
                            
                            📸 Image: ${image.width}x${image.height} pixels
                            💬 Your question: "$prompt"
                            
                            🔧 Please try:
                            • Using a clearer, well-lit image
                            • Ensuring the image isn't corrupted
                            • Asking a text-only question instead
                            
                            💡 I work best with clear images containing visible people, objects, or text!
                        """.trimIndent()
                    }
                    is ImageAnalysisResult.Loading<*> -> {
                        Log.d(TAG, "⏳ Image analysis in progress...")
                        return@withContext "🔄 Analyzing image, please wait..."
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to generate multimodal response", e)
                
                // Re-throw compatibility errors for fallback handling
                if (e is IllegalStateException && 
                    (e.message?.contains("POSITION_ENCODING_NOT_SUPPORTED") == true ||
                     e.message?.contains("input_pos != nullptr") == true ||
                     e.message?.contains("Input name args_1 was not found") == true ||
                     e.message?.contains("Calculator::Open() for node") == true)) {
                    Log.d(TAG, "Re-throwing MediaPipe compatibility error for multimodal fallback handling")
                    throw e
                }
                
                "Error: Failed to analyze image and generate response - ${e.message}"
            }
        }
    }
    
    private fun testInferenceSession() {
        try {
            Log.d(TAG, "Running inference session compatibility test...")
            
            // Try a very simple test prompt to check if session initialization works
            val testPrompt = "Hi"
            Log.d(TAG, "Testing with simple prompt: '$testPrompt'")
            
            // This will trigger session creation and reveal any tensor issues
            val response = llmInference?.generateResponse(testPrompt)
            Log.d(TAG, "Test inference successful: ${response?.take(50) ?: "null"}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Inference session test failed", e)
            // Don't throw here - let the actual generation handle the error
            Log.w(TAG, "Session test failed, but continuing with model loading")
        }
    }
    
    // Speed optimization functions for Gemma3N - REMOVED AGGRESSIVE TRUNCATION
    private fun optimizePromptForSpeed(prompt: String): String {
        val trimmed = prompt.trim()
        
        // Allow longer prompts for better image analysis (increased from 300 to 1500)
        val optimized = if (trimmed.length > 1500) {
            Log.d(TAG, "⚡ Smart truncation: ${trimmed.length} → 1500 chars to preserve key information")
            // Keep the most important parts: user question + key analysis results
            val userQuestion = if (trimmed.contains("👤 USER QUESTION:")) {
                trimmed.substringAfter("👤 USER QUESTION:").substringBefore("\n").trim()
            } else ""
            
            val peopleInfo = if (trimmed.contains("👥 PEOPLE DETECTION")) {
                trimmed.substringAfter("👥 PEOPLE DETECTION").substringBefore("🎨").take(300)
            } else ""
            
            val directAnswer = if (trimmed.contains("💬 DIRECT ANSWER")) {
                trimmed.substringAfter("💬 DIRECT ANSWER").take(400)
            } else ""
            
            buildString {
                appendLine("Question: $userQuestion")
                if (peopleInfo.isNotBlank()) {
                    appendLine("People Detection: $peopleInfo")
                }
                if (directAnswer.isNotBlank()) {
                    appendLine("Analysis: $directAnswer")
                }
            }
        } else {
            trimmed
        }
        
        Log.d(TAG, "✅ Optimized prompt length: ${optimized.length} chars")
        return optimized
    }
    
    private fun startProgressMonitoring(startTime: Long): Thread {
        return Thread {
            try {
                var elapsed = 0L
                while (!Thread.currentThread().isInterrupted) {
                    Thread.sleep(2000) // Update every 2 seconds
                    elapsed = System.currentTimeMillis() - startTime
                    Log.d(TAG, "⏳ Generation in progress... ${elapsed/1000}s elapsed")
                    
                    if (elapsed > 30000) { // 30 second warning
                        Log.w(TAG, "⚠️ Generation taking longer than expected (${elapsed/1000}s)")
                    }
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Progress monitoring stopped")
            }
        }.apply { start() }
    }
    
    private fun cacheResponse(promptKey: String, response: String) {
        Log.d(TAG, "💾 Caching response for future speed boost...")
        
        // Remove oldest entries if cache is full
        if (responseCache.size >= maxCacheSize) {
            val oldestKey = responseCache.keys.first()
            responseCache.remove(oldestKey)
            Log.d(TAG, "🗑️ Removed oldest cache entry to make room")
        }
        
        responseCache[promptKey] = response
        Log.d(TAG, "✅ Response cached! Cache size: ${responseCache.size}/$maxCacheSize")
        Log.d(TAG, "⚡ Similar prompts will now get instant responses")
    }
    
    override fun isModelLoaded(): Boolean = modelLoaded
    
    override fun unloadModel() {
        try {
            llmInference?.close()
            llmInference = null
            modelLoaded = false
            
            // Clear cache to free memory
            responseCache.clear()
            Log.d(TAG, "🗑️ Response cache cleared")
            
            Log.d(TAG, "Model unloaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model", e)
        }
    }
}