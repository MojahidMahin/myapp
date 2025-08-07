package com.localllm.myapplication.command

import android.graphics.Bitmap
import android.util.Log
import com.localllm.myapplication.service.ImageAnalysisService
import com.localllm.myapplication.service.ImageAnalysisResult as ServiceAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Command for comprehensive image analysis for LLM processing
 * Implements Command pattern with Single Responsibility Principle
 */
class AnalyzeImageForLLMCommand(
    imageBitmap: Bitmap,
    private val userQuestion: String = "",
    private val imageAnalysisService: ImageAnalysisService = ImageAnalysisService(),
    parameters: Map<String, Any> = emptyMap()
) : BaseImageAnalysisCommand<ServiceAnalysisResult>(imageBitmap, parameters) {
    
    companion object {
        private const val TAG = "AnalyzeImageForLLMCommand"
    }
    
    override suspend fun performAnalysis(): ServiceAnalysisResult {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "🔍 Executing comprehensive image analysis for LLM")
            Log.d(TAG, "📸 Image: ${bitmap.width}x${bitmap.height}")
            Log.d(TAG, "❓ User question: ${userQuestion.take(100)}")
            
            imageAnalysisService.analyzeImage(bitmap, userQuestion)
        }
    }
    
    override fun validate(): Boolean {
        return super.validate() && userQuestion.length <= 1000 // Reasonable question length limit
    }
}

/**
 * Command for detecting people in images
 * Focused on people detection with high accuracy
 */
class DetectPeopleCommand(
    imageBitmap: Bitmap,
    private val imageAnalysisService: ImageAnalysisService = ImageAnalysisService(),
    parameters: Map<String, Any> = emptyMap()
) : BaseImageAnalysisCommand<PeopleDetectionResult>(imageBitmap, parameters) {
    
    companion object {
        private const val TAG = "DetectPeopleCommand"
    }
    
    override suspend fun performAnalysis(): PeopleDetectionResult {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "👥 Executing people detection analysis")
            Log.d(TAG, "📸 Image: ${bitmap.width}x${bitmap.height}")
            
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            
            // Use the enhanced people detection from ImageAnalysisService
            val analysisResult = imageAnalysisService.analyzeImage(bitmap, "How many people?")
            
            PeopleDetectionResult(
                peopleCount = analysisResult.objectsDetected.peopleCount,
                confidence = analysisResult.confidence,
                detectionMethods = listOf("skin_tone", "head_regions", "body_shapes", "facial_features"),
                analysisDetails = analysisResult.description
            )
        }
    }
}

/**
 * Command for detecting specific objects in images
 * Implements object detection with classification
 */
class DetectObjectsCommand(
    imageBitmap: Bitmap,
    private val objectTypes: List<ObjectType> = ObjectType.values().toList(),
    private val imageAnalysisService: ImageAnalysisService = ImageAnalysisService(),
    parameters: Map<String, Any> = emptyMap()
) : BaseImageAnalysisCommand<ObjectDetectionResult>(imageBitmap, parameters) {
    
    companion object {
        private const val TAG = "DetectObjectsCommand"
    }
    
    override suspend fun performAnalysis(): ObjectDetectionResult {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "🔍 Executing object detection analysis")
            Log.d(TAG, "📸 Image: ${bitmap.width}x${bitmap.height}")
            Log.d(TAG, "🎯 Target object types: ${objectTypes.joinToString()}")
            
            val analysisResult = imageAnalysisService.analyzeImage(bitmap, "What objects are in this image?")
            
            ObjectDetectionResult(
                detectedObjects = analysisResult.objectsDetected.detectedObjects,
                sceneType = analysisResult.objectsDetected.sceneType,
                confidence = analysisResult.confidence,
                visualCharacteristics = mapOf(
                    "brightness" to analysisResult.visualElements.brightness,
                    "contrast" to analysisResult.visualElements.contrast,
                    "colorfulness" to analysisResult.visualElements.colorfulness,
                    "clarity" to analysisResult.visualElements.clarity
                )
            )
        }
    }
}

/**
 * Command for generating LLM-optimized prompts from images
 * Separates prompt generation logic with clear responsibilities
 */
class GenerateLLMPromptCommand(
    imageBitmap: Bitmap,
    private val userQuestion: String,
    private val promptStyle: PromptStyle = PromptStyle.PRECISION_FOCUSED,
    private val imageAnalysisService: ImageAnalysisService = ImageAnalysisService(),
    parameters: Map<String, Any> = emptyMap()
) : BaseImageAnalysisCommand<LLMPromptResult>(imageBitmap, parameters) {
    
    companion object {
        private const val TAG = "GenerateLLMPromptCommand"
    }
    
    override suspend fun performAnalysis(): LLMPromptResult {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "📝 Generating LLM prompt from image analysis")
            Log.d(TAG, "❓ User question: $userQuestion")
            Log.d(TAG, "🎨 Prompt style: $promptStyle")
            
            // First analyze the image
            val analysisResult = imageAnalysisService.analyzeImage(bitmap, userQuestion)
            
            // Generate optimized prompt based on style and question type
            val optimizedPrompt = generatePromptBasedOnStyle(analysisResult, userQuestion, promptStyle)
            
            LLMPromptResult(
                optimizedPrompt = optimizedPrompt,
                promptLength = optimizedPrompt.length,
                promptStyle = promptStyle,
                confidence = analysisResult.confidence,
                sourceAnalysis = analysisResult.description
            )
        }
    }
    
    private fun generatePromptBasedOnStyle(
        analysis: ServiceAnalysisResult,
        question: String,
        style: PromptStyle
    ): String {
        val questionLower = question.lowercase().trim()
        
        return when (style) {
            PromptStyle.PRECISION_FOCUSED -> when {
                questionLower.contains("how many") && (questionLower.contains("person") || questionLower.contains("people") || questionLower.contains("face")) -> {
                    buildString {
                        appendLine("Question: $question")
                        appendLine("Image analysis: ${analysis.objectsDetected.peopleCount} people detected using advanced computer vision.")
                        appendLine("Detection confidence: ${String.format("%.1f", analysis.confidence)}%")
                        appendLine("Answer with just the number:")
                    }
                }
                
                questionLower.contains("what") && (questionLower.contains("is this") || questionLower.contains("object")) -> {
                    val primaryObjects = analysis.objectsDetected.detectedObjects.filter { 
                        it.contains("laptop") || it.contains("bottle") || it.contains("phone") || 
                        it.contains("computer") || it.contains("electronic") || it.contains("container")
                    }
                    
                    buildString {
                        appendLine("Question: $question")
                        if (primaryObjects.isNotEmpty()) {
                            appendLine("Object detected: ${primaryObjects.first()}")
                            appendLine("Scene type: ${analysis.objectsDetected.sceneType}")
                        } else {
                            appendLine("Scene: ${analysis.objectsDetected.sceneType}")
                            appendLine("Objects: ${analysis.objectsDetected.detectedObjects.take(3).joinToString(", ")}")
                        }
                        appendLine("Visual quality: ${analysis.visualElements.clarity}")
                        appendLine("Answer directly:")
                    }
                }
                
                else -> {
                    buildString {
                        appendLine("Question: $question")
                        appendLine("Image contains: ${analysis.objectsDetected.sceneType}")
                        if (analysis.objectsDetected.peopleCount > 0) {
                            appendLine("People: ${analysis.objectsDetected.peopleCount}")
                        }
                        appendLine("Answer concisely:")
                    }
                }
            }
            
            PromptStyle.DETAILED -> analysis.description
            
            PromptStyle.MINIMAL -> {
                buildString {
                    appendLine("Q: $question")
                    appendLine("Image: ${analysis.objectsDetected.sceneType}")
                    if (analysis.objectsDetected.peopleCount > 0) {
                        appendLine("${analysis.objectsDetected.peopleCount} person(s)")
                    }
                    appendLine("A:")
                }
            }
        }
    }
}

// Data classes for command results
data class PeopleDetectionResult(
    val peopleCount: Int,
    val confidence: Float,
    val detectionMethods: List<String>,
    val analysisDetails: String
)

data class ObjectDetectionResult(
    val detectedObjects: List<String>,
    val sceneType: String,
    val confidence: Float,
    val visualCharacteristics: Map<String, Any>
)

data class LLMPromptResult(
    val optimizedPrompt: String,
    val promptLength: Int,
    val promptStyle: PromptStyle,
    val confidence: Float,
    val sourceAnalysis: String
)

// Enums for configuration
enum class ObjectType {
    PEOPLE, ELECTRONICS, BOTTLES, FOOD, VEHICLES, FURNITURE, CLOTHING, PLANTS, ARCHITECTURE
}

enum class PromptStyle {
    PRECISION_FOCUSED, DETAILED, MINIMAL
}