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
                // Extract timeframe from maxImages field (backwards compatibility hack)
                val timeFrameHours = if (action.maxImages <= 24) action.maxImages else action.maxImages / 2
                
                Log.i(TAG, "🔍 === GALLERY ANALYSIS START ===")
                Log.i(TAG, "⏱️ Time frame: ${timeFrameHours} hours")
                Log.i(TAG, "📤 Delivery method: ${action.deliveryMethod}")
                Log.i(TAG, "🤖 Analysis prompt: '${action.analysisPrompt.take(50)}...'")
                
                // Auto-initialize Gmail if delivery method is Gmail and not already authenticated
                if (action.deliveryMethod.lowercase() == "gmail" && !gmailService.isSignedIn()) {
                    Log.i(TAG, "🔐 Gmail not authenticated, attempting auto-authentication...")
                    
                    try {
                        val initResult = gmailService.initialize()
                        initResult.fold(
                            onSuccess = {
                                if (gmailService.isSignedIn()) {
                                    Log.i(TAG, "✅ Gmail auto-authentication successful: ${gmailService.getCurrentUserEmail()}")
                                } else {
                                    Log.w(TAG, "⚠️ Gmail initialized but no signed-in account found")
                                    Log.i(TAG, "💡 SOLUTION: The Gmail service is checking for the last signed-in Google account")
                                    Log.i(TAG, "📋 If no account is found, user needs to sign in via WorkflowManager → Gmail tab")
                                }
                            },
                            onFailure = { error ->
                                Log.e(TAG, "❌ Gmail auto-authentication failed: ${error.message}")
                                Log.i(TAG, "💡 This is expected if no Google account has been previously signed in to the app")
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Exception during Gmail auto-authentication", e)
                    }
                }

                // Step 1: Get images from specified time frame
                val recentImages = getRecentGalleryImages(timeFrameHours)
                Log.i(TAG, "📷 Found ${recentImages.size} images from last ${timeFrameHours} hours")

                if (recentImages.isEmpty()) {
                    Log.w(TAG, "⚠️ No images found in the last ${timeFrameHours} hours (and fallback also failed)")
                    val noImagesMessage = "No images found in gallery from the last ${timeFrameHours} hours. Please take some photos and try again, or check that the app has permission to access your photos."
                    
                    // Send "no images" notification
                    val sendResult = sendAnalysisResults(
                        noImagesMessage,
                        emptyList(),
                        action.deliveryMethod,
                        action.recipientEmail,
                        action.telegramChatId
                    )
                    
                    return@withContext sendResult.fold(
                        onSuccess = { deliveryMessage ->
                            ActionExecutionResult(
                                success = true,
                                message = "$noImagesMessage Notification sent: $deliveryMessage",
                                outputData = mapOf(
                                    action.outputVariable to noImagesMessage,
                                    "delivery_status" to "success",
                                    "delivery_details" to deliveryMessage
                                )
                            )
                        },
                        onFailure = { error ->
                            ActionExecutionResult(
                                success = false,
                                message = "$noImagesMessage Additionally, failed to send notification: ${error.message}",
                                outputData = mapOf(
                                    action.outputVariable to noImagesMessage,
                                    "delivery_status" to "failed",
                                    "delivery_error" to error.message.toString()
                                )
                            )
                        }
                    )
                }

                // Step 2: Limit images if needed (no keyword filtering anymore)
                val maxImagesToProcess = if (action.maxImages > 0) action.maxImages.coerceAtMost(100) else 100
                val imagesToAnalyze = if (recentImages.size > maxImagesToProcess) {
                    recentImages.take(maxImagesToProcess)
                } else {
                    recentImages
                }
                Log.i(TAG, "📸 Processing ${imagesToAnalyze.size} images (limited from ${recentImages.size})")

                // Step 3: Smart filtering and analysis with LLM using custom prompt
                val analysisResults = smartAnalyzeImagesWithLLM(imagesToAnalyze, action.analysisPrompt)
                Log.i(TAG, "🤖 Smart LLM analysis completed: ${analysisResults.filteredImages.size} relevant images found, ${analysisResults.analysisResults.size} detailed analyses performed")

                // Step 4: Generate summary report
                val summaryReport = generateSummaryReport(
                    imagesToAnalyze, 
                    analysisResults.analysisResults,
                    analysisResults.consolidatedResult,
                    timeFrameHours,
                    action.includeImagePaths
                )
                
                Log.i(TAG, "📄 Generated summary report (${summaryReport.length} characters)")

                // Step 5: Send results via Gmail or Telegram
                val sendResult = sendAnalysisResults(
                    summaryReport,
                    imagesToAnalyze,
                    action.deliveryMethod,
                    action.recipientEmail,
                    action.telegramChatId
                )

                sendResult.fold(
                    onSuccess = { deliveryMessage ->
                        Log.i(TAG, "✅ === GALLERY ANALYSIS COMPLETE ===")
                        Log.i(TAG, "📧 $deliveryMessage")
                        
                        ActionExecutionResult(
                            success = true,
                            message = "Successfully analyzed ${analysisResults.filteredImages.size}/${imagesToAnalyze.size} relevant images from last ${timeFrameHours}h and sent results via ${action.deliveryMethod}: $deliveryMessage",
                            outputData = mapOf(
                                action.outputVariable to summaryReport,
                                "processed_images_count" to imagesToAnalyze.size.toString(),
                                "relevant_images_count" to analysisResults.filteredImages.size.toString(),
                                "detailed_analyses_count" to analysisResults.analysisResults.size.toString(),
                                "consolidated_result" to analysisResults.consolidatedResult,
                                "time_frame_hours" to timeFrameHours.toString(),
                                "delivery_status" to "success",
                                "delivery_details" to deliveryMessage
                            )
                        )
                    },
                    onFailure = { error ->
                        Log.e(TAG, "❌ === GALLERY ANALYSIS FAILED ===")
                        Log.e(TAG, "📧 Delivery failed: ${error.message}")
                        
                        ActionExecutionResult(
                            success = false,
                            message = "Gallery analysis completed but delivery failed: ${error.message}",
                            outputData = mapOf(
                                action.outputVariable to summaryReport,
                                "processed_images_count" to imagesToAnalyze.size.toString(),
                                "relevant_images_count" to analysisResults.filteredImages.size.toString(),
                                "detailed_analyses_count" to analysisResults.analysisResults.size.toString(),
                                "consolidated_result" to analysisResults.consolidatedResult,
                                "time_frame_hours" to timeFrameHours.toString(),
                                "delivery_status" to "failed",
                                "delivery_error" to error.message.toString()
                            )
                        )
                    }
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
    private suspend fun getRecentGalleryImages(timeFrameHours: Int = 24): List<GalleryImage> = withContext(Dispatchers.IO) {
        val images = mutableListOf<GalleryImage>()
        val currentTime = System.currentTimeMillis()
        val timeframeCutoff = currentTime - (timeFrameHours * 60 * 60 * 1000L)

        Log.d(TAG, "🕐 Current time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(currentTime))}")
        Log.d(TAG, "🕐 ${timeFrameHours} hours ago: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timeframeCutoff))}")

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE
        )

        val selection = "${MediaStore.Images.Media.DATE_TAKEN} > ?"
        val selectionArgs = arrayOf(timeframeCutoff.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        try {
            // First, check if we can access the MediaStore at all
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null, // No selection first to see if we can get any images
                null,
                sortOrder
            )?.use { cursor ->
                Log.d(TAG, "📊 Total images in gallery: ${cursor.count}")
                
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                var recentCount = 0
                var totalCount = 0
                
                while (cursor.moveToNext()) {
                    totalCount++
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val path = cursor.getString(dataColumn)
                    val dateTaken = cursor.getLong(dateColumn)
                    val size = cursor.getLong(sizeColumn)

                    // Log first few images for debugging
                    if (totalCount <= 5) {
                        val dateStr = if (dateTaken > 0) {
                            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(dateTaken))
                        } else {
                            "No date"
                        }
                        Log.d(TAG, "📷 Image $totalCount: $name, date: $dateStr, path: $path")
                    }

                    // Check if image is within timeframe
                    if (dateTaken > timeframeCutoff) {
                        recentCount++
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
                        } else {
                            Log.d(TAG, "⚠️ File does not exist: $path")
                        }
                    }
                }
                
                Log.d(TAG, "📊 Images from last ${timeFrameHours}h (before file verification): $recentCount")
                Log.d(TAG, "📊 Images from last ${timeFrameHours}h (after file verification): ${images.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying gallery images", e)
        }

        // If no recent images found, try with a longer timeframe as fallback for testing
        if (images.isEmpty() && timeFrameHours < 168) { // Only fallback if timeframe is less than 7 days
            val fallbackHours = if (timeFrameHours < 24) 168 else timeFrameHours * 3 // 7 days or 3x original
            Log.i(TAG, "🔄 No images found in last ${timeFrameHours}h, trying last ${fallbackHours}h as fallback...")
            val fallbackCutoff = currentTime - (fallbackHours * 60 * 60 * 1000L)
            
            try {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Images.Media.DATE_TAKEN} > ?",
                    arrayOf(fallbackCutoff.toString()),
                    sortOrder
                )?.use { cursor ->
                    Log.d(TAG, "📊 Images from last ${fallbackHours}h: ${cursor.count}")
                    
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                    var count = 0
                    while (cursor.moveToNext() && count < 5) { // Limit to 5 for testing
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn)
                        val path = cursor.getString(dataColumn)
                        val dateTaken = cursor.getLong(dateColumn)
                        val size = cursor.getLong(sizeColumn)

                        if (path != null && File(path).exists()) {
                            val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                            
                            images.add(GalleryImage(
                                id = id,
                                name = name ?: "Unknown (${fallbackHours}h fallback)",
                                path = path,
                                uri = uri,
                                dateTaken = dateTaken,
                                size = size
                            ))
                            count++
                        }
                    }
                    
                    if (images.isNotEmpty()) {
                        Log.i(TAG, "✅ Found ${images.size} images from last ${fallbackHours}h to use as fallback")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying gallery images (${fallbackHours}h fallback)", e)
            }
        }

        Log.d(TAG, "Retrieved ${images.size} images for analysis")
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
    private suspend fun smartAnalyzeImagesWithLLM(
        images: List<GalleryImage>,
        analysisPrompt: String
    ): SmartAnalysisResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "🎯 === SMART IMAGE ANALYSIS START ===")
        Log.i(TAG, "📋 Analysis Prompt: '${analysisPrompt.take(100)}${if(analysisPrompt.length > 100) "..." else ""}'")
        Log.i(TAG, "📷 Total images to process: ${images.size}")
        
        // Phase 1: Quick filtering to identify relevant images
        Log.i(TAG, "🔍 PHASE 1: Quick filtering to identify relevant images")
        val relevantImages = mutableListOf<GalleryImage>()
        val filteringResults = mutableListOf<ImageFilterResult>()
        
        for ((index, image) in images.withIndex()) {
            try {
                Log.d(TAG, "🔎 Quick scan ${index + 1}/${images.size}: ${image.name}")
                
                val bitmap = loadBitmapFromUri(image.uri)
                if (bitmap != null) {
                    // Create a filtering prompt that asks if the image matches the criteria
                    val filteringPrompt = createFilteringPrompt(analysisPrompt)
                    
                    val filterResult = imageAnalysisService.analyzeImage(
                        bitmap = bitmap,
                        userQuestion = filteringPrompt
                    )
                    
                    val isRelevant = isImageRelevant(filterResult.description, analysisPrompt)
                    
                    filteringResults.add(ImageFilterResult(
                        image = image,
                        isRelevant = isRelevant,
                        confidence = filterResult.confidence,
                        filterDescription = filterResult.description
                    ))
                    
                    if (isRelevant) {
                        relevantImages.add(image)
                        Log.i(TAG, "✅ RELEVANT: ${image.name} - ${filterResult.description.take(50)}...")
                    } else {
                        Log.d(TAG, "❌ Not relevant: ${image.name}")
                    }
                } else {
                    Log.w(TAG, "⚠️ Could not load bitmap for filtering: ${image.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in filtering for ${image.name}", e)
            }
        }
        
        Log.i(TAG, "🎯 PHASE 1 COMPLETE: Found ${relevantImages.size}/${images.size} relevant images")
        
        if (relevantImages.isEmpty()) {
            Log.w(TAG, "😕 No relevant images found for the given prompt")
            return@withContext SmartAnalysisResult(
                filteredImages = emptyList(),
                analysisResults = emptyList(),
                consolidatedResult = "No images found matching the criteria: '${analysisPrompt}'",
                filteringResults = filteringResults
            )
        }
        
        // Phase 2: Detailed analysis of relevant images only
        Log.i(TAG, "🔍 PHASE 2: Detailed analysis of ${relevantImages.size} relevant images")
        val detailedResults = mutableListOf<GalleryImageAnalysisResult>()
        
        for ((index, image) in relevantImages.withIndex()) {
            try {
                Log.d(TAG, "🤖 Detailed analysis ${index + 1}/${relevantImages.size}: ${image.name}")
                
                val bitmap = loadBitmapFromUri(image.uri)
                if (bitmap != null) {
                    val analysisResult = imageAnalysisService.analyzeImage(
                        bitmap = bitmap,
                        userQuestion = analysisPrompt
                    )
                    
                    detailedResults.add(GalleryImageAnalysisResult(
                        imageName = image.name,
                        imagePath = image.path,
                        dateTaken = image.dateTaken,
                        analysisText = analysisResult.description,
                        success = analysisResult.success,
                        confidence = analysisResult.confidence,
                        ocrText = analysisResult.ocrText
                    ))
                    
                    Log.d(TAG, "✅ Detailed analysis complete for ${image.name}")
                } else {
                    Log.w(TAG, "⚠️ Could not load bitmap for detailed analysis: ${image.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in detailed analysis for ${image.name}", e)
                detailedResults.add(GalleryImageAnalysisResult(
                    imageName = image.name,
                    imagePath = image.path,
                    dateTaken = image.dateTaken,
                    analysisText = "Analysis failed: ${e.message}",
                    success = false
                ))
            }
        }
        
        // Phase 3: Generate consolidated result from all relevant analyses
        Log.i(TAG, "🔍 PHASE 3: Generating consolidated result")
        val consolidatedResult = generateConsolidatedResult(detailedResults, analysisPrompt)
        
        Log.i(TAG, "🎆 === SMART IMAGE ANALYSIS COMPLETE ===")
        Log.i(TAG, "📊 Results: ${relevantImages.size} relevant images, ${detailedResults.size} detailed analyses")
        Log.i(TAG, "📋 Consolidated: ${consolidatedResult.take(100)}${if(consolidatedResult.length > 100) "..." else ""}")
        
        SmartAnalysisResult(
            filteredImages = relevantImages,
            analysisResults = detailedResults,
            consolidatedResult = consolidatedResult,
            filteringResults = filteringResults
        )
    }

    /**
     * Create a filtering prompt to quickly determine if image matches criteria
     */
    private fun createFilteringPrompt(originalPrompt: String): String {
        // Extract the key criteria from the original prompt
        val lowercasePrompt = originalPrompt.lowercase()
        
        return when {
            // Receipt detection
            lowercasePrompt.contains("receipt") -> 
                "Is this image a receipt, bill, or invoice? Answer YES or NO and briefly explain what you see."
                
            // Food detection
            lowercasePrompt.contains("food") || lowercasePrompt.contains("meal") || lowercasePrompt.contains("dish") -> 
                "Is this image showing food, meals, or dishes? Answer YES or NO and briefly explain what you see."
                
            // Document detection
            lowercasePrompt.contains("document") || lowercasePrompt.contains("text") || lowercasePrompt.contains("paper") -> 
                "Is this image a document with text or written content? Answer YES or NO and briefly explain what you see."
                
            // People detection
            lowercasePrompt.contains("people") || lowercasePrompt.contains("person") || lowercasePrompt.contains("human") -> 
                "Are there people or persons visible in this image? Answer YES or NO and briefly explain what you see."
                
            // Vehicle detection
            lowercasePrompt.contains("car") || lowercasePrompt.contains("vehicle") || lowercasePrompt.contains("bike") -> 
                "Is this image showing cars, vehicles, or transportation? Answer YES or NO and briefly explain what you see."
                
            // Generic filtering based on the prompt
            else -> 
                "Based on this request: '$originalPrompt', is this image relevant? Answer YES or NO and briefly explain what you see."
        }
    }
    
    /**
     * Determine if an image is relevant based on the filter result
     */
    private fun isImageRelevant(filterDescription: String, originalPrompt: String): Boolean {
        val description = filterDescription.lowercase()
        
        // Look for positive indicators
        val positiveIndicators = listOf(
            "yes", "this is", "contains", "shows", "has", "displays", 
            "visible", "present", "appears to be", "looks like"
        )
        
        // Look for negative indicators
        val negativeIndicators = listOf(
            "no", "not", "doesn't", "does not", "cannot", "can't", 
            "unable", "nothing", "none", "absent"
        )
        
        // Check for explicit YES/NO answers first
        if (description.startsWith("yes") || description.contains(" yes ")) {
            return true
        }
        if (description.startsWith("no") || description.contains(" no ")) {
            return false
        }
        
        // Score based on positive vs negative indicators
        val positiveScore = positiveIndicators.count { description.contains(it) }
        val negativeScore = negativeIndicators.count { description.contains(it) }
        
        // Return true if more positive indicators than negative
        return positiveScore > negativeScore
    }
    
    /**
     * Generate consolidated result from multiple image analyses
     */
    private fun generateConsolidatedResult(
        analysisResults: List<GalleryImageAnalysisResult>,
        originalPrompt: String
    ): String {
        if (analysisResults.isEmpty()) {
            return "No relevant images found for analysis."
        }
        
        val successfulAnalyses = analysisResults.filter { it.success }
        if (successfulAnalyses.isEmpty()) {
            return "Found ${analysisResults.size} relevant images but analysis failed for all of them."
        }
        
        return buildString {
            appendLine("🎯 CONSOLIDATED ANALYSIS RESULT:")
            appendLine("======================================")
            appendLine("📊 Found ${successfulAnalyses.size} relevant images matching your request.")
            appendLine()
            
            // Try to extract and consolidate specific information based on prompt type
            val lowercasePrompt = originalPrompt.lowercase()
            
            when {
                // Handle receipt/spending analysis
                lowercasePrompt.contains("total") && (lowercasePrompt.contains("spending") || lowercasePrompt.contains("cost") || lowercasePrompt.contains("amount")) -> {
                    appendLine("💰 SPENDING ANALYSIS:")
                    appendLine("-------------------")
                    
                    val allAnalysisText = successfulAnalyses.joinToString("\n") { it.analysisText }
                    val extractedAmounts = extractMonetaryAmounts(allAnalysisText)
                    
                    if (extractedAmounts.isNotEmpty()) {
                        appendLine("💵 Individual amounts found:")
                        extractedAmounts.forEach { amount ->
                            appendLine("   • $amount")
                        }
                        
                        val totalSpending = calculateTotalSpending(extractedAmounts)
                        appendLine()
                        appendLine("📋 TOTAL SPENDING: $totalSpending")
                    } else {
                        appendLine("Could not extract specific monetary amounts from the receipts.")
                        appendLine("Here's what was found:")
                        successfulAnalyses.forEach { result ->
                            appendLine("• ${result.imageName}: ${result.analysisText}")
                        }
                    }
                }
                
                // Handle counting requests
                lowercasePrompt.contains("count") || lowercasePrompt.contains("how many") -> {
                    appendLine("🔢 COUNT ANALYSIS:")
                    appendLine("----------------")
                    
                    val allAnalysisText = successfulAnalyses.joinToString(" ") { it.analysisText }
                    val extractedNumbers = extractNumbers(allAnalysisText)
                    
                    if (extractedNumbers.isNotEmpty()) {
                        val total = extractedNumbers.sum()
                        appendLine("📋 TOTAL COUNT: $total")
                        appendLine("📊 Breakdown: ${extractedNumbers.joinToString(" + ")} = $total")
                    } else {
                        appendLine("Individual results:")
                        successfulAnalyses.forEach { result ->
                            appendLine("• ${result.imageName}: ${result.analysisText}")
                        }
                    }
                }
                
                // Generic consolidation
                else -> {
                    appendLine("📋 ANALYSIS SUMMARY:")
                    appendLine("------------------")
                    
                    // Group similar findings
                    val commonFindings = findCommonFindings(successfulAnalyses)
                    if (commonFindings.isNotEmpty()) {
                        appendLine("🔄 Common findings across images:")
                        commonFindings.forEach { finding ->
                            appendLine("   • $finding")
                        }
                        appendLine()
                    }
                    
                    appendLine("📁 Individual image results:")
                    successfulAnalyses.forEach { result ->
                        appendLine("• ${result.imageName}: ${result.analysisText}")
                    }
                }
            }
            
            appendLine()
            appendLine("🚀 Analysis completed successfully!")
        }
    }
    
    /**
     * Extract monetary amounts from analysis text
     */
    private fun extractMonetaryAmounts(text: String): List<String> {
        val amounts = mutableListOf<String>()
        
        // Regex patterns for different currency formats
        val patterns = listOf(
            Regex("\\$[0-9,]+\\.?[0-9]*"), // $123.45, $1,234
            Regex("[0-9,]+\\.?[0-9]*\\s*dollars?"), // 123 dollars
            Regex("USD\\s*[0-9,]+\\.?[0-9]*"), // USD 123.45
            Regex("\\b[0-9,]+\\.?[0-9]*\\s*\\$"), // 123.45 $
            Regex("total[:\\s]*\\$?[0-9,]+\\.?[0-9]*", RegexOption.IGNORE_CASE), // total: $123.45
            Regex("amount[:\\s]*\\$?[0-9,]+\\.?[0-9]*", RegexOption.IGNORE_CASE), // amount: 123.45
        )
        
        patterns.forEach { pattern ->
            amounts.addAll(pattern.findAll(text).map { it.value }.toList())
        }
        
        return amounts.distinct()
    }
    
    /**
     * Calculate total spending from extracted amounts
     */
    private fun calculateTotalSpending(amounts: List<String>): String {
        var total = 0.0
        
        amounts.forEach { amount ->
            // Extract numeric value from amount string
            val numericValue = Regex("[0-9,]+\\.?[0-9]*").find(amount)?.value?.replace(",", "")
            numericValue?.toDoubleOrNull()?.let { value ->
                total += value
            }
        }
        
        return if (total > 0) String.format("$%.2f", total) else "Unable to calculate total"
    }
    
    /**
     * Extract numbers from text for counting
     */
    private fun extractNumbers(text: String): List<Int> {
        val numbers = mutableListOf<Int>()
        
        Regex("\\b([0-9]+)\\b").findAll(text).forEach { match ->
            match.value.toIntOrNull()?.let { numbers.add(it) }
        }
        
        return numbers
    }
    
    /**
     * Find common findings across multiple analysis results
     */
    private fun findCommonFindings(results: List<GalleryImageAnalysisResult>): List<String> {
        val commonFindings = mutableListOf<String>()
        
        // Look for common keywords/phrases that appear in multiple results
        val allText = results.joinToString(" ") { it.analysisText.lowercase() }
        val words = allText.split(Regex("[\\s,;.!?]+")).filter { it.length > 3 }
        
        // Find words that appear multiple times
        val wordCounts = words.groupBy { it }.mapValues { it.value.size }
        val commonWords = wordCounts.filter { it.value > 1 && it.value >= results.size / 2 }
        
        if (commonWords.isNotEmpty()) {
            commonFindings.add("Frequently mentioned: ${commonWords.keys.take(5).joinToString(", ")}")
        }
        
        return commonFindings
    }

    /**
     * Generate comprehensive summary report
     */
    private fun generateSummaryReport(
        images: List<GalleryImage>,
        analysisResults: List<GalleryImageAnalysisResult>,
        consolidatedResult: String,
        timeFrameHours: Int,
        includeImagePaths: Boolean
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val now = Date()
        val timeFrameStart = Date(now.time - (timeFrameHours * 60 * 60 * 1000L))

        return buildString {
            appendLine("📸 Gallery Analysis Report")
            appendLine("═══════════════════════════════════════")
            appendLine("📅 Analysis Period: ${dateFormat.format(timeFrameStart)} to ${dateFormat.format(now)}")
            appendLine("⏳ Time Frame: ${timeFrameHours} hours")
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
    ): Result<String> {
        try {
            when (deliveryMethod.lowercase()) {
                "gmail" -> {
                    if (recipientEmail.isNotEmpty()) {
                        // Check if Gmail is authenticated
                        if (!gmailService.isSignedIn()) {
                            Log.e(TAG, "🔐 Gmail not authenticated! Cannot send email reports.")
                            Log.e(TAG, "📋 SOLUTION: Go to WorkflowManager → Gmail tab → Sign in with your Gmail account")
                            Log.e(TAG, "📧 Recipients that would have received reports: $recipientEmail")
                            Log.e(TAG, "🔍 Gmail Service Status:")
                            Log.e(TAG, "   - Current account: ${gmailService.getCurrentUserEmail() ?: "None"}")
                            Log.e(TAG, "   - Is signed in: ${gmailService.isSignedIn()}")
                            return Result.failure(Exception("Gmail not authenticated. The app needs you to sign in to Gmail in WorkflowManager → Gmail tab first, then try the workflow again."))
                        }
                        
                        // Handle comma-separated email addresses
                        val emailAddresses = recipientEmail.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val failedEmails = mutableListOf<String>()
                        val successfulEmails = mutableListOf<String>()
                        
                        for (email in emailAddresses) {
                            val result = gmailService.sendEmail(
                                to = email,
                                subject = "📸 Gallery Analysis Report",
                                body = report
                            )
                            
                            result.fold(
                                onSuccess = { messageId ->
                                    Log.i(TAG, "📧 Report sent via Gmail to $email (Message ID: $messageId)")
                                    successfulEmails.add(email)
                                },
                                onFailure = { error ->
                                    Log.e(TAG, "❌ Failed to send Gmail to $email: ${error.message}")
                                    failedEmails.add(email)
                                    if (error.message?.contains("not initialized") == true || 
                                       error.message?.contains("not authenticated") == true) {
                                        Log.e(TAG, "🔐 Gmail authentication required! Please sign in to Gmail in WorkflowManager → Gmail tab first.")
                                        Log.e(TAG, "💡 The auto-authentication attempt was not sufficient - manual sign-in is required.")
                                    }
                                }
                            )
                        }
                        
                        // Check if any emails failed to send
                        if (failedEmails.isNotEmpty()) {
                            val errorMessage = "Failed to send Gmail reports to: ${failedEmails.joinToString(", ")}. " +
                                    if (successfulEmails.isNotEmpty()) "Successfully sent to: ${successfulEmails.joinToString(", ")}" else "No emails sent successfully."
                            Log.e(TAG, "❌ $errorMessage")
                            return Result.failure(Exception(errorMessage))
                        }
                        
                        val successMessage = "Successfully sent Gmail reports to ${successfulEmails.size} recipients: ${successfulEmails.joinToString(", ")}"
                        Log.i(TAG, "✅ $successMessage")
                        return Result.success(successMessage)
                    } else {
                        Log.w(TAG, "⚠️ No recipient email provided for Gmail delivery")
                        return Result.failure(Exception("No recipient email provided for Gmail delivery"))
                    }
                }
                
                "telegram" -> {
                    if (telegramChatId != 0L) {
                        try {
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
                            return Result.success("Successfully sent Telegram report to chat $telegramChatId")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to send Telegram message: ${e.message}")
                            return Result.failure(Exception("Failed to send Telegram report: ${e.message}"))
                        }
                    } else {
                        Log.w(TAG, "⚠️ No Telegram chat ID provided")
                        return Result.failure(Exception("No Telegram chat ID provided"))
                    }
                }
                
                else -> {
                    Log.w(TAG, "⚠️ Unknown delivery method: $deliveryMethod")
                    return Result.failure(Exception("Unknown delivery method: $deliveryMethod"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending analysis results via $deliveryMethod", e)
            return Result.failure(Exception("Failed to send analysis results via $deliveryMethod: ${e.message}"))
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
 * Data class for gallery image analysis results
 */
data class GalleryImageAnalysisResult(
    val imageName: String,
    val imagePath: String,
    val dateTaken: Long,
    val analysisText: String,
    val success: Boolean,
    val confidence: Float = 0f,
    val ocrText: String = ""
)

/**
 * Data class for smart analysis results
 */
data class SmartAnalysisResult(
    val filteredImages: List<GalleryImage>,
    val analysisResults: List<GalleryImageAnalysisResult>,
    val consolidatedResult: String,
    val filteringResults: List<ImageFilterResult>
)

/**
 * Data class for image filtering results
 */
data class ImageFilterResult(
    val image: GalleryImage,
    val isRelevant: Boolean,
    val confidence: Float,
    val filterDescription: String
)
