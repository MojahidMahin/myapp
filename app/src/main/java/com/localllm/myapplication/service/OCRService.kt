package com.localllm.myapplication.service

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Enhanced OCR service for extracting text from images
 * This allows text-only models like Gemma 2B to process image content
 * Now includes image preprocessing for better text recognition
 */
class OCRService {
    
    companion object {
        private const val TAG = "OCRService"
    }
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val imagePreprocessor = ImagePreprocessor()
    
    /**
     * Extract text from an image using Google ML Kit OCR with preprocessing
     */
    suspend fun extractTextFromImage(bitmap: Bitmap, usePreprocessing: Boolean = true): OCRResult {
        return try {
            Log.d(TAG, "🔍 Starting enhanced OCR text extraction...")
            Log.d(TAG, "📸 Image size: ${bitmap.width}x${bitmap.height}")
            
            // Apply preprocessing if enabled
            val processedImage = if (usePreprocessing) {
                Log.d(TAG, "🔧 Preprocessing image for better OCR accuracy...")
                val options = PreprocessingOptions(
                    enableResize = true,
                    enhanceContrast = true,
                    enableSharpening = true,
                    normalizeLighting = true,
                    enableNoiseReduction = false, // Skip for OCR to preserve text edges
                    adjustSaturation = false, // Keep original colors for text
                    enhanceEdges = true
                )
                imagePreprocessor.preprocessImage(bitmap, options)
            } else {
                ProcessedImage(
                    bitmap = bitmap,
                    enhancedDescription = "No preprocessing applied",
                    processingSteps = emptyList(),
                    originalSize = "${bitmap.width}x${bitmap.height}",
                    processedSize = "${bitmap.width}x${bitmap.height}"
                )
            }
            
            Log.d(TAG, "🎯 Processing ${if (usePreprocessing) "enhanced" else "original"} image...")
            if (usePreprocessing) {
                Log.d(TAG, "✨ Applied preprocessing steps: ${processedImage.processingSteps.joinToString(", ")}")
            }
            
            val inputImage = InputImage.fromBitmap(processedImage.bitmap, 0)
            val visionText = textRecognizer.process(inputImage).await()
            
            val extractedText = visionText.text
            val confidence = calculateAverageConfidence(visionText)
            val blockCount = visionText.textBlocks.size
            val lineCount = visionText.textBlocks.sumOf { it.lines.size }
            
            Log.d(TAG, "✅ OCR extraction completed!")
            Log.d(TAG, "📝 Extracted text: \"$extractedText\"")
            Log.d(TAG, "📊 Stats: ${extractedText.length} chars, $blockCount blocks, $lineCount lines")
            Log.d(TAG, "🎯 Confidence: ${String.format("%.1f", confidence)}%")
            
            if (extractedText.isBlank()) {
                Log.w(TAG, "⚠️ No text found in image")
                OCRResult(
                    success = false,
                    text = "",
                    confidence = 0.0f,
                    message = "No text detected in the image",
                    preprocessingInfo = if (usePreprocessing) processedImage.enhancedDescription else null
                )
            } else {
                val enhancedText = if (usePreprocessing) {
                    buildString {
                        appendLine("📸➡️📝 ENHANCED OCR EXTRACTION")
                        appendLine()
                        appendLine(processedImage.enhancedDescription)
                        appendLine()
                        appendLine("🔤 EXTRACTED TEXT:")
                        append(extractedText)
                    }
                } else {
                    extractedText
                }
                
                OCRResult(
                    success = true,
                    text = enhancedText,
                    confidence = confidence,
                    blockCount = blockCount,
                    lineCount = lineCount,
                    message = if (usePreprocessing) "Text extracted with image preprocessing" else "Text extracted successfully",
                    preprocessingInfo = if (usePreprocessing) processedImage.enhancedDescription else null,
                    processingSteps = if (usePreprocessing) processedImage.processingSteps else emptyList()
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ OCR extraction failed", e)
            OCRResult(
                success = false,
                text = "",
                confidence = 0.0f,
                message = "OCR failed: ${e.message}",
                preprocessingInfo = null,
                processingSteps = emptyList()
            )
        }
    }
    
    private fun calculateAverageConfidence(visionText: com.google.mlkit.vision.text.Text): Float {
        if (visionText.textBlocks.isEmpty()) return 0.0f
        
        var totalConfidence = 0.0f
        var elementCount = 0
        
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    // ML Kit doesn't provide confidence scores directly
                    // We'll use a heuristic based on text characteristics
                    totalConfidence += estimateConfidence(element.text)
                    elementCount++
                }
            }
        }
        
        return if (elementCount > 0) totalConfidence / elementCount else 0.0f
    }
    
    private fun estimateConfidence(text: String): Float {
        // Simple heuristic to estimate confidence based on text characteristics
        var confidence = 50.0f // Base confidence
        
        // Boost confidence for longer words
        if (text.length >= 4) confidence += 20.0f
        
        // Boost for dictionary-like words (contain mostly letters)
        val letterRatio = text.count { it.isLetter() }.toFloat() / text.length
        confidence += letterRatio * 30.0f
        
        // Reduce for unusual characters
        val specialCharRatio = text.count { !it.isLetterOrDigit() && !it.isWhitespace() }.toFloat() / text.length
        confidence -= specialCharRatio * 20.0f
        
        return confidence.coerceIn(0.0f, 100.0f)
    }
    
    fun cleanup() {
        try {
            textRecognizer.close()
            Log.d(TAG, "OCR service cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up OCR service", e)
        }
    }
}

/**
 * Enhanced result of OCR text extraction with preprocessing information
 */
data class OCRResult(
    val success: Boolean,
    val text: String,
    val confidence: Float,
    val blockCount: Int = 0,
    val lineCount: Int = 0,
    val message: String,
    val preprocessingInfo: String? = null,
    val processingSteps: List<String> = emptyList()
)