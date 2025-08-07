package com.localllm.myapplication.service

import android.graphics.Bitmap
import android.util.Log
import com.localllm.myapplication.command.ImageAnalysisResult as CommandImageAnalysisResult
import com.localllm.myapplication.command.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Facade for image analysis operations
 * Implements Facade pattern to simplify complex image analysis subsystem
 * Follows Single Responsibility Principle - coordinates image analysis commands
 */
class ImageAnalysisFacade(
    private val commandExecutor: ImageAnalysisCommandExecutor = DefaultImageAnalysisCommandExecutor()
) {
    
    companion object {
        private const val TAG = "ImageAnalysisFacade"
    }
    
    /**
     * Comprehensive image analysis for LLM processing
     * High-level operation that encapsulates complex analysis logic
     */
    suspend fun analyzeImageForLLM(
        imageBitmap: Bitmap,
        userQuestion: String
    ): CommandImageAnalysisResult<com.localllm.myapplication.service.ImageAnalysisResult> {
        Log.d(TAG, "🎯 Starting comprehensive LLM image analysis")
        
        val command = AnalyzeImageForLLMCommand(imageBitmap, userQuestion)
        return commandExecutor.execute(command)
    }
    
    /**
     * Specialized people detection analysis
     */
    suspend fun detectPeople(imageBitmap: Bitmap): CommandImageAnalysisResult<PeopleDetectionResult> {
        Log.d(TAG, "👥 Starting people detection analysis")
        
        val command = DetectPeopleCommand(imageBitmap)
        return commandExecutor.execute(command)
    }
    
    /**
     * Object detection and classification
     */
    suspend fun detectObjects(
        imageBitmap: Bitmap,
        targetObjects: List<ObjectType> = ObjectType.values().toList()
    ): CommandImageAnalysisResult<ObjectDetectionResult> {
        Log.d(TAG, "🔍 Starting object detection analysis")
        
        val command = DetectObjectsCommand(imageBitmap, targetObjects)
        return commandExecutor.execute(command)
    }
    
    /**
     * Generate optimized prompts for LLM based on image content
     */
    suspend fun generateLLMPrompt(
        imageBitmap: Bitmap,
        userQuestion: String,
        promptStyle: PromptStyle = PromptStyle.PRECISION_FOCUSED
    ): CommandImageAnalysisResult<LLMPromptResult> {
        Log.d(TAG, "📝 Generating LLM-optimized prompt")
        
        val command = GenerateLLMPromptCommand(imageBitmap, userQuestion, promptStyle)
        return commandExecutor.execute(command)
    }
    
    /**
     * Quick analysis for common use cases
     * Determines the best analysis approach based on the question
     */
    suspend fun quickAnalysis(
        imageBitmap: Bitmap,
        userQuestion: String
    ): QuickAnalysisResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "⚡ Performing quick analysis to determine approach")
        
        val questionLower = userQuestion.lowercase().trim()
        
        when {
            // People counting questions
            questionLower.contains("how many") && (questionLower.contains("person") || 
                                                  questionLower.contains("people") || 
                                                  questionLower.contains("face")) -> {
                val result = detectPeople(imageBitmap)
                when (result) {
                    is CommandImageAnalysisResult.Success<*> -> {
                        val peopleResult = result.data as PeopleDetectionResult
                        return@withContext QuickAnalysisResult(
                            analysisType = AnalysisType.PEOPLE_DETECTION,
                            primaryResult = peopleResult.peopleCount.toString(),
                            confidence = peopleResult.confidence,
                            recommendation = "Use people detection for accurate counting"
                        )
                    }
                    is CommandImageAnalysisResult.Error<*> -> {
                        return@withContext QuickAnalysisResult(
                            analysisType = AnalysisType.ERROR,
                            primaryResult = "Detection failed",
                            confidence = 0f,
                            recommendation = result.message
                        )
                    }
                    is CommandImageAnalysisResult.Loading<*> -> {
                        return@withContext QuickAnalysisResult(
                            analysisType = AnalysisType.PROCESSING,
                            primaryResult = "Processing...",
                            confidence = 0f,
                            recommendation = "Please wait"
                        )
                    }
                }
            }
            
            // Object identification questions
            questionLower.contains("what") && (questionLower.contains("is this") || 
                                              questionLower.contains("object")) -> {
                val result = detectObjects(imageBitmap)
                when (result) {
                    is CommandImageAnalysisResult.Success<*> -> {
                        val objectResult = result.data as ObjectDetectionResult
                        val primaryObject = objectResult.detectedObjects.firstOrNull() ?: objectResult.sceneType
                        return@withContext QuickAnalysisResult(
                            analysisType = AnalysisType.OBJECT_DETECTION,
                            primaryResult = primaryObject,
                            confidence = objectResult.confidence,
                            recommendation = "Use object detection for identification"
                        )
                    }
                    is CommandImageAnalysisResult.Error<*> -> {
                        return@withContext QuickAnalysisResult(
                            analysisType = AnalysisType.ERROR,
                            primaryResult = "Detection failed",
                            confidence = 0f,
                            recommendation = result.message
                        )
                    }
                    is CommandImageAnalysisResult.Loading<*> -> {
                        return@withContext QuickAnalysisResult(
                            analysisType = AnalysisType.PROCESSING,
                            primaryResult = "Processing...",
                            confidence = 0f,
                            recommendation = "Please wait"
                        )
                    }
                }
            }
            
            // General questions - use comprehensive analysis
            else -> {
                val result = analyzeImageForLLM(imageBitmap, userQuestion)
                when (result) {
                    is CommandImageAnalysisResult.Success<*> -> {
                        val analysisResult = result.data as com.localllm.myapplication.service.ImageAnalysisResult
                        return@withContext QuickAnalysisResult(
                            analysisType = AnalysisType.COMPREHENSIVE,
                            primaryResult = analysisResult.objectsDetected.sceneType,
                            confidence = analysisResult.confidence,
                            recommendation = "Use comprehensive analysis for complex queries"
                        )
                    }
                    is CommandImageAnalysisResult.Error<*> -> {
                        return@withContext QuickAnalysisResult(
                            analysisType = AnalysisType.ERROR,
                            primaryResult = "Analysis failed",
                            confidence = 0f,
                            recommendation = result.message
                        )
                    }
                    is CommandImageAnalysisResult.Loading<*> -> {
                        return@withContext QuickAnalysisResult(
                            analysisType = AnalysisType.PROCESSING,
                            primaryResult = "Processing...",
                            confidence = 0f,
                            recommendation = "Please wait"
                        )
                    }
                }
            }
        }
        
        // This should never be reached due to the else clause above, but added for completeness
        return@withContext QuickAnalysisResult(
            analysisType = AnalysisType.ERROR,
            primaryResult = "Unknown analysis error",
            confidence = 0f,
            recommendation = "Please try again"
        )
    }
}

/**
 * Factory for creating configured image analysis facade
 * Implements Factory pattern for flexible instantiation
 */
object ImageAnalysisFacadeFactory {
    
    fun createDefault(): ImageAnalysisFacade {
        return ImageAnalysisFacade(DefaultImageAnalysisCommandExecutor())
    }
    
    fun createWithCustomExecutor(executor: ImageAnalysisCommandExecutor): ImageAnalysisFacade {
        return ImageAnalysisFacade(executor)
    }
}

// Data classes for results
data class QuickAnalysisResult(
    val analysisType: AnalysisType,
    val primaryResult: String,
    val confidence: Float,
    val recommendation: String
)

enum class AnalysisType {
    PEOPLE_DETECTION, OBJECT_DETECTION, COMPREHENSIVE, PROCESSING, ERROR
}