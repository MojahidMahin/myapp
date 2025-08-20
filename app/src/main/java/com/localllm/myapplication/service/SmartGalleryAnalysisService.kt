package com.localllm.myapplication.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import com.localllm.myapplication.service.ModelManager
import kotlin.coroutines.resume

/**
 * Smart Gallery Analysis Service that uses only Gemma 3N model for everything
 * - OCR text extraction through Gemma 3N vision
 * - Face detection through Gemma 3N vision  
 * - Analysis and summary through Gemma 3N reasoning
 */
class SmartGalleryAnalysisService(
    private val context: Context,
    private val modelManager: ModelManager
) {
    companion object {
        private const val TAG = "SmartGalleryAnalysis"
    }
    
    /**
     * Main function for smart gallery analysis using only Gemma 3N
     */
    suspend fun analyzeImages(
        images: List<Bitmap>,
        userPrompt: String
    ): GemmaAnalysisResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🤖 Starting Gemma 3N gallery analysis for ${images.size} images")
            Log.d(TAG, "💬 User prompt: '$userPrompt'")
            
            if (!modelManager.isModelLoaded.value) {
                return@withContext GemmaAnalysisResult(
                    success = false,
                    analysisType = "ERROR",
                    reasoning = "Gemma 3N model is not loaded. Please load the model first.",
                    imageResults = emptyList(),
                    finalSummary = "❌ Error: Gemma 3N model not loaded",
                    totalImages = images.size
                )
            }
            
            // Step 1: Gemma 3N analyzes each image and determines what to do
            val analysisResults = mutableListOf<GemmaImageAnalysis>()
            
            images.forEachIndexed { index, bitmap ->
                Log.d(TAG, "🔍 Gemma 3N analyzing image ${index + 1}/${images.size}")
                
                val result = analyzeImageWithGemma(bitmap, index, userPrompt)
                analysisResults.add(result)
            }
            
            // Step 2: Gemma 3N creates final comprehensive summary
            val finalSummary = createGemmaFinalSummary(analysisResults, userPrompt)
            
            Log.d(TAG, "✅ Gemma 3N analysis completed successfully")
            
            GemmaAnalysisResult(
                success = true,
                analysisType = "GEMMA_ANALYSIS",
                reasoning = "Full analysis performed by Gemma 3N vision model",
                imageResults = analysisResults,
                finalSummary = finalSummary,
                totalImages = images.size
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Gemma 3N analysis failed", e)
            GemmaAnalysisResult(
                success = false,
                analysisType = "ERROR",
                reasoning = "Gemma 3N analysis failed: ${e.message}",
                imageResults = emptyList(),
                finalSummary = "❌ Failed to analyze images with Gemma 3N: ${e.message}",
                totalImages = images.size
            )
        }
    }
    
    /**
     * Analyze single image with Gemma 3N - handles OCR, face detection, and description
     */
    private suspend fun analyzeImageWithGemma(bitmap: Bitmap, imageIndex: Int, userPrompt: String): GemmaImageAnalysis {
        val analysisPrompt = """
        You are Gemma 3N, an advanced vision AI model. Analyze this image and respond to the user's request.
        
        User request: "$userPrompt"
        
        Please analyze the image and provide:
        1. If there's text in the image, extract and transcribe it (OCR)
        2. If there are people/faces, count and describe them
        3. General description of what you see
        4. Direct answer to the user's specific question
        
        Respond in this format:
        TEXT_FOUND: [any text you can read from the image, or "NONE" if no text]
        FACES_COUNT: [number of faces/people you can see, or 0]
        DESCRIPTION: [what you see in the image]
        ANSWER: [direct answer to user's question: "$userPrompt"]
        """.trimIndent()
        
        return try {
            // Use Gemma 3N with image input
            val response = suspendCancellableCoroutine<String> { continuation ->
                modelManager.generateResponse(
                    prompt = analysisPrompt,
                    images = listOf(bitmap),
                    onResult = { result ->
                        result.fold(
                            onSuccess = { text -> continuation.resume(text) },
                            onFailure = { error -> continuation.resume("ERROR: ${error.message}") }
                        )
                    }
                )
            }
            parseGemmaResponse(response, imageIndex)
        } catch (e: Exception) {
            Log.e(TAG, "Gemma 3N analysis failed for image $imageIndex", e)
            GemmaImageAnalysis(
                imageIndex = imageIndex,
                textFound = "",
                facesCount = 0,
                description = "❌ Gemma 3N analysis failed: ${e.message}",
                answer = "Failed to analyze image",
                success = false
            )
        }
    }
    
    /**
     * Parse Gemma 3N response for image analysis
     */
    private fun parseGemmaResponse(response: String, imageIndex: Int): GemmaImageAnalysis {
        return try {
            var textFound = ""
            var facesCount = 0
            var description = ""
            var answer = ""
            
            response.lines().forEach { line ->
                when {
                    line.startsWith("TEXT_FOUND:") -> {
                        textFound = line.substring(11).trim()
                        if (textFound == "NONE") textFound = ""
                    }
                    line.startsWith("FACES_COUNT:") -> {
                        facesCount = line.substring(12).trim().toIntOrNull() ?: 0
                    }
                    line.startsWith("DESCRIPTION:") -> {
                        description = line.substring(12).trim()
                    }
                    line.startsWith("ANSWER:") -> {
                        answer = line.substring(7).trim()
                    }
                }
            }
            
            GemmaImageAnalysis(
                imageIndex = imageIndex,
                textFound = textFound,
                facesCount = facesCount,
                description = description,
                answer = answer,
                success = true
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Gemma response: $response", e)
            GemmaImageAnalysis(
                imageIndex = imageIndex,
                textFound = "",
                facesCount = 0,
                description = "Parse error: Could not understand Gemma response",
                answer = "Failed to parse response",
                success = false
            )
        }
    }
    
    /**
     * Create final summary using Gemma 3N based on all image analyses
     */
    private suspend fun createGemmaFinalSummary(
        results: List<GemmaImageAnalysis>,
        userPrompt: String
    ): String {
        val summaryPrompt = buildString {
            appendLine("Create a comprehensive summary based on the analysis of ${results.size} images.")
            appendLine()
            appendLine("User's original request: \"$userPrompt\"")
            appendLine()
            appendLine("Analysis results from each image:")
            
            results.forEach { result ->
                appendLine("Image ${result.imageIndex + 1}:")
                if (result.textFound.isNotBlank()) {
                    appendLine("  Text: ${result.textFound}")
                }
                if (result.facesCount > 0) {
                    appendLine("  Faces: ${result.facesCount}")
                }
                appendLine("  Description: ${result.description}")
                appendLine("  Answer: ${result.answer}")
                appendLine()
            }
            
            appendLine("Please provide a final comprehensive summary that directly answers the user's request: \"$userPrompt\"")
            appendLine("Include any patterns, totals, or key insights from all the images combined.")
        }
        
        return try {
            suspendCancellableCoroutine<String> { continuation ->
                modelManager.generateResponse(
                    prompt = summaryPrompt,
                    onResult = { result ->
                        result.fold(
                            onSuccess = { text -> continuation.resume(text) },
                            onFailure = { error -> continuation.resume("ERROR: ${error.message}") }
                        )
                    }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to generate final summary with Gemma 3N", e)
            
            // Fallback summary
            buildString {
                appendLine("🤖 Gemma 3N Analysis Summary:")
                appendLine("Analyzed ${results.size} images")
                appendLine()
                
                val successfulResults = results.filter { it.success }
                val totalTextChars = successfulResults.sumOf { it.textFound.length }
                val totalFaces = successfulResults.sumOf { it.facesCount }
                val imagesWithText = successfulResults.count { it.textFound.isNotBlank() }
                val imagesWithFaces = successfulResults.count { it.facesCount > 0 }
                
                appendLine("📊 Results:")
                appendLine("- Images successfully analyzed: ${successfulResults.size}")
                appendLine("- Images with text: $imagesWithText")
                appendLine("- Total text characters found: $totalTextChars")
                appendLine("- Images with faces: $imagesWithFaces") 
                appendLine("- Total faces detected: $totalFaces")
                
                if (successfulResults.isNotEmpty()) {
                    appendLine()
                    appendLine("📝 Individual responses:")
                    successfulResults.forEach { result ->
                        appendLine("Image ${result.imageIndex + 1}: ${result.answer}")
                    }
                }
            }
        }
    }
}

/**
 * Data classes for Gemma 3N analysis
 */
data class GemmaImageAnalysis(
    val imageIndex: Int,
    val textFound: String,
    val facesCount: Int,
    val description: String,
    val answer: String,
    val success: Boolean
)

data class GemmaAnalysisResult(
    val success: Boolean,
    val analysisType: String,
    val reasoning: String,
    val imageResults: List<GemmaImageAnalysis>,
    val finalSummary: String,
    val totalImages: Int
)