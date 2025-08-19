package com.localllm.myapplication.service

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.localllm.myapplication.data.*
import com.localllm.myapplication.service.ai.AIProcessingFacade
import com.localllm.myapplication.service.integration.GmailIntegrationService
import com.localllm.myapplication.service.integration.TelegramBotService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for analyzing gallery images from the previous 24 hours
 * with keyword filtering and LLM processing
 */
@Singleton
class GalleryAnalysisService @Inject constructor(
    private val context: Context,
    private val aiProcessingFacade: AIProcessingFacade,
    private val gmailService: GmailIntegrationService,
    private val telegramService: TelegramBotService,
    private val imageAnalysisService: ImageAnalysisService
) {
    companion object {
        private const val TAG = "GalleryAnalysisService"
        private const val HOURS_24_IN_MILLIS = 24 * 60 * 60 * 1000L
    }

    /**
     * Main function to execute 24-hour gallery analysis
     */
    suspend fun execute24HourGalleryAnalysis(action: MultiUserAction.AI24HourGalleryAnalysis): ActionExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "🔍 === 24-HOUR GALLERY ANALYSIS START ===")
                Log.i(TAG, "📱 Search keyword: '${action.searchKeyword}'")
                Log.i(TAG, "📤 Delivery method: ${action.deliveryMethod}")
                Log.i(TAG, "📸 Max images: ${action.maxImages}")

                // Step 1: Get images from previous 24 hours
                val recentImages = getRecentGalleryImages()
                Log.i(TAG, "📷 Found ${recentImages.size} images from last 24 hours")

                if (recentImages.isEmpty()) {
                    Log.w(TAG, "⚠️ No images found in the last 24 hours")
                    return@withContext ActionExecutionResult(
                        success = false,
                        message = "No images found in gallery from the last 24 hours",
                        outputData = mapOf(action.outputVariable to "")
                    )
                }

                // Step 2: Filter images by keyword
                val filteredImages = filterImagesByKeyword(recentImages, action.searchKeyword, action.maxImages)
                Log.i(TAG, "🔍 Filtered to ${filteredImages.size} images containing keyword '${action.searchKeyword}'")

                if (filteredImages.isEmpty()) {
                    Log.i(TAG, "ℹ️ No images found containing the keyword '${action.searchKeyword}'")
                    val noResultsMessage = "No images found in the last 24 hours containing the keyword '${action.searchKeyword}'"
                    
                    // Send "no results" message
                    sendAnalysisResults(
                        noResultsMessage,
                        emptyList(),
                        action.deliveryMethod,
                        action.recipientEmail,
                        action.telegramChatId
                    )
                    
                    return@withContext ActionExecutionResult(
                        success = true,
                        message = noResultsMessage,
                        outputData = mapOf(action.outputVariable to noResultsMessage)
                    )
                }

                // Step 3: Analyze filtered images with LLM
                val analysisResults = analyzeImagesWithLLM(filteredImages, action.analysisPrompt)
                Log.i(TAG, "🤖 LLM analysis completed for ${analysisResults.size} images")

                // Step 4: Generate summary report
                val summaryReport = generateSummaryReport(
                    filteredImages, 
                    analysisResults, 
                    action.searchKeyword,
                    action.includeImagePaths
                )
                
                Log.i(TAG, "📄 Generated summary report (${summaryReport.length} characters)")

                // Step 5: Send results via Gmail or Telegram
                sendAnalysisResults(
                    summaryReport,
                    filteredImages,
                    action.deliveryMethod,
                    action.recipientEmail,
                    action.telegramChatId
                )

                Log.i(TAG, "✅ === 24-HOUR GALLERY ANALYSIS COMPLETE ===")
                
                ActionExecutionResult(
                    success = true,
                    message = "Successfully analyzed ${filteredImages.size} images and sent results via ${action.deliveryMethod}",
                    outputData = mapOf(
                        action.outputVariable to summaryReport,
                        "processed_images_count" to filteredImages.size.toString(),
                        "search_keyword" to action.searchKeyword
                    )
                )

            } catch (e: Exception) {
                Log.e(TAG, "💥 Error in 24-hour gallery analysis", e)
                ActionExecutionResult(
                    success = false,
                    message = "Failed to analyze gallery images: ${e.message}",
                    outputData = mapOf(action.outputVariable to "")
                )
            }
        }
    }

    /**
     * Get images from gallery that were taken in the last 24 hours
     */
    private suspend fun getRecentGalleryImages(): List<GalleryImage> = withContext(Dispatchers.IO) {
        val images = mutableListOf<GalleryImage>()
        val currentTime = System.currentTimeMillis()
        val yesterday = currentTime - HOURS_24_IN_MILLIS

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE
        )

        val selection = "${MediaStore.Images.Media.DATE_TAKEN} > ?"
        val selectionArgs = arrayOf(yesterday.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val path = cursor.getString(dataColumn)
                    val dateTaken = cursor.getLong(dateColumn)
                    val size = cursor.getLong(sizeColumn)

                    // Verify file exists
                    if (path != null && File(path).exists()) {
                        val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                        
                        images.add(GalleryImage(
                            id = id,
                            name = name ?: "Unknown",
                            path = path,
                            uri = uri,
                            dateTaken = dateTaken,
                            size = size
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying gallery images", e)
        }

        Log.d(TAG, "Retrieved ${images.size} images from last 24 hours")
        images
    }

    /**
     * Filter images by keyword using image analysis
     */
    private suspend fun filterImagesByKeyword(
        images: List<GalleryImage>,
        keyword: String,
        maxImages: Int
    ): List<GalleryImage> = withContext(Dispatchers.IO) {
        val filteredImages = mutableListOf<GalleryImage>()
        var processedCount = 0

        for (image in images) {
            if (filteredImages.size >= maxImages) break
            
            try {
                processedCount++
                Log.d(TAG, "🔍 Analyzing image ${processedCount}/${images.size}: ${image.name}")

                val bitmap = loadBitmapFromUri(image.uri)
                if (bitmap != null) {
                    // Quick analysis to check if keyword is present
                    val analysisResult = imageAnalysisService.analyzeImage(
                        bitmap = bitmap,
                        userQuestion = "Is there any '$keyword' in this image? Answer yes or no and briefly explain what you see."
                    )

                    if (analysisResult.success && containsKeyword(analysisResult.description, keyword)) {
                        filteredImages.add(image)
                        Log.d(TAG, "✅ Image contains '$keyword': ${image.name}")
                    } else {
                        Log.d(TAG, "❌ Image does not contain '$keyword': ${image.name}")
                    }
                } else {
                    Log.w(TAG, "⚠️ Could not load bitmap for ${image.name}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing image ${image.name}", e)
            }
        }

        Log.i(TAG, "Filtered ${filteredImages.size} images out of ${processedCount} processed")
        filteredImages
    }

    /**
     * Analyze filtered images with LLM using custom prompt
     */
    private suspend fun analyzeImagesWithLLM(
        images: List<GalleryImage>,
        analysisPrompt: String
    ): List<ImageAnalysisResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ImageAnalysisResult>()

        for ((index, image) in images.withIndex()) {
            try {
                Log.d(TAG, "🤖 LLM analyzing image ${index + 1}/${images.size}: ${image.name}")

                val bitmap = loadBitmapFromUri(image.uri)
                if (bitmap != null) {
                    val analysisResult = imageAnalysisService.analyzeImage(
                        bitmap = bitmap,
                        userQuestion = analysisPrompt
                    )

                    results.add(ImageAnalysisResult(
                        imageName = image.name,
                        imagePath = image.path,
                        dateTaken = image.dateTaken,
                        analysisText = analysisResult.description,
                        success = analysisResult.success,
                        confidence = analysisResult.confidence,
                        ocrText = analysisResult.ocrText
                    ))

                    Log.d(TAG, "✅ LLM analysis complete for ${image.name}")
                } else {
                    Log.w(TAG, "⚠️ Could not load bitmap for LLM analysis: ${image.name}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in LLM analysis for ${image.name}", e)
                results.add(ImageAnalysisResult(
                    imageName = image.name,
                    imagePath = image.path,
                    dateTaken = image.dateTaken,
                    analysisText = "Analysis failed: ${e.message}",
                    success = false
                ))
            }
        }

        results
    }

    /**
     * Generate comprehensive summary report
     */
    private fun generateSummaryReport(
        images: List<GalleryImage>,
        analysisResults: List<ImageAnalysisResult>,
        keyword: String,
        includeImagePaths: Boolean
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val now = Date()
        val yesterday = Date(now.time - HOURS_24_IN_MILLIS)

        return buildString {
            appendLine("📸 24-Hour Gallery Analysis Report")
            appendLine("═══════════════════════════════════════")
            appendLine("🔍 Search Keyword: '$keyword'")
            appendLine("📅 Analysis Period: ${dateFormat.format(yesterday)} to ${dateFormat.format(now)}")
            appendLine("📊 Images Found: ${images.size}")
            appendLine("🤖 Analysis Method: Local LLM")
            appendLine()

            if (analysisResults.isNotEmpty()) {
                appendLine("🖼️ DETAILED ANALYSIS RESULTS:")
                appendLine("─────────────────────────────────────")
                
                analysisResults.forEachIndexed { index, result ->
                    appendLine("\n📷 Image ${index + 1}: ${result.imageName}")
                    appendLine("   📅 Date: ${dateFormat.format(Date(result.dateTaken))}")
                    if (includeImagePaths && result.imagePath.isNotEmpty()) {
                        appendLine("   📁 Path: ${result.imagePath}")
                    }
                    appendLine("   🤖 Analysis: ${result.analysisText}")
                    if (result.ocrText.isNotEmpty()) {
                        appendLine("   📝 Text Found: ${result.ocrText}")
                    }
                    if (result.success) {
                        appendLine("   ✅ Status: Success (Confidence: ${String.format("%.1f", result.confidence * 100)}%)")
                    } else {
                        appendLine("   ❌ Status: Failed")
                    }
                }

                // Summary statistics
                val successfulAnalyses = analysisResults.count { it.success }
                val averageConfidence = analysisResults.filter { it.success }.map { it.confidence }.average()
                val imagesWithText = analysisResults.count { it.ocrText.isNotEmpty() }

                appendLine("\n📊 SUMMARY STATISTICS:")
                appendLine("─────────────────────")
                appendLine("✅ Successful Analyses: $successfulAnalyses/${analysisResults.size}")
                if (successfulAnalyses > 0) {
                    appendLine("📈 Average Confidence: ${String.format("%.1f", averageConfidence * 100)}%")
                }
                appendLine("📝 Images with Text: $imagesWithText")
                
            } else {
                appendLine("ℹ️ No images were successfully analyzed.")
            }

            appendLine("\n🔧 Generated by: Workflow Manager")
            appendLine("⏰ Report Time: ${dateFormat.format(now)}")
        }
    }

    /**
     * Send analysis results via Gmail or Telegram
     */
    private suspend fun sendAnalysisResults(
        report: String,
        images: List<GalleryImage>,
        deliveryMethod: String,
        recipientEmail: String,
        telegramChatId: Long
    ) {
        try {
            when (deliveryMethod.lowercase()) {
                "gmail" -> {
                    if (recipientEmail.isNotEmpty()) {
                        gmailService.sendEmail(
                            to = recipientEmail,
                            subject = "📸 24-Hour Gallery Analysis Report",
                            body = report
                        )
                        Log.i(TAG, "📧 Report sent via Gmail to $recipientEmail")
                    } else {
                        Log.w(TAG, "⚠️ No recipient email provided for Gmail delivery")
                    }
                }
                
                "telegram" -> {
                    if (telegramChatId != 0L) {
                        // Split long messages if needed (Telegram has 4096 character limit)
                        val maxLength = 4000
                        if (report.length <= maxLength) {
                            telegramService.sendMessage(telegramChatId, report)
                        } else {
                            val chunks = report.chunked(maxLength)
                            chunks.forEachIndexed { index, chunk ->
                                val header = if (index == 0) "" else "📄 (Part ${index + 1}/${chunks.size})\n\n"
                                telegramService.sendMessage(telegramChatId, header + chunk)
                            }
                        }
                        Log.i(TAG, "📱 Report sent via Telegram to chat $telegramChatId")
                    } else {
                        Log.w(TAG, "⚠️ No Telegram chat ID provided")
                    }
                }
                
                else -> {
                    Log.w(TAG, "⚠️ Unknown delivery method: $deliveryMethod")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending analysis results via $deliveryMethod", e)
        }
    }

    /**
     * Load bitmap from URI
     */
    private suspend fun loadBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from $uri", e)
            null
        }
    }

    /**
     * Check if analysis result contains the keyword
     */
    private fun containsKeyword(analysisText: String, keyword: String): Boolean {
        val text = analysisText.lowercase()
        val searchKeyword = keyword.lowercase()
        
        // Simple keyword matching - could be enhanced with more sophisticated NLP
        return text.contains(searchKeyword) || 
               text.contains("yes") && text.contains(searchKeyword) ||
               text.contains("found") && text.contains(searchKeyword) ||
               text.contains("see") && text.contains(searchKeyword) ||
               text.contains("there is") && text.contains(searchKeyword) ||
               text.contains("there are") && text.contains(searchKeyword)
    }
}

/**
 * Data class for gallery images
 */
data class GalleryImage(
    val id: Long,
    val name: String,
    val path: String,
    val uri: Uri,
    val dateTaken: Long,
    val size: Long
)

/**
 * Data class for image analysis results
 */
data class ImageAnalysisResult(
    val imageName: String,
    val imagePath: String,
    val dateTaken: Long,
    val analysisText: String,
    val success: Boolean,
    val confidence: Float = 0f,
    val ocrText: String = ""
)