package com.localllm.myapplication.command.model

import android.util.Log

/**
 * Command for coordinating model selection workflow
 * Following Command Pattern and Single Responsibility Principle
 */
class ModelSelectionCommand(
    private val modelService: ModelService
) : ModelCommand<Unit> {
    
    companion object {
        private const val TAG = "ModelSelectionCommand"
    }
    
    override suspend fun execute(): Result<Unit> {
        return try {
            Log.d(TAG, "Initiating model selection workflow")
            // This command prepares the system for model selection
            // The actual file picking is handled by the UI
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error in model selection workflow", e)
            Result.failure(e)
        }
    }
    
    override fun canExecute(): Boolean {
        // Model selection is always available
        return true
    }
    
    override fun getDescription(): String {
        return "Initiate model selection workflow"
    }
}

/**
 * Command for validating selected model file
 * Following Single Responsibility Principle
 */
class ModelValidationCommand(
    private val filePath: String
) : ModelCommand<Boolean> {
    
    companion object {
        private const val TAG = "ModelValidationCommand"
        private val SUPPORTED_EXTENSIONS = listOf(".task", ".tflite")
        private val SUPPORTED_KEYWORDS = listOf("gemma", "llama", "mistral", "phi")
    }
    
    override suspend fun execute(): Result<Boolean> {
        return try {
            val fileName = filePath.substringAfterLast("/").lowercase()
            
            val isValidExtension = SUPPORTED_EXTENSIONS.any { ext -> 
                fileName.endsWith(ext) 
            }
            
            val hasValidKeyword = SUPPORTED_KEYWORDS.any { keyword -> 
                fileName.contains(keyword) 
            }
            
            val isValid = isValidExtension || hasValidKeyword
            
            Log.d(TAG, "Validating file: $fileName, valid: $isValid")
            Result.success(isValid)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating model file", e)
            Result.failure(e)
        }
    }
    
    override fun canExecute(): Boolean {
        return filePath.isNotBlank()
    }
    
    override fun getDescription(): String {
        return "Validate model file: ${filePath.substringAfterLast("/")}"
    }
}