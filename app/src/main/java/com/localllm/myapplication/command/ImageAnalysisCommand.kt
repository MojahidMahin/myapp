package com.localllm.myapplication.command

import android.graphics.Bitmap

/**
 * Command interface for image analysis operations
 * Follows Command Design Pattern for encapsulating image analysis requests
 */
interface ImageAnalysisCommand<T> {
    suspend fun execute(): T
    fun getImageBitmap(): Bitmap
    fun getParameters(): Map<String, Any>
}

/**
 * Base abstract command for image analysis with common functionality
 */
abstract class BaseImageAnalysisCommand<T>(
    protected val bitmap: Bitmap,
    protected val params: Map<String, Any> = emptyMap()
) : ImageAnalysisCommand<T> {
    
    override fun getImageBitmap(): Bitmap = bitmap
    override fun getParameters(): Map<String, Any> = params
    
    /**
     * Template method for validation before execution
     */
    protected open fun validate(): Boolean {
        return bitmap.isRecycled.not() && 
               bitmap.width > 0 && 
               bitmap.height > 0
    }
    
    /**
     * Template method that ensures validation before execution
     */
    final override suspend fun execute(): T {
        if (!validate()) {
            throw IllegalStateException("Invalid image bitmap for analysis")
        }
        return performAnalysis()
    }
    
    /**
     * Abstract method for specific analysis implementation
     */
    protected abstract suspend fun performAnalysis(): T
}

/**
 * Result wrapper for image analysis commands
 */
sealed class ImageAnalysisResult<T> {
    data class Success<T>(val data: T) : ImageAnalysisResult<T>()
    data class Error<T>(val exception: Throwable, val message: String) : ImageAnalysisResult<T>()
    data class Loading<T>(val progress: Float = 0f) : ImageAnalysisResult<T>()
}

/**
 * Command executor for managing image analysis operations
 */
interface ImageAnalysisCommandExecutor {
    suspend fun <T> execute(command: ImageAnalysisCommand<T>): ImageAnalysisResult<T>
}

/**
 * Default implementation of command executor with error handling
 */
class DefaultImageAnalysisCommandExecutor : ImageAnalysisCommandExecutor {
    override suspend fun <T> execute(command: ImageAnalysisCommand<T>): ImageAnalysisResult<T> {
        return try {
            ImageAnalysisResult.Success(command.execute())
        } catch (e: Exception) {
            ImageAnalysisResult.Error(e, "Image analysis failed: ${e.message}")
        }
    }
}