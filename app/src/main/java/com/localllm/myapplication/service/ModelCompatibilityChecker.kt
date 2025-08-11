package com.localllm.myapplication.service

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile

object ModelCompatibilityChecker {
    
    private const val TAG = "ModelCompatibility"
    
    data class CompatibilityResult(
        val isCompatible: Boolean,
        val modelType: ModelType,
        val estimatedSize: Long,
        val issues: List<String>,
        val recommendations: List<String>
    )
    
    enum class ModelType {
        GEMMA_2B,
        GEMMA_7B, 
        GEMMA_3N,
        LLAMA,
        PHI,
        UNKNOWN
    }
    
    fun checkModelCompatibility(modelPath: String, context: Context): CompatibilityResult {
        Log.d(TAG, "Checking compatibility for model: $modelPath")
        
        val modelFile = File(modelPath)
        val issues = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        
        // Basic file checks
        if (!modelFile.exists()) {
            issues.add("Model file does not exist")
            return CompatibilityResult(false, ModelType.UNKNOWN, 0, issues, recommendations)
        }
        
        val fileSize = modelFile.length()
        Log.d(TAG, "Model file size: ${fileSize / 1024 / 1024}MB")
        
        // Size-based compatibility check
        when {
            fileSize < 1024 * 1024 -> {
                issues.add("Model file too small (${fileSize} bytes)")
                recommendations.add("Use a proper model file (minimum 1MB)")
            }
            fileSize > 8L * 1024 * 1024 * 1024 -> {
                issues.add("Model file very large (${fileSize / 1024 / 1024 / 1024}GB)")
                recommendations.add("Consider using a smaller quantized model")
                recommendations.add("Ensure device has sufficient RAM")
            }
        }
        
        // Detect model type and check MediaPipe compatibility
        val modelType = detectModelType(modelFile)
        Log.d(TAG, "Detected model type: $modelType")
        
        val deviceCapabilities = checkDeviceCapabilities(context)
        val compatibilityCheck = checkMediaPipeCompatibility(modelType, fileSize, deviceCapabilities)
        
        issues.addAll(compatibilityCheck.first)
        recommendations.addAll(compatibilityCheck.second)
        
        val isCompatible = issues.isEmpty()
        
        Log.d(TAG, "Compatibility result: $isCompatible")
        if (issues.isNotEmpty()) {
            Log.w(TAG, "Compatibility issues found: ${issues.joinToString(", ")}")
        }
        
        return CompatibilityResult(
            isCompatible = isCompatible,
            modelType = modelType,
            estimatedSize = fileSize,
            issues = issues,
            recommendations = recommendations
        )
    }
    
    private fun detectModelType(modelFile: File): ModelType {
        return try {
            val fileName = modelFile.name.lowercase()
            when {
                fileName.contains("gemma") && fileName.contains("3n") -> ModelType.GEMMA_3N
                fileName.contains("gemma") && fileName.contains("2b") -> ModelType.GEMMA_2B
                fileName.contains("gemma") && fileName.contains("7b") -> ModelType.GEMMA_7B
                fileName.contains("llama") -> ModelType.LLAMA
                fileName.contains("phi") -> ModelType.PHI
                else -> {
                    // Try to detect by file content magic numbers
                    RandomAccessFile(modelFile, "r").use { raf ->
                        val buffer = ByteArray(64)
                        val bytesRead = raf.read(buffer)
                        val header = String(buffer, 0, minOf(bytesRead, 64))
                        
                        when {
                            header.contains("gemma", ignoreCase = true) -> ModelType.GEMMA_2B
                            header.contains("llama", ignoreCase = true) -> ModelType.LLAMA
                            else -> ModelType.UNKNOWN
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not detect model type", e)
            ModelType.UNKNOWN
        }
    }
    
    private fun checkDeviceCapabilities(context: Context): DeviceCapabilities {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val availableMemory = maxMemory - (totalMemory - freeMemory)
        
        Log.d(TAG, "Device memory - Max: ${maxMemory / 1024 / 1024}MB, Available: ${availableMemory / 1024 / 1024}MB")
        
        return DeviceCapabilities(
            availableMemoryMB = (availableMemory / 1024 / 1024).toInt(),
            maxMemoryMB = (maxMemory / 1024 / 1024).toInt(),
            hasGPU = true, // Assume GPU is available
            cpuCores = Runtime.getRuntime().availableProcessors()
        )
    }
    
    private fun checkMediaPipeCompatibility(
        modelType: ModelType,
        fileSize: Long,
        capabilities: DeviceCapabilities
    ): Pair<List<String>, List<String>> {
        val issues = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        
        when (modelType) {
            ModelType.GEMMA_3N -> {
                issues.add("Gemma 3N models may not be fully supported by MediaPipe 0.10.14")
                recommendations.add("Try using MediaPipe 0.10.21+ for Gemma 3N support")
                recommendations.add("Or use a Gemma 2B model for better compatibility")
            }
            ModelType.GEMMA_7B -> {
                if (capabilities.availableMemoryMB < 2048) {
                    issues.add("Gemma 7B requires at least 2GB of free RAM")
                    recommendations.add("Close other apps to free memory")
                    recommendations.add("Consider using a quantized or 2B variant")
                }
            }
            ModelType.LLAMA -> {
                recommendations.add("LLAMA models may work better with TensorFlow Lite")
                recommendations.add("Ensure model is properly converted for MediaPipe")
            }
            ModelType.UNKNOWN -> {
                recommendations.add("Verify model is in correct .task or .tflite format")
                recommendations.add("Check model was exported specifically for MediaPipe")
            }
            else -> {
                // Gemma 2B and PHI should work well
                Log.d(TAG, "$modelType should be compatible")
            }
        }
        
        // Memory requirements check
        val estimatedMemoryMB = (fileSize / 1024 / 1024 * 2).toInt() // Rough estimate: 2x file size
        if (estimatedMemoryMB > capabilities.availableMemoryMB) {
            issues.add("Model may require more memory than available (estimated ${estimatedMemoryMB}MB needed)")
            recommendations.add("Close other applications to free memory")
            recommendations.add("Use a smaller quantized model")
        }
        
        return Pair(issues, recommendations)
    }
    
    data class DeviceCapabilities(
        val availableMemoryMB: Int,
        val maxMemoryMB: Int,
        val hasGPU: Boolean,
        val cpuCores: Int
    )
}