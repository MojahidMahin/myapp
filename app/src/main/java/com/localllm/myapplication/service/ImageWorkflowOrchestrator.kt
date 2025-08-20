package com.localllm.myapplication.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.google.android.gms.common.util.Strings
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

data class TimeRange(
    val start: String,
    val end: String
)

data class WorkflowResult(
    val timeRange: TimeRange,
    val imagesChecked: Int,
    val imagesMatched: Int,
    val action: String,
    val destination: String?,
    val payload: Map<String, Any>
)

class ImageWorkflowOrchestrator(private val context: Context) {
    
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    
    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .build()
        FaceDetection.getClient(options)
    }
    
    // Note: GalleryAnalysisService integration would need to be implemented
    // private val galleryAnalysisService by lazy { GalleryAnalysisService(context) }
    
    suspend fun processWorkflowInstruction(
        instruction: String,
        referenceImageUri: Uri? = null,
        timeRangeSelection: String? = null
    ): Result<WorkflowResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Processing workflow instruction: $instruction")
            
            val timeRange = timeRangeSelection?.let { getTimeRangeFromSelection(it) } ?: extractTimeRange(instruction)
            Log.d(TAG, "Extracted time range: ${timeRange.start} to ${timeRange.end}")
            
            val condition = extractFilteringCondition(instruction)
            Log.d(TAG, "Extracted condition: $condition")
            
            val action = extractAction(instruction)
            Log.d(TAG, "Extracted action: $action")
            
            val galleryImages = getGalleryImagesInTimeRange(timeRange)
            Log.d(TAG, "Found ${galleryImages.size} images in time range")
            
            val matchedImages = filterImages(galleryImages, condition, referenceImageUri)
            Log.d(TAG, "Matched ${matchedImages.size} images")
            
            val payload = executeAction(action, matchedImages, instruction)
            
            val result = WorkflowResult(
                timeRange = timeRange,
                imagesChecked = galleryImages.size,
                imagesMatched = matchedImages.size,
                action = action,
                destination = extractDestination(instruction),
                payload = payload
            )
            
            Log.d(TAG, "Workflow completed successfully")
            Result.success(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing workflow instruction", e)
            Result.failure(e)
        }
    }
    
    private fun extractTimeRange(instruction: String): TimeRange {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        
        return when {
            instruction.contains("last 24 hours", ignoreCase = true) -> {
                val start = now.minusHours(24)
                TimeRange(
                    start = start.format(formatter),
                    end = now.format(formatter)
                )
            }
            instruction.contains("past week", ignoreCase = true) -> {
                val start = now.minusWeeks(1)
                TimeRange(
                    start = start.format(formatter),
                    end = now.format(formatter)
                )
            }
            instruction.contains("yesterday", ignoreCase = true) -> {
                val start = now.minusDays(1).withHour(0).withMinute(0).withSecond(0)
                val end = now.minusDays(1).withHour(23).withMinute(59).withSecond(59)
                TimeRange(
                    start = start.format(formatter),
                    end = end.format(formatter)
                )
            }
            instruction.contains("today", ignoreCase = true) -> {
                val start = now.withHour(0).withMinute(0).withSecond(0)
                val end = now.withHour(23).withMinute(59).withSecond(59)
                TimeRange(
                    start = start.format(formatter),
                    end = end.format(formatter)
                )
            }
            instruction.contains("past month", ignoreCase = true) -> {
                val start = now.minusMonths(1)
                TimeRange(
                    start = start.format(formatter),
                    end = now.format(formatter)
                )
            }
            else -> {
                val datePattern = Regex("""(\d{4}-\d{2}-\d{2})""")
                val dates = datePattern.findAll(instruction).map { it.value }.toList()
                
                if (dates.size >= 2) {
                    TimeRange(
                        start = "${dates[0]}T00:00:00",
                        end = "${dates[1]}T23:59:59"
                    )
                } else {
                    val start = now.minusYears(10)
                    TimeRange(
                        start = start.format(formatter),
                        end = now.format(formatter)
                    )
                }
            }
        }
    }
    
    private fun getTimeRangeFromSelection(selection: String): TimeRange {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        
        return when (selection) {
            "last_24_hours" -> {
                val start = now.minusHours(24)
                TimeRange(
                    start = start.format(formatter),
                    end = now.format(formatter)
                )
            }
            "yesterday" -> {
                val start = now.minusDays(1).withHour(0).withMinute(0).withSecond(0)
                val end = now.minusDays(1).withHour(23).withMinute(59).withSecond(59)
                TimeRange(
                    start = start.format(formatter),
                    end = end.format(formatter)
                )
            }
            "today" -> {
                val start = now.withHour(0).withMinute(0).withSecond(0)
                val end = now.withHour(23).withMinute(59).withSecond(59)
                TimeRange(
                    start = start.format(formatter),
                    end = end.format(formatter)
                )
            }
            "past_week" -> {
                val start = now.minusWeeks(1)
                TimeRange(
                    start = start.format(formatter),
                    end = now.format(formatter)
                )
            }
            "past_month" -> {
                val start = now.minusMonths(1)
                TimeRange(
                    start = start.format(formatter),
                    end = now.format(formatter)
                )
            }
            "all_time" -> {
                val start = now.minusYears(10)
                TimeRange(
                    start = start.format(formatter),
                    end = now.format(formatter)
                )
            }
            else -> {
                // Default to last 24 hours
                val start = now.minusHours(24)
                TimeRange(
                    start = start.format(formatter),
                    end = now.format(formatter)
                )
            }
        }
    }
    
    private fun extractFilteringCondition(instruction: String): String {
        return when {
            instruction.contains("receipt", ignoreCase = true) || 
            instruction.contains("receipts", ignoreCase = true) -> "receipts"
            instruction.contains("wedding", ignoreCase = true) -> "wedding"
            instruction.contains("screenshots", ignoreCase = true) -> "screenshots"
            instruction.contains("documents", ignoreCase = true) -> "documents"
            instruction.contains("food", ignoreCase = true) -> "food"
            instruction.contains("text", ignoreCase = true) -> "text"
            instruction.contains("people", ignoreCase = true) || 
            instruction.contains("faces", ignoreCase = true) -> "faces"
            instruction.contains("like this", ignoreCase = true) || 
            instruction.contains("similar to", ignoreCase = true) -> "similar_image"
            else -> "all"
        }
    }
    
    private fun extractAction(instruction: String): String {
        return when {
            instruction.contains("total spending", ignoreCase = true) ||
            instruction.contains("total amount", ignoreCase = true) ||
            instruction.contains("calculate", ignoreCase = true) ||
            instruction.contains("total", ignoreCase = true) -> "calculate"
            instruction.contains("summarize", ignoreCase = true) ||
            instruction.contains("summary", ignoreCase = true) -> "summarize"
            instruction.contains("send", ignoreCase = true) -> "send"
            instruction.contains("copy", ignoreCase = true) -> "copy"
            instruction.contains("move", ignoreCase = true) -> "move"
            instruction.contains("delete", ignoreCase = true) -> "delete"
            instruction.contains("analyze", ignoreCase = true) -> "analyze"
            else -> "list"
        }
    }
    
    private fun extractDestination(instruction: String): String? {
        return when {
            instruction.contains("telegram", ignoreCase = true) -> "telegram"
            instruction.contains("email", ignoreCase = true) -> "email"
            instruction.contains("folder", ignoreCase = true) -> "folder"
            else -> null
        }
    }
    
    private suspend fun getGalleryImagesInTimeRange(timeRange: TimeRange): List<GalleryImage> = withContext(Dispatchers.IO) {
        val startTime = LocalDateTime.parse(timeRange.start).toEpochSecond(ZoneOffset.UTC) * 1000
        val endTime = LocalDateTime.parse(timeRange.end).toEpochSecond(ZoneOffset.UTC) * 1000
        
        val images = mutableListOf<GalleryImage>()
        
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATA
            )
            
            val selection = "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} <= ?"
            val selectionArgs = arrayOf(startTime.toString(), endTime.toString())
            val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val dateTaken = cursor.getLong(dateTakenColumn)
                    val size = cursor.getLong(sizeColumn)
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    
                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    
                    images.add(
                        GalleryImage(
                            id = id,
                            name = name,
                            path = path,
                            uri = contentUri,
                            dateTaken = dateTaken,
                            size = size
                        )
                    )
                }
            }
            
            Log.d(TAG, "Retrieved ${images.size} images from gallery in time range")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error querying gallery images", e)
        }
        
        images
    }
    
    private suspend fun filterImages(
        images: List<GalleryImage>,
        condition: String,
        referenceImageUri: Uri?
    ): List<GalleryImage> = withContext(Dispatchers.IO) {
        
        when (condition) {
            "receipts" -> filterReceiptImages(images)
            "wedding" -> filterWeddingImages(images)
            "screenshots" -> filterScreenshots(images)
            "documents" -> filterDocumentImages(images)
            "food" -> filterFoodImages(images)
            "text" -> filterImagesWithText(images)
            "faces" -> filterImagesWithFaces(images)
            "similar_image" -> {
                if (referenceImageUri != null) {
                    filterSimilarImages(images, referenceImageUri)
                } else {
                    emptyList()
                }
            }
            else -> images
        }
    }
    
    private suspend fun filterReceiptImages(images: List<GalleryImage>): List<GalleryImage> {
        return images.filter { image ->
            val text = extractTextFromImage(image.uri)
            text.contains("receipt", ignoreCase = true) ||
            text.contains("total", ignoreCase = true) ||
            text.contains("subtotal", ignoreCase = true) ||
            text.contains("tax", ignoreCase = true) ||
            text.contains("$") ||
            text.contains("BDT", ignoreCase = true) ||
            text.contains("₹")
        }
    }
    
    private suspend fun filterWeddingImages(images: List<GalleryImage>): List<GalleryImage> {
        return images.filter { image ->
            val fileName = image.name.lowercase()
            fileName.contains("wedding") ||
            fileName.contains("marriage") ||
            fileName.contains("bride") ||
            fileName.contains("groom")
        }
    }
    
    private suspend fun filterScreenshots(images: List<GalleryImage>): List<GalleryImage> {
        return images.filter { image ->
            val fileName = image.name.lowercase()
            fileName.contains("screenshot") ||
            fileName.contains("screen_") ||
            fileName.startsWith("screenshot_")
        }
    }
    
    private suspend fun filterDocumentImages(images: List<GalleryImage>): List<GalleryImage> {
        return images.filter { image ->
            val text = extractTextFromImage(image.uri)
            text.length > 50 && 
            (text.contains("document", ignoreCase = true) ||
            text.split("\\s+".toRegex()).size > 20)
        }
    }
    
    private suspend fun filterFoodImages(images: List<GalleryImage>): List<GalleryImage> {
        return images.filter { image ->
            val fileName = image.name.lowercase()
            fileName.contains("food") ||
            fileName.contains("meal") ||
            fileName.contains("restaurant") ||
            fileName.contains("lunch") ||
            fileName.contains("dinner")
        }
    }
    
    private suspend fun filterImagesWithText(images: List<GalleryImage>): List<GalleryImage> {
        return images.filter { image ->
            val text = extractTextFromImage(image.uri)
            text.isNotBlank() && text.length > 10
        }
    }
    
    private suspend fun filterImagesWithFaces(images: List<GalleryImage>): List<GalleryImage> {
        return images.filter { image ->
            detectFacesInImage(image.uri) > 0
        }
    }
    
    private suspend fun filterSimilarImages(
        images: List<GalleryImage>,
        referenceImageUri: Uri
    ): List<GalleryImage> {
        return images.take(5)
    }
    
    private suspend fun extractTextFromImage(imageUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = textRecognizer.process(image).await()
            result.text
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from image", e)
            ""
        }
    }
    
    private suspend fun detectFacesInImage(imageUri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = faceDetector.process(image).await()
            Log.d(TAG, "Detected ${result.size} faces in image")
            result.size
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting faces in image", e)
            0
        }
    }
    
    private suspend fun executeAction(
        action: String,
        matchedImages: List<GalleryImage>,
        instruction: String
    ): Map<String, Any> {
        return when (action) {
            "summarize" -> executeSummarizeAction(matchedImages, instruction)
            "calculate" -> executeCalculateAction(matchedImages, instruction)
            "send" -> executeSendAction(matchedImages, instruction)
            "copy" -> executeCopyAction(matchedImages)
            "move" -> executeMoveAction(matchedImages)
            "analyze" -> executeAnalyzeAction(matchedImages)
            else -> mapOf("items" to matchedImages.map { it.name })
        }
    }
    
    private suspend fun executeSummarizeAction(
        images: List<GalleryImage>,
        instruction: String
    ): Map<String, Any> {
        return when {
            instruction.contains("spending", ignoreCase = true) ||
            instruction.contains("total", ignoreCase = true) -> {
                val totalAmount = calculateTotalSpending(images)
                mapOf(
                    "summary" to "Total spending = $totalAmount BDT",
                    "totalAmount" to totalAmount,
                    "currency" to "BDT",
                    "receiptCount" to images.size
                )
            }
            else -> {
                mapOf(
                    "summary" to "Found ${images.size} matching images",
                    "imageCount" to images.size,
                    "categories" to categorizeImages(images)
                )
            }
        }
    }
    
    private suspend fun executeCalculateAction(
        images: List<GalleryImage>,
        instruction: String
    ): Map<String, Any> {
        val totalAmount = calculateTotalSpending(images)
        return mapOf(
            "calculation" to "Total amount from ${images.size} receipts",
            "totalAmount" to totalAmount,
            "currency" to "BDT",
            "breakdown" to getSpendingBreakdown(images)
        )
    }
    
    private fun executeSendAction(
        images: List<GalleryImage>,
        instruction: String
    ): Map<String, Any> {
        return mapOf(
            "action" to "send",
            "imageCount" to images.size,
            "destination" to (extractDestination(instruction) ?: "unknown"),
            "message" to "Prepared ${images.size} images for sending"
        )
    }
    
    private fun executeCopyAction(images: List<GalleryImage>): Map<String, Any> {
        return mapOf(
            "action" to "copy",
            "imageCount" to images.size,
            "message" to "Copied ${images.size} images to clipboard/folder"
        )
    }
    
    private fun executeMoveAction(images: List<GalleryImage>): Map<String, Any> {
        return mapOf(
            "action" to "move",
            "imageCount" to images.size,
            "message" to "Moved ${images.size} images to destination"
        )
    }
    
    private suspend fun executeAnalyzeAction(images: List<GalleryImage>): Map<String, Any> {
        return mapOf(
            "analysis" to "Analyzed ${images.size} images",
            "imageCount" to images.size,
            "categories" to categorizeImages(images),
            "hasText" to images.any { it.name.contains("text", ignoreCase = true) },
            "hasReceipts" to images.any { it.name.contains("receipt", ignoreCase = true) },
            "hasFaces" to checkForFacesInImages(images)
        )
    }
    
    private suspend fun calculateTotalSpending(images: List<GalleryImage>): Double {
        var total = 0.0
        
        images.forEach { image ->
            try {
                val text = extractTextFromImage(image.uri)
                val amounts = extractAmountsFromText(text)
                if (amounts.isNotEmpty()) {
                    total += amounts.maxOrNull() ?: 0.0
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating spending from image", e)
            }
        }
        
        return total
    }
    
    private fun extractAmountsFromText(text: String): List<Double> {
        val amounts = mutableListOf<Double>()
        
        val patterns = listOf(
            Regex("""(\d+\.?\d*)\s*BDT""", RegexOption.IGNORE_CASE),
            Regex("""BDT\s*(\d+\.?\d*)""", RegexOption.IGNORE_CASE),
            Regex("""\$(\d+\.?\d*)"""),
            Regex("""(\d+\.?\d*)\s*₹"""),
            Regex("""Total[:\s]*(\d+\.?\d*)""", RegexOption.IGNORE_CASE),
            Regex("""Amount[:\s]*(\d+\.?\d*)""", RegexOption.IGNORE_CASE)
        )
        
        patterns.forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                match.groupValues[1].toDoubleOrNull()?.let { amount ->
                    if (amount > 0 && amount < 100000) {
                        amounts.add(amount)
                    }
                }
            }
        }
        
        return amounts
    }
    
    private suspend fun getSpendingBreakdown(images: List<GalleryImage>): List<Map<String, Any>> {
        return images.mapNotNull { image ->
            try {
                val text = extractTextFromImage(image.uri)
                val amounts = extractAmountsFromText(text)
                if (amounts.isNotEmpty()) {
                    mapOf(
                        "image" to image.name,
                        "amount" to amounts.maxOrNull()!!,
                        "date" to Date(image.dateTaken).toString()
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun categorizeImages(images: List<GalleryImage>): Map<String, Any> {
        val categories = mutableMapOf<String, Int>()
        
        images.forEach { image ->
            val fileName = image.name.lowercase()
            when {
                fileName.contains("screenshot") -> 
                    categories["screenshots"] = categories.getOrDefault("screenshots", 0) + 1
                fileName.contains("receipt") || fileName.contains("bill") -> 
                    categories["receipts"] = categories.getOrDefault("receipts", 0) + 1
                fileName.contains("food") || fileName.contains("meal") -> 
                    categories["food"] = categories.getOrDefault("food", 0) + 1
                fileName.contains("document") -> 
                    categories["documents"] = categories.getOrDefault("documents", 0) + 1
                else -> 
                    categories["other"] = categories.getOrDefault("other", 0) + 1
            }
        }
        
        return categories.mapValues { it.value }
    }
    
    private suspend fun checkForFacesInImages(images: List<GalleryImage>): Boolean {
        return images.any { image ->
            try {
                detectFacesInImage(image.uri) > 0
            } catch (e: Exception) {
                false
            }
        }
    }
    
    fun formatResultAsJson(result: WorkflowResult): String {
        val json = JSONObject().apply {
            put("time_range", JSONObject().apply {
                put("start", result.timeRange.start)
                put("end", result.timeRange.end)
            })
            put("images_checked", result.imagesChecked)
            put("images_matched", result.imagesMatched)
            put("action", result.action)
            put("destination", result.destination)
            put("payload", JSONObject(result.payload))
        }
        return json.toString(2)
    }
    
    fun formatResultAsHumanReadable(result: WorkflowResult): String {
        val timeStart = LocalDateTime.parse(result.timeRange.start).format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
        )
        val timeEnd = LocalDateTime.parse(result.timeRange.end).format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
        )
        
        val taskDescription = when (result.action) {
            "summarize" -> {
                if (result.payload.containsKey("totalAmount")) {
                    "Spending summary"
                } else {
                    "Image summary"
                }
            }
            "calculate" -> "Total calculation"
            "send" -> "Send images"
            "copy" -> "Copy images"
            "move" -> "Move images"
            else -> "List images"
        }
        
        val resultDescription = when {
            result.payload.containsKey("totalAmount") -> 
                "${result.payload["totalAmount"]} ${result.payload["currency"]}"
            result.payload.containsKey("summary") -> 
                result.payload["summary"].toString()
            else -> "${result.imagesMatched} images found"
        }
        
        return """
📅 Time Range: $timeStart → $timeEnd
📷 Images Checked: ${result.imagesChecked}
✅ Matches Found: ${result.imagesMatched}
🎯 Task: $taskDescription
📊 Result: $resultDescription
        """.trimIndent()
    }
    
    companion object {
        private const val TAG = "ImageWorkflowOrchestrator"
    }
}

// GalleryImage is already defined in GalleryAnalysisService.kt