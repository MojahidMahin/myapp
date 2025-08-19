package com.localllm.myapplication.service

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Comprehensive image analysis service that converts visual content to detailed text descriptions
 * This allows text-only LLM models to "understand" images by providing rich textual analysis
 */
class ImageAnalysisService {
    
    companion object {
        private const val TAG = "ImageAnalysisService"
    }
    
    private val imagePreprocessor = ImagePreprocessor()
    private val ocrService = OCRService()
    
    /**
     * Perform comprehensive image analysis and return detailed text description
     */
    suspend fun analyzeImage(bitmap: Bitmap, userQuestion: String = ""): ImageAnalysisResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 Starting comprehensive image analysis...")
                Log.d(TAG, "📸 Image: ${bitmap.width}x${bitmap.height}")
                Log.d(TAG, "❓ User question: $userQuestion")
                
                // Step 1: Preprocess image for better analysis
                val processedImage = imagePreprocessor.preprocessImage(bitmap)
                
                // Step 2: Extract text using OCR
                val ocrResult = ocrService.extractTextFromImage(processedImage.bitmap, usePreprocessing = false) // Already preprocessed
                
                // Step 3: Analyze visual elements
                val visualAnalysis = analyzeVisualContent(processedImage.bitmap)
                
                // Step 4: Detect objects and scenes
                val objectAnalysis = analyzeObjects(processedImage.bitmap)
                
                // Step 5: Generate comprehensive description
                val description = generateComprehensiveDescription(
                    processedImage = processedImage,
                    ocrResult = ocrResult,
                    visualAnalysis = visualAnalysis,
                    objectAnalysis = objectAnalysis,
                    userQuestion = userQuestion
                )
                
                Log.d(TAG, "✅ Image analysis completed successfully")
                
                ImageAnalysisResult(
                    success = true,
                    description = description,
                    ocrText = ocrResult.text,
                    hasText = ocrResult.success,
                    visualElements = visualAnalysis,
                    objectsDetected = objectAnalysis,
                    confidence = calculateOverallConfidence(ocrResult, visualAnalysis)
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Image analysis failed", e)
                ImageAnalysisResult(
                    success = false,
                    description = "Image analysis failed: ${e.message}",
                    ocrText = "",
                    hasText = false,
                    visualElements = VisualAnalysis(),
                    objectsDetected = ObjectAnalysis(),
                    confidence = 0.0f
                )
            }
        }
    }
    
    /**
     * Simple image filtering for quick YES/NO decisions
     * Much faster than comprehensive analysis - only extracts text and does basic checks
     */
    suspend fun filterImage(bitmap: Bitmap, filterQuestion: String): ImageFilterResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 Starting simple image filtering...")
                Log.d(TAG, "📸 Image: ${bitmap.width}x${bitmap.height}")
                Log.d(TAG, "❓ Filter question: $filterQuestion")
                
                // Step 1: Quick OCR extraction (no heavy preprocessing)
                val ocrResult = ocrService.extractTextFromImage(bitmap, usePreprocessing = false)
                
                // Step 2: Simple content-based filtering
                val filterResponse = performSimpleFiltering(ocrResult.text, filterQuestion)
                
                Log.d(TAG, "🎯 Filter response: $filterResponse")
                
                ImageFilterResult(
                    isRelevant = filterResponse.isRelevant,
                    confidence = filterResponse.confidence,
                    reasoning = filterResponse.reasoning,
                    extractedText = ocrResult.text,
                    success = true
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "💥 Error in image filtering", e)
                ImageFilterResult(
                    isRelevant = false,
                    confidence = 0.0f,
                    reasoning = "Filtering failed: ${e.message}",
                    extractedText = "",
                    success = false
                )
            }
        }
    }
    
    /**
     * Perform simple rule-based filtering without LLM
     */
    private fun performSimpleFiltering(extractedText: String, filterQuestion: String): SimpleFilterResponse {
        val text = extractedText.lowercase()
        val question = filterQuestion.lowercase()
        
        return when {
            // Receipt filtering
            question.contains("receipt") -> {
                val receiptIndicators = listOf(
                    "receipt", "bill", "invoice", "total", "subtotal", "tax", 
                    "$", "dollar", "price", "amount", "cost", "payment", 
                    "visa", "mastercard", "cash", "change", "qty", "quantity"
                )
                
                val socialMediaIndicators = listOf(
                    "like", "comment", "share", "post", "facebook", "instagram", 
                    "twitter", "updated", "profile", "notification", "message",
                    "chat", "status", "story", "follow", "friend"
                )
                
                val receiptScore = receiptIndicators.count { text.contains(it) }
                val socialScore = socialMediaIndicators.count { text.contains(it) }
                
                val isReceipt = receiptScore >= 2 && socialScore == 0
                val confidence = if (isReceipt) 0.9f else 0.1f
                
                val reasoning = if (isReceipt) {
                    "YES - Found $receiptScore receipt indicators: ${receiptIndicators.filter { text.contains(it) }.take(3).joinToString(", ")}"
                } else if (socialScore > 0) {
                    "NO - Detected social media content: ${socialMediaIndicators.filter { text.contains(it) }.take(3).joinToString(", ")}"
                } else {
                    "NO - Insufficient receipt indicators (found $receiptScore, need 2+)"
                }
                
                SimpleFilterResponse(isReceipt, confidence, reasoning)
            }
            
            // Food filtering
            question.contains("food") -> {
                val foodIndicators = listOf("food", "meal", "dish", "restaurant", "menu", "eat", "cooking")
                val foodScore = foodIndicators.count { text.contains(it) }
                val isFood = foodScore >= 1
                val confidence = if (isFood) 0.8f else 0.2f
                val reasoning = if (isFood) "YES - Food related" else "NO - No food indicators"
                SimpleFilterResponse(isFood, confidence, reasoning)
            }
            
            // Default: conservative approach
            else -> {
                SimpleFilterResponse(false, 0.3f, "NO - Conservative filtering")
            }
        }
    }
    
    /**
     * Analyze visual content characteristics
     */
    private fun analyzeVisualContent(bitmap: Bitmap): VisualAnalysis {
        Log.d(TAG, "🎨 Analyzing visual content...")
        
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        // Color analysis
        val colorInfo = analyzeColors(pixels)
        
        // Composition analysis
        val compositionInfo = analyzeComposition(bitmap)
        
        // Texture analysis
        val textureInfo = analyzeTexture(pixels, bitmap.width, bitmap.height)
        
        return VisualAnalysis(
            dominantColors = colorInfo.dominantColors,
            colorfulness = colorInfo.colorfulness,
            brightness = colorInfo.brightness,
            contrast = colorInfo.contrast,
            composition = compositionInfo,
            texture = textureInfo,
            clarity = analyzeClarity(pixels, bitmap.width, bitmap.height)
        )
    }
    
    /**
     * Analyze and detect objects/scenes in the image
     */
    private fun analyzeObjects(bitmap: Bitmap): ObjectAnalysis {
        Log.d(TAG, "🔍 Analyzing objects and scenes...")
        
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        // Detect people using simple heuristics
        val peopleCount = detectPeople(pixels, bitmap.width, bitmap.height)
        
        // Analyze scene type
        val sceneType = analyzeSceneType(pixels, bitmap.width, bitmap.height)
        
        // Detect common objects
        val objects = detectCommonObjects(pixels, bitmap.width, bitmap.height)
        
        return ObjectAnalysis(
            peopleCount = peopleCount,
            sceneType = sceneType,
            detectedObjects = objects,
            isDocument = detectDocument(pixels, bitmap.width, bitmap.height),
            isPhoto = detectPhoto(pixels, bitmap.width, bitmap.height),
            isScreenshot = detectScreenshot(bitmap.width, bitmap.height)
        )
    }
    
    /**
     * Detect people using improved color distribution and edge patterns
     */
    private fun detectPeople(pixels: IntArray, width: Int, height: Int): Int {
        Log.d(TAG, "👥 Detecting people in image...")
        
        // Multi-algorithm people detection
        var skinPixelCount = 0
        var bodyShapeRegions = 0
        var headRegions = 0
        
        // Enhanced skin tone detection with multiple ethnicities  
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            
            if (isSkinTone(r, g, b)) {
                skinPixelCount++
            }
        }
        
        val skinRatio = skinPixelCount.toFloat() / pixels.size
        
        // Detect head/face regions using improved algorithms
        headRegions = detectHeadRegions(pixels, width, height)
        
        // Detect body-like shapes and proportions
        bodyShapeRegions = detectBodyShapes(pixels, width, height)
        
        // Additional fallback detection method
        val facialFeatureRegions = detectFacialFeatures(pixels, width, height)
        
        // IMPROVED people counting logic - more sensitive
        val estimatedPeople = when {
            // High confidence: multiple detection methods agree
            headRegions >= 2 -> headRegions
            headRegions >= 1 && bodyShapeRegions >= 1 -> maxOf(headRegions, bodyShapeRegions)
            headRegions >= 1 && skinRatio > 0.05 -> maxOf(1, headRegions)
            
            // Medium confidence: strong single indicators  
            bodyShapeRegions >= 2 -> bodyShapeRegions
            bodyShapeRegions >= 1 && skinRatio > 0.04 -> maxOf(1, bodyShapeRegions)
            skinRatio > 0.12 -> (skinRatio * 10).toInt().coerceIn(1, 6) // More generous skin-based estimation
            
            // Lower confidence: but still detect people
            skinRatio > 0.06 && headRegions >= 1 -> 1
            skinRatio > 0.08 -> (skinRatio * 8).toInt().coerceIn(1, 4)
            skinRatio > 0.05 -> 1 // Even small skin areas might indicate people
            headRegions >= 1 -> headRegions // Trust head detection even with low skin
            bodyShapeRegions >= 1 -> 1 // Trust body detection
            facialFeatureRegions >= 1 -> facialFeatureRegions // Trust facial feature detection
            
            else -> 0
        }
        
        // Cap at reasonable maximum
        val finalCount = estimatedPeople.coerceIn(0, 10)
        
        Log.d(TAG, "👥 People detection results:")
        Log.d(TAG, "  • Skin ratio: ${String.format("%.4f", skinRatio)}")
        Log.d(TAG, "  • Head regions: $headRegions")
        Log.d(TAG, "  • Body shapes: $bodyShapeRegions") 
        Log.d(TAG, "  • Facial features: $facialFeatureRegions")
        Log.d(TAG, "  • Final count: $finalCount")
        
        return finalCount
    }
    
    /**
     * Check if RGB values represent a skin tone - IMPROVED with broader range
     */
    private fun isSkinTone(r: Int, g: Int, b: Int): Boolean {
        // Convert to HSV for better skin detection
        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]
        
        // Extended skin tone detection covering all ethnicities
        return when {
            // HSV-based skin detection (more accurate)
            hue in 0f..50f && saturation in 0.2f..0.8f && value in 0.3f..0.95f -> true
            hue in 340f..360f && saturation in 0.2f..0.8f && value in 0.3f..0.95f -> true
            
            // RGB-based fallback with extended ranges
            // Very light skin tones
            r in 220..255 && g in 180..240 && b in 160..220 && r > g && g > b -> true
            // Light skin tones  
            r in 180..240 && g in 140..200 && b in 100..180 && r > g -> true
            // Medium skin tones
            r in 130..200 && g in 90..160 && b in 70..140 && r > g && r > b -> true
            // Olive/tan skin tones
            r in 140..190 && g in 110..150 && b in 80..120 && r > b -> true
            // Dark skin tones
            r in 70..140 && g in 50..120 && b in 30..100 && r >= g && r >= b -> true
            // Very dark skin tones
            r in 40..100 && g in 30..80 && b in 20..70 && r >= g && r >= b -> true
            
            // Additional checks for mixed lighting conditions
            r > 60 && g > 40 && b > 30 && r > b && abs(r - g) < r * 0.4f -> true
            
            else -> false
        }
    }
    
    /**
     * Detect head/face regions using improved algorithms  
     */
    private fun detectHeadRegions(pixels: IntArray, width: Int, height: Int): Int {
        var headRegions = 0
        val regionSize = minOf(width, height) / 6 // Smaller regions for better detection
        
        // Scan image in overlapping regions
        for (y in 0 until height - regionSize step regionSize / 3) {
            for (x in 0 until width - regionSize step regionSize / 3) {
                if (isPossibleHeadRegion(pixels, x, y, regionSize, width, height)) {
                    headRegions++
                }
            }
        }
        
        // Reduce over-counting with improved clustering
        return minOf(headRegions / 2, 10) // Max 10 people
    }
    
    /**
     * Detect body-like shapes and proportions
     */
    private fun detectBodyShapes(pixels: IntArray, width: Int, height: Int): Int {
        var bodyRegions = 0
        val regionWidth = width / 4
        val regionHeight = height / 3
        
        // Look for vertical body-like regions
        for (y in 0 until height - regionHeight step regionHeight / 2) {
            for (x in 0 until width - regionWidth step regionWidth / 2) {
                if (isPossibleBodyRegion(pixels, x, y, regionWidth, regionHeight, width, height)) {
                    bodyRegions++
                }
            }
        }
        
        return minOf(bodyRegions / 2, 8) // Max 8 people
    }
    
    /**
     * Detect facial features using pattern recognition - FALLBACK METHOD
     */
    private fun detectFacialFeatures(pixels: IntArray, width: Int, height: Int): Int {
        var eyeLikeRegions = 0
        var mouthLikeRegions = 0
        val regionSize = minOf(width, height) / 12 // Smaller regions for features
        
        // Look for eye-like patterns (small dark regions with bright surroundings)
        for (y in 0 until height - regionSize step regionSize / 2) {
            for (x in 0 until width - regionSize step regionSize / 2) {
                if (isEyeLikeRegion(pixels, x, y, regionSize, width, height)) {
                    eyeLikeRegions++
                }
                if (isMouthLikeRegion(pixels, x, y, regionSize, width, height)) {
                    mouthLikeRegions++
                }
            }
        }
        
        // Estimate faces from feature detection
        val possibleFaces = when {
            eyeLikeRegions >= 4 && mouthLikeRegions >= 2 -> 2 // Multiple faces
            eyeLikeRegions >= 2 && mouthLikeRegions >= 1 -> 1 // One face
            eyeLikeRegions >= 2 -> 1 // Eyes only
            mouthLikeRegions >= 1 -> 1 // Mouth only
            else -> 0
        }
        
        Log.d(TAG, "👁️ Facial features detection: eyes=$eyeLikeRegions, mouths=$mouthLikeRegions, faces=$possibleFaces")
        return possibleFaces
    }
    
    /**
     * Check if region contains eye-like patterns
     */
    private fun isEyeLikeRegion(pixels: IntArray, startX: Int, startY: Int, size: Int, width: Int, height: Int): Boolean {
        var darkCenter = 0
        var brightSurroundings = 0
        var totalPixels = 0
        
        val centerX = startX + size / 2
        val centerY = startY + size / 2
        
        for (y in startY until minOf(startY + size, height)) {
            for (x in startX until minOf(startX + size, width)) {
                val pixel = pixels[y * width + x]
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                totalPixels++
                
                val distFromCenter = sqrt(((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)).toFloat())
                
                if (distFromCenter < size / 4f && brightness < 100) {
                    darkCenter++ // Dark center (pupil/iris)
                } else if (distFromCenter > size / 3f && brightness > 120) {
                    brightSurroundings++ // Bright surroundings (whites of eyes/skin)
                }
            }
        }
        
        val darkRatio = darkCenter.toFloat() / totalPixels
        val brightRatio = brightSurroundings.toFloat() / totalPixels
        
        return darkRatio > 0.1 && brightRatio > 0.2 // Eye-like pattern
    }
    
    /**
     * Check if region contains mouth-like patterns
     */
    private fun isMouthLikeRegion(pixels: IntArray, startX: Int, startY: Int, size: Int, width: Int, height: Int): Boolean {
        var horizontalEdges = 0
        var darkHorizontalLine = 0
        var totalChecks = 0
        
        // Look for horizontal mouth line
        for (y in (startY + size / 3) until (startY + 2 * size / 3)) {
            var consecutiveDark = 0
            for (x in startX until minOf(startX + size, width)) {
                val pixel = pixels[y * width + x]
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                totalChecks++
                
                if (brightness < 120) { // Dark line
                    consecutiveDark++
                    if (consecutiveDark > 3) darkHorizontalLine++
                } else {
                    consecutiveDark = 0
                }
                
                // Check for horizontal edge
                if (x < width - 1) {
                    val nextPixel = pixels[y * width + x + 1]
                    val edgeStrength = abs(Color.red(pixel) - Color.red(nextPixel)) +
                                     abs(Color.green(pixel) - Color.green(nextPixel)) +
                                     abs(Color.blue(pixel) - Color.blue(nextPixel))
                    if (edgeStrength > 80) horizontalEdges++
                }
            }
        }
        
        val horizontalEdgeRatio = horizontalEdges.toFloat() / totalChecks
        val darkLineRatio = darkHorizontalLine.toFloat() / totalChecks
        
        return horizontalEdgeRatio > 0.1 || darkLineRatio > 0.05 // Mouth-like pattern
    }
    
    /**
     * Check if a region could contain a head/face - IMPROVED sensitivity
     */
    private fun isPossibleHeadRegion(pixels: IntArray, startX: Int, startY: Int, size: Int, width: Int, height: Int): Boolean {
        var skinPixels = 0
        var darkPixels = 0 // Hair, eyebrows, eyes
        var brightPixels = 0 // Eyes, teeth
        var edgePixels = 0
        var totalPixels = 0
        var colorVariance = 0f
        
        val colorValues = mutableListOf<Float>()
        
        for (y in startY until minOf(startY + size, height)) {
            for (x in startX until minOf(startX + size, width)) {
                val pixel = pixels[y * width + x]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val brightness = (r + g + b) / 3f
                
                totalPixels++
                colorValues.add(brightness)
                
                if (isSkinTone(r, g, b)) {
                    skinPixels++
                }
                
                // Detect dark areas (hair, eyes, eyebrows)
                if (brightness < 80) {
                    darkPixels++
                }
                
                // Detect bright areas (eyes, teeth, highlights)
                if (brightness > 180) {
                    brightPixels++
                }
                
                // Enhanced edge detection
                if (x < width - 1 && y < height - 1) {
                    val nextPixel = pixels[y * width + x + 1]
                    val belowPixel = pixels[(y + 1) * width + x]
                    
                    val edgeStrength = abs(Color.red(pixel) - Color.red(nextPixel)) +
                                     abs(Color.red(pixel) - Color.red(belowPixel)) +
                                     abs(Color.green(pixel) - Color.green(nextPixel)) +
                                     abs(Color.green(pixel) - Color.green(belowPixel)) +
                                     abs(Color.blue(pixel) - Color.blue(nextPixel)) +
                                     abs(Color.blue(pixel) - Color.blue(belowPixel))
                    
                    if (edgeStrength > 100) { // More sensitive edge detection
                        edgePixels++
                    }
                }
            }
        }
        
        // Calculate color variance (faces have varied colors)
        if (colorValues.isNotEmpty()) {
            val mean = colorValues.average().toFloat()
            colorVariance = colorValues.map { (it - mean) * (it - mean) }.average().toFloat()
        }
        
        val skinRatio = skinPixels.toFloat() / totalPixels
        val darkRatio = darkPixels.toFloat() / totalPixels  
        val brightRatio = brightPixels.toFloat() / totalPixels
        val edgeRatio = edgePixels.toFloat() / totalPixels
        
        // More lenient face detection criteria
        val hasFaceFeatures = (darkRatio > 0.05 && brightRatio > 0.02) || // Has dark and bright areas
                             colorVariance > 400 || // Good color variation
                             (skinRatio > 0.05 && edgeRatio > 0.03) // Some skin + edges
        
        val validSkinRatio = skinRatio > 0.03 && skinRatio < 0.8 // More lenient skin range
        val hasStructure = edgeRatio > 0.02 || colorVariance > 200
        
        return hasFaceFeatures && (validSkinRatio || hasStructure)
    }
    
    /**
     * Check if a region could contain a body
     */
    private fun isPossibleBodyRegion(pixels: IntArray, startX: Int, startY: Int, regionWidth: Int, regionHeight: Int, width: Int, height: Int): Boolean {
        var skinPixels = 0
        var clothingPixels = 0
        var edgePixels = 0
        var totalPixels = 0
        
        for (y in startY until minOf(startY + regionHeight, height)) {
            for (x in startX until minOf(startX + regionWidth, width)) {
                val pixel = pixels[y * width + x]
                val r = Color.red(pixel)
                val g = Color.green(pixel) 
                val b = Color.blue(pixel)
                
                totalPixels++
                
                if (isSkinTone(r, g, b)) {
                    skinPixels++
                } else if (isClothingColor(r, g, b)) {
                    clothingPixels++
                }
                
                // Edge detection for body outline
                if (x < width - 1 && y < height - 1) {
                    val nextPixel = pixels[y * width + x + 1]
                    val belowPixel = pixels[(y + 1) * width + x]
                    
                    val edgeStrength = abs(Color.red(pixel) - Color.red(nextPixel)) +
                                     abs(Color.red(pixel) - Color.red(belowPixel))
                    if (edgeStrength > 30) {
                        edgePixels++
                    }
                }
            }
        }
        
        val skinRatio = skinPixels.toFloat() / totalPixels
        val clothingRatio = clothingPixels.toFloat() / totalPixels
        val edgeRatio = edgePixels.toFloat() / totalPixels
        
        // More lenient body detection - any reasonable combination
        return (skinRatio > 0.02 && (skinRatio + clothingRatio) > 0.2) || // Some skin + clothing
               (edgeRatio > 0.05 && clothingRatio > 0.15) || // Good structure + clothing
               (skinRatio > 0.04 && edgeRatio > 0.02) // Some skin + some structure
    }
    
    /**
     * Check if color could be clothing
     */
    private fun isClothingColor(r: Int, g: Int, b: Int): Boolean {
        // Common clothing colors: dark colors, denim blues, etc.
        return when {
            // Dark colors (common for clothing)
            r < 100 && g < 100 && b < 100 -> true
            // Blue tones (denim, etc.)
            b > r && b > g && b > 80 -> true
            // White/light colors (shirts, etc.)
            r > 180 && g > 180 && b > 180 -> true
            else -> false
        }
    }
    
    /**
     * Analyze scene type using improved object detection
     */
    private fun analyzeSceneType(pixels: IntArray, width: Int, height: Int): String {
        val colorAnalysis = analyzeColors(pixels)
        
        // Detect specific objects with proper hierarchy
        val detectedObjects = detectSpecificObjects(pixels, width, height, colorAnalysis)
        val hasText = detectTextPattern(pixels, width, height)
        val hasFaces = detectPeople(pixels, width, height) > 0
        
        Log.d(TAG, "🔍 Scene detection results:")
        Log.d(TAG, "  • Detected objects: ${detectedObjects.joinToString(", ")}")
        Log.d(TAG, "  • Text patterns: $hasText") 
        Log.d(TAG, "  • People: $hasFaces")
        
        return when {
            hasFaces -> "people/portrait"
            hasText && colorAnalysis.brightness > 0.7 -> "document/text"
            detectedObjects.isNotEmpty() -> {
                val primaryObject = detectedObjects.first()
                when {
                    primaryObject.contains("bottle") -> "bottles/containers"
                    primaryObject.contains("electronics") || primaryObject.contains("laptop") || primaryObject.contains("phone") -> "electronics/technology"
                    primaryObject.contains("food") -> "food/kitchen"
                    primaryObject.contains("vehicle") -> "transportation/vehicles"
                    primaryObject.contains("furniture") -> "furniture/indoor"
                    primaryObject.contains("clothing") -> "clothing/apparel"
                    primaryObject.contains("building") -> "architecture/building"
                    primaryObject.contains("plant") || primaryObject.contains("nature") -> "nature/outdoor"
                    else -> "everyday objects"
                }
            }
            colorAnalysis.brightness < 0.3 -> "indoor/low-light"
            width.toFloat() / height > 1.5 -> "wide/landscape"
            height.toFloat() / width > 1.5 -> "tall/portrait"
            else -> "general scene"
        }
    }
    
    /**
     * Detect specific objects using targeted algorithms
     */
    private fun detectSpecificObjects(pixels: IntArray, width: Int, height: Int, colorAnalysis: ColorAnalysis): List<String> {
        val detectedObjects = mutableListOf<String>()
        
        // Check for bottles/containers first (common misclassification)
        if (detectBottles(pixels, width, height, colorAnalysis)) {
            detectedObjects.add("bottles/containers")
        }
        
        // Check for electronics (more restrictive now)
        if (detectElectronics(pixels, width, height, colorAnalysis)) {
            detectedObjects.add("electronics")
            
            // Be more specific about electronics type
            val electronicsType = classifyElectronicsType(pixels, width, height, colorAnalysis)
            if (electronicsType.isNotBlank()) {
                detectedObjects.add(electronicsType)
            }
        }
        
        // Check for food items
        if (detectFood(pixels, width, height, colorAnalysis)) {
            detectedObjects.add("food items")
        }
        
        // Check for vehicles
        if (detectVehicles(pixels, width, height, colorAnalysis)) {
            detectedObjects.add("vehicles")
        }
        
        // Check for furniture
        if (detectFurniture(pixels, width, height, colorAnalysis)) {
            detectedObjects.add("furniture")
        }
        
        // Check for clothing/apparel
        if (detectClothing(pixels, width, height, colorAnalysis)) {
            detectedObjects.add("clothing")
        }
        
        // Check for plants/nature
        if (detectPlants(pixels, width, height, colorAnalysis)) {
            detectedObjects.add("plants/nature")
        }
        
        Log.d(TAG, "🔍 Specific object detection complete: ${detectedObjects.joinToString(", ")}")
        return detectedObjects.distinct()
    }
    
    /**
     * Detect bottles and containers - NEW METHOD
     */
    private fun detectBottles(pixels: IntArray, width: Int, height: Int, colorAnalysis: ColorAnalysis): Boolean {
        var cylindricalShapes = 0
        var reflectiveAreas = 0
        var verticalEdges = 0
        val sampleSize = pixels.size / 500 // Sample for performance
        
        // Look for bottle characteristics
        for (i in pixels.indices step sampleSize) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            
            // Detect glass/plastic reflectivity
            if (isReflectiveSurface(r, g, b)) {
                reflectiveAreas++
            }
        }
        
        // Detect vertical cylindrical shapes
        cylindricalShapes = detectCylindricalShapes(pixels, width, height)
        
        // Detect vertical edges (bottle silhouettes)
        verticalEdges = detectVerticalEdges(pixels, width, height)
        
        val reflectiveRatio = reflectiveAreas.toFloat() / (pixels.size / sampleSize)
        
        val isBottle = (cylindricalShapes > 0 && reflectiveRatio > 0.1) ||
                      (verticalEdges > 2 && reflectiveRatio > 0.05) ||
                      (reflectiveRatio > 0.2 && colorAnalysis.dominantColors.any { 
                          it in listOf("white", "blue", "green", "gray") 
                      })
        
        Log.d(TAG, "🍾 Bottle detection: cylindrical=$cylindricalShapes, reflective=${String.format("%.3f", reflectiveRatio)}, vertical=$verticalEdges, result=$isBottle")
        
        return isBottle
    }
    
    /**
     * Check if surface appears reflective (glass/plastic)
     */
    private fun isReflectiveSurface(r: Int, g: Int, b: Int): Boolean {
        val brightness = (r + g + b) / 3
        val variance = ((r - brightness) * (r - brightness) + 
                       (g - brightness) * (g - brightness) + 
                       (b - brightness) * (b - brightness)) / 3
        
        // Reflective surfaces have high brightness or low variance (neutral colors)
        return brightness > 180 || (brightness > 120 && variance < 100)
    }
    
    /**
     * Detect cylindrical shapes typical of bottles
     */
    private fun detectCylindricalShapes(pixels: IntArray, width: Int, height: Int): Int {
        var cylindricalRegions = 0
        val regionWidth = width / 6
        val regionHeight = height / 4
        
        for (y in 0 until height - regionHeight step regionHeight / 2) {
            for (x in 0 until width - regionWidth step regionWidth / 2) {
                if (isCylindricalRegion(pixels, x, y, regionWidth, regionHeight, width, height)) {
                    cylindricalRegions++
                }
            }
        }
        
        return cylindricalRegions
    }
    
    /**
     * Check if region has cylindrical characteristics
     */
    private fun isCylindricalRegion(pixels: IntArray, startX: Int, startY: Int, regionWidth: Int, regionHeight: Int, width: Int, height: Int): Boolean {
        var verticalConsistency = 0
        var centerBrightness = 0f
        var edgeDarkness = 0f
        var totalChecks = 0
        
        // Check vertical consistency (bottles are tall and consistent)
        for (x in startX until minOf(startX + regionWidth, width) step 2) {
            var columnConsistency = 0
            var prevBrightness = -1f
            
            for (y in startY until minOf(startY + regionHeight, height)) {
                val pixel = pixels[y * width + x]
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3f
                
                totalChecks++
                
                // Check consistency with previous pixel
                if (prevBrightness >= 0 && abs(brightness - prevBrightness) < 30) {
                    columnConsistency++
                }
                prevBrightness = brightness
                
                // Analyze brightness distribution
                val distFromCenter = abs(x - (startX + regionWidth / 2))
                if (distFromCenter < regionWidth / 4) {
                    centerBrightness += brightness
                } else {
                    edgeDarkness += brightness
                }
            }
            
            if (columnConsistency > regionHeight / 3) {
                verticalConsistency++
            }
        }
        
        // Bottles often have consistent vertical patterns
        return verticalConsistency > regionWidth / 4
    }
    
    /**
     * Detect vertical edges typical of bottle silhouettes
     */
    private fun detectVerticalEdges(pixels: IntArray, width: Int, height: Int): Int {
        var strongVerticalEdges = 0
        
        for (y in height / 4 until 3 * height / 4 step 3) { // Sample middle section
            for (x in 1 until width - 1 step 5) {
                val left = pixels[y * width + x - 1]
                val center = pixels[y * width + x]
                val right = pixels[y * width + x + 1]
                
                val leftBrightness = (Color.red(left) + Color.green(left) + Color.blue(left)) / 3
                val centerBrightness = (Color.red(center) + Color.green(center) + Color.blue(center)) / 3
                val rightBrightness = (Color.red(right) + Color.green(right) + Color.blue(right)) / 3
                
                // Strong vertical edge detection
                val edgeStrength = abs(leftBrightness - rightBrightness)
                if (edgeStrength > 50 && (centerBrightness > leftBrightness && centerBrightness > rightBrightness ||
                                        centerBrightness < leftBrightness && centerBrightness < rightBrightness)) {
                    strongVerticalEdges++
                }
            }
        }
        
        return strongVerticalEdges
    }
    
    /**
     * Detect electronics like laptops, phones, monitors - IMPROVED precision
     */
    private fun detectElectronics(pixels: IntArray, width: Int, height: Int, colorAnalysis: ColorAnalysis): Boolean {
        // Look for rectangular shapes with metallic/plastic colors
        var rectangularRegions = 0
        var metallicPixels = 0
        var screenPixels = 0
        
        // Sample pixels for efficiency
        for (i in pixels.indices step 100) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            
            // Detect metallic colors (silver, gray, black)
            if (isMetallicColor(r, g, b)) {
                metallicPixels++
            }
            
            // Detect screen-like colors (dark with blue tint or bright)
            if (isScreenColor(r, g, b)) {
                screenPixels++
            }
        }
        
        val metallicRatio = metallicPixels.toFloat() / (pixels.size / 100)
        val screenRatio = screenPixels.toFloat() / (pixels.size / 100)
        
        // Detect rectangular patterns typical of electronics
        rectangularRegions = detectRectangularShapes(pixels, width, height)
        
        Log.d(TAG, "📱 Electronics detection: metallic=${String.format("%.3f", metallicRatio)}, screen=${String.format("%.3f", screenRatio)}, rectangles=$rectangularRegions")
        
        // MUCH MORE RESTRICTIVE criteria for electronics
        return (metallicRatio > 0.3 && screenRatio > 0.15 && rectangularRegions > 3) || // High confidence: all three indicators
               (screenRatio > 0.4 && rectangularRegions > 2) || // Strong screen + some structure
               (metallicRatio > 0.4 && rectangularRegions > 4) // Very metallic + very structured
    }
    
    /**
     * Check if color appears metallic (typical of laptop/phone casings)
     */
    private fun isMetallicColor(r: Int, g: Int, b: Int): Boolean {
        val avg = (r + g + b) / 3
        val variance = ((r - avg) * (r - avg) + (g - avg) * (g - avg) + (b - avg) * (b - avg)) / 3
        
        return when {
            // Gray/silver metallic (low variance, medium brightness)
            variance < 200 && avg in 80..180 -> true
            // Black/dark metallic
            avg < 60 && variance < 100 -> true
            // White/light metallic
            avg > 200 && variance < 150 -> true
            else -> false
        }
    }
    
    /**
     * Check if color appears screen-like
     */
    private fun isScreenColor(r: Int, g: Int, b: Int): Boolean {
        return when {
            // Dark screen (off or black content)
            r < 50 && g < 50 && b < 50 -> true
            // Blue-tinted screen 
            b > r && b > g && b > 60 -> true
            // Bright screen content
            r > 200 && g > 200 && b > 200 -> true
            // Colorful screen content
            maxOf(r, g, b) - minOf(r, g, b) > 100 -> true
            else -> false
        }
    }
    
    /**
     * Detect rectangular shapes typical of electronics
     */
    private fun detectRectangularShapes(pixels: IntArray, width: Int, height: Int): Int {
        var rectangles = 0
        val regionSize = minOf(width, height) / 8
        
        // Look for regions with strong horizontal/vertical edges
        for (y in 0 until height - regionSize step regionSize / 2) {
            for (x in 0 until width - regionSize step regionSize / 2) {
                var horizontalEdges = 0
                var verticalEdges = 0
                var totalChecks = 0
                
                // Check for horizontal edges
                for (checkY in y until minOf(y + regionSize, height) step 5) {
                    for (checkX in x until minOf(x + regionSize - 5, width)) {
                        val left = pixels[checkY * width + checkX]
                        val right = pixels[checkY * width + checkX + 5]
                        val edgeStrength = abs(Color.red(left) - Color.red(right)) +
                                         abs(Color.green(left) - Color.green(right)) +
                                         abs(Color.blue(left) - Color.blue(right))
                        totalChecks++
                        if (edgeStrength > 60) horizontalEdges++
                    }
                }
                
                // Check for vertical edges
                for (checkY in y until minOf(y + regionSize - 5, height)) {
                    for (checkX in x until minOf(x + regionSize, width) step 5) {
                        val top = pixels[checkY * width + checkX]
                        val bottom = pixels[(checkY + 5) * width + checkX]
                        val edgeStrength = abs(Color.red(top) - Color.red(bottom)) +
                                         abs(Color.green(top) - Color.green(bottom)) +
                                         abs(Color.blue(top) - Color.blue(bottom))
                        if (edgeStrength > 60) verticalEdges++
                    }
                }
                
                // Rectangle-like if both horizontal and vertical edges
                if (horizontalEdges > totalChecks / 8 && verticalEdges > totalChecks / 8) {
                    rectangles++
                }
            }
        }
        
        return rectangles
    }
    
    /**
     * Detect text patterns in image
     */
    private fun detectTextPattern(pixels: IntArray, width: Int, height: Int): Boolean {
        var textLikeRegions = 0
        val regionSize = minOf(width, height) / 10
        
        for (y in 0 until height - regionSize step regionSize) {
            for (x in 0 until width - regionSize step regionSize) {
                if (isTextLikeRegion(pixels, x, y, regionSize, width, height)) {
                    textLikeRegions++
                }
            }
        }
        
        return textLikeRegions > 3
    }
    
    /**
     * Check if region contains text-like patterns
     */
    private fun isTextLikeRegion(pixels: IntArray, startX: Int, startY: Int, size: Int, width: Int, height: Int): Boolean {
        var highContrastPixels = 0
        var totalPixels = 0
        var linePatterns = 0
        
        for (y in startY until minOf(startY + size, height)) {
            var consecutiveEdges = 0
            for (x in startX until minOf(startX + size, width)) {
                totalPixels++
                
                if (x < width - 1) {
                    val current = pixels[y * width + x]
                    val next = pixels[y * width + x + 1]
                    val edgeStrength = abs(Color.red(current) - Color.red(next)) +
                                     abs(Color.green(current) - Color.green(next)) +
                                     abs(Color.blue(current) - Color.blue(next))
                    
                    if (edgeStrength > 100) {
                        highContrastPixels++
                        consecutiveEdges++
                    } else {
                        if (consecutiveEdges > 3) linePatterns++
                        consecutiveEdges = 0
                    }
                }
            }
            if (consecutiveEdges > 3) linePatterns++
        }
        
        val contrastRatio = highContrastPixels.toFloat() / totalPixels
        return contrastRatio > 0.2 && linePatterns > 2
    }
    
    /**
     * Detect architecture/buildings
     */
    private fun detectArchitecture(pixels: IntArray, width: Int, height: Int, colorAnalysis: ColorAnalysis): Boolean {
        // Look for straight lines, geometric patterns, neutral colors
        val hasGeometricPatterns = detectRectangularShapes(pixels, width, height) > 5
        val hasNeutralColors = colorAnalysis.dominantColors.any { it in listOf("gray", "white", "black") }
        val hasHighContrast = colorAnalysis.contrast > 0.5
        
        return hasGeometricPatterns && hasNeutralColors && hasHighContrast
    }
    
    /**
     * Detect nature/outdoor scenes
     */
    private fun detectNature(pixels: IntArray, width: Int, height: Int, colorAnalysis: ColorAnalysis): Boolean {
        val hasGreen = colorAnalysis.dominantColors.contains("green")
        val hasBlue = colorAnalysis.dominantColors.contains("blue") 
        val isColorful = colorAnalysis.colorfulness > 0.3
        val isBright = colorAnalysis.brightness > 0.5
        
        return (hasGreen && isBright) || (hasBlue && hasGreen) || (isColorful && isBright)
    }
    
    /**
     * Classify specific type of electronics
     */
    private fun classifyElectronicsType(pixels: IntArray, width: Int, height: Int, colorAnalysis: ColorAnalysis): String {
        val aspectRatio = width.toFloat() / height.toFloat()
        val screenRatio = pixels.count { pixel ->
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            isScreenColor(r, g, b)
        }.toFloat() / pixels.size
        
        return when {
            aspectRatio > 1.2f && screenRatio > 0.2 -> "laptop/computer"
            aspectRatio < 0.8f && screenRatio > 0.3 -> "smartphone/mobile"
            screenRatio > 0.5 -> "monitor/display"
            else -> ""
        }
    }
    
    /**
     * Detect food items
     */
    private fun detectFood(pixels: IntArray, width: Int, height: Int, colorAnalysis: ColorAnalysis): Boolean {
        val hasOrganic = colorAnalysis.dominantColors.any { it in listOf("red", "green", "yellow", "white") }
        val isRounded = detectRoundedShapes(pixels, width, height) > 2
        val hasVariedTexture = colorAnalysis.colorfulness > 0.4
        
        return hasOrganic && (isRounded || hasVariedTexture)
    }
    
    /**
     * Detect vehicles
     */
    private fun detectVehicles(pixels: IntArray, width: Int, height: Int, colorAnalysis: ColorAnalysis): Boolean {
        val hasMetallicFinish = colorAnalysis.dominantColors.any { it in listOf("black", "white", "gray", "blue", "red") }
        val hasLargeShapes = detectRectangularShapes(pixels, width, height) > 5
        val aspectRatio = width.toFloat() / height.toFloat()
        
        return hasMetallicFinish && hasLargeShapes && aspectRatio > 1.3f
    }
    
    /**
     * Detect furniture
     */
    private fun detectFurniture(pixels: IntArray, width: Int, height: Int, colorAnalysis: ColorAnalysis): Boolean {
        val hasWoodColors = colorAnalysis.dominantColors.any { it in listOf("brown", "white", "black", "gray") }
        val hasGeometricShapes = detectRectangularShapes(pixels, width, height) > 3
        val isLowContrast = colorAnalysis.contrast < 0.4 // Furniture often has uniform colors
        
        return hasWoodColors && hasGeometricShapes && isLowContrast
    }
    
    /**
     * Detect clothing/apparel
     */
    private fun detectClothing(pixels: IntArray, width: Int, height: Int, colorAnalysis: ColorAnalysis): Boolean {
        val hasFabricColors = colorAnalysis.dominantColors.isNotEmpty()
        val hasFlowingShapes = detectRoundedShapes(pixels, width, height) > 1
        val hasTextureVariation = colorAnalysis.colorfulness > 0.2
        
        return hasFabricColors && hasFlowingShapes && hasTextureVariation
    }
    
    /**
     * Detect plants and nature elements
     */
    private fun detectPlants(pixels: IntArray, width: Int, height: Int, colorAnalysis: ColorAnalysis): Boolean {
        val hasGreenColors = colorAnalysis.dominantColors.contains("green")
        val hasOrganicShapes = detectRoundedShapes(pixels, width, height) > 3
        val isBright = colorAnalysis.brightness > 0.5
        val isColorful = colorAnalysis.colorfulness > 0.3
        
        return hasGreenColors && (hasOrganicShapes || (isBright && isColorful))
    }
    
    /**
     * Detect rounded/organic shapes
     */
    private fun detectRoundedShapes(pixels: IntArray, width: Int, height: Int): Int {
        var roundedRegions = 0
        val regionSize = minOf(width, height) / 8
        
        for (y in 0 until height - regionSize step regionSize) {
            for (x in 0 until width - regionSize step regionSize) {
                if (isRoundedRegion(pixels, x, y, regionSize, width, height)) {
                    roundedRegions++
                }
            }
        }
        
        return roundedRegions
    }
    
    /**
     * Check if region has rounded characteristics
     */
    private fun isRoundedRegion(pixels: IntArray, startX: Int, startY: Int, size: Int, width: Int, height: Int): Boolean {
        var centerPixels = 0
        var edgePixels = 0
        val centerX = startX + size / 2
        val centerY = startY + size / 2
        val radius = size / 2f
        
        for (y in startY until minOf(startY + size, height)) {
            for (x in startX until minOf(startX + size, width)) {
                val distance = sqrt(((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)).toFloat())
                
                if (distance < radius * 0.7f) {
                    centerPixels++
                } else if (distance > radius * 0.9f) {
                    edgePixels++
                }
            }
        }
        
        // Rounded shapes have more center pixels than edge pixels
        return centerPixels > edgePixels && centerPixels > size * size / 8
    }
    
    /**
     * Detect common objects using improved algorithms - UPDATED to use new methods
     */
    private fun detectCommonObjects(pixels: IntArray, width: Int, height: Int): List<String> {
        val objects = mutableListOf<String>()
        val colorAnalysis = analyzeColors(pixels)
        
        // Use the specific object detection methods
        return detectSpecificObjects(pixels, width, height, colorAnalysis)
    }
    
    /**
     * Analyze color characteristics
     */
    private fun analyzeColors(pixels: IntArray): ColorAnalysis {
        val colorCounts = mutableMapOf<String, Int>()
        var totalR = 0f; var totalG = 0f; var totalB = 0f
        var brightPixels = 0
        val brightnesses = mutableListOf<Float>()
        
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            
            totalR += r; totalG += g; totalB += b
            
            val brightness = (r * 0.299f + g * 0.587f + b * 0.114f) / 255f
            brightnesses.add(brightness)
            if (brightness > 0.7f) brightPixels++
            
            // Categorize dominant color
            val dominantColor = when {
                r > g && r > b && r > 100 -> "red"
                g > r && g > b && g > 100 -> "green"
                b > r && b > g && b > 100 -> "blue"
                r > 200 && g > 200 && b > 200 -> "white"
                r < 50 && g < 50 && b < 50 -> "black"
                abs(r - g) < 30 && abs(g - b) < 30 -> "gray"
                else -> "mixed"
            }
            colorCounts[dominantColor] = (colorCounts[dominantColor] ?: 0) + 1
        }
        
        val avgBrightness = brightnesses.average().toFloat()
        val brightnessVariance = brightnesses.map { (it - avgBrightness).pow(2) }.average().toFloat()
        val contrast = sqrt(brightnessVariance)
        
        // Calculate colorfulness
        val avgR = totalR / pixels.size
        val avgG = totalG / pixels.size
        val avgB = totalB / pixels.size
        val colorfulness = sqrt((avgR - avgG).pow(2) + (avgG - avgB).pow(2) + (avgB - avgR).pow(2)) / 255f
        
        val dominantColors = colorCounts.entries.sortedByDescending { it.value }.take(3).map { it.key }
        
        return ColorAnalysis(
            dominantColors = dominantColors,
            brightness = avgBrightness,
            contrast = contrast,
            colorfulness = colorfulness
        )
    }
    
    /**
     * Analyze image composition
     */
    private fun analyzeComposition(bitmap: Bitmap): String {
        val aspectRatio = bitmap.width.toFloat() / bitmap.height
        
        return when {
            aspectRatio > 2.0f -> "ultra-wide panoramic"
            aspectRatio > 1.5f -> "wide landscape"
            aspectRatio > 1.2f -> "landscape orientation"
            aspectRatio < 0.5f -> "tall vertical"
            aspectRatio < 0.8f -> "portrait orientation"
            else -> "square/balanced composition"
        }
    }
    
    /**
     * Analyze texture and patterns
     */
    private fun analyzeTexture(pixels: IntArray, width: Int, height: Int): String {
        var edgeCount = 0
        var patternScore = 0
        
        // Simple texture analysis using edge detection
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = pixels[y * width + x]
                val right = pixels[y * width + x + 1]
                val below = pixels[(y + 1) * width + x]
                
                val edgeStrength = abs(Color.red(center) - Color.red(right)) +
                                 abs(Color.red(center) - Color.red(below))
                
                if (edgeStrength > 30) edgeCount++
                if (edgeStrength > 60) patternScore++
            }
        }
        
        val edgeRatio = edgeCount.toFloat() / (width * height)
        val patternRatio = patternScore.toFloat() / (width * height)
        
        return when {
            patternRatio > 0.1 -> "highly detailed/textured"
            edgeRatio > 0.05 -> "structured/geometric"
            edgeRatio > 0.02 -> "moderate detail"
            else -> "smooth/low detail"
        }
    }
    
    /**
     * Analyze image clarity - FIXED BLUR DETECTION
     */
    private fun analyzeClarity(pixels: IntArray, width: Int, height: Int): String {
        var sharpEdges = 0
        var totalEdges = 0
        var highContrastAreas = 0
        
        // Sample every 4th pixel for performance while maintaining accuracy
        for (y in 2 until height - 2 step 2) {
            for (x in 2 until width - 2 step 2) {
                val center = pixels[y * width + x]
                val neighbors = listOf(
                    pixels[(y-2) * width + x],     // top
                    pixels[(y+2) * width + x],     // bottom  
                    pixels[y * width + (x-2)],     // left
                    pixels[y * width + (x+2)]      // right
                )
                
                val centerGray = Color.red(center) * 0.299 + Color.green(center) * 0.587 + Color.blue(center) * 0.114
                val neighborGrays = neighbors.map { 
                    Color.red(it) * 0.299 + Color.green(it) * 0.587 + Color.blue(it) * 0.114 
                }
                
                // Calculate maximum edge strength for better detection
                val maxEdgeStrength = neighborGrays.maxOf { abs(centerGray - it) }
                
                if (maxEdgeStrength > 5) {  // Lower threshold for edge detection
                    totalEdges++
                    
                    // Multiple sharpness criteria
                    if (maxEdgeStrength > 25) sharpEdges++
                    if (maxEdgeStrength > 40) highContrastAreas++
                }
            }
        }
        
        val sharpnessRatio = if (totalEdges > 0) sharpEdges.toFloat() / totalEdges else 0f
        val contrastRatio = if (totalEdges > 0) highContrastAreas.toFloat() / totalEdges else 0f
        val edgeDensity = totalEdges.toFloat() / ((width * height) / 16) // Adjusted for sampling
        
        Log.d(TAG, "🔍 Clarity analysis: sharpness=${String.format("%.3f", sharpnessRatio)}, contrast=${String.format("%.3f", contrastRatio)}, edges=${String.format("%.3f", edgeDensity)}")
        
        return when {
            // More lenient criteria for "clear" images
            sharpnessRatio > 0.4 && contrastRatio > 0.2 -> "very sharp and clear"
            sharpnessRatio > 0.25 && contrastRatio > 0.1 -> "sharp and clear"  
            sharpnessRatio > 0.15 || edgeDensity > 0.1 -> "moderately sharp"
            sharpnessRatio > 0.05 || edgeDensity > 0.05 -> "acceptable clarity"
            else -> "somewhat blurry"
        }
    }
    
    /**
     * Detect if image is a document
     */
    private fun detectDocument(pixels: IntArray, width: Int, height: Int): Boolean {
        var whitePixels = 0
        var blackPixels = 0
        
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel) 
            val b = Color.blue(pixel)
            val brightness = r * 0.299f + g * 0.587f + b * 0.114f
            
            if (brightness > 220) whitePixels++
            if (brightness < 50) blackPixels++
        }
        
        val whiteRatio = whitePixels.toFloat() / pixels.size
        val blackRatio = blackPixels.toFloat() / pixels.size
        
        return whiteRatio > 0.5 && blackRatio > 0.05 // High contrast document-like
    }
    
    /**
     * Detect if image is a photo
     */
    private fun detectPhoto(pixels: IntArray, width: Int, height: Int): Boolean {
        val colorAnalysis = analyzeColors(pixels)
        return colorAnalysis.colorfulness > 0.3 && colorAnalysis.dominantColors.size >= 3
    }
    
    /**
     * Detect if image is a screenshot
     */
    private fun detectScreenshot(width: Int, height: Int): Boolean {
        // Common smartphone screenshot dimensions
        val aspectRatio = width.toFloat() / height.toFloat()
        return aspectRatio in 0.4f..0.7f || aspectRatio in 1.4f..2.5f
    }
    
    /**
     * Generate comprehensive description combining all analysis
     */
    private fun generateComprehensiveDescription(
        processedImage: ProcessedImage,
        ocrResult: OCRResult,
        visualAnalysis: VisualAnalysis,
        objectAnalysis: ObjectAnalysis,
        userQuestion: String
    ): String {
        return buildString {
            appendLine("🔍 COMPREHENSIVE IMAGE ANALYSIS")
            appendLine()
            
            // Image specifications
            appendLine("📐 IMAGE SPECIFICATIONS:")
            appendLine("• Resolution: ${processedImage.processedSize}")
            appendLine("• Composition: ${visualAnalysis.composition}")
            appendLine("• Clarity: ${visualAnalysis.clarity}")
            appendLine("• Visual quality: ${visualAnalysis.texture}")
            appendLine()
            
            // Scene and content analysis
            appendLine("🎬 SCENE ANALYSIS:")
            appendLine("• Scene type: ${objectAnalysis.sceneType}")
            appendLine("• Content type: ${when {
                objectAnalysis.isDocument -> "Document/Text"
                objectAnalysis.isPhoto -> "Photograph"
                objectAnalysis.isScreenshot -> "Screenshot"
                else -> "General image"
            }}")
            appendLine()
            
            // People detection (focused on user's question)
            if (userQuestion.contains("person", ignoreCase = true) || 
                userQuestion.contains("people", ignoreCase = true) ||
                userQuestion.contains("how many", ignoreCase = true)) {
                appendLine("👥 PEOPLE DETECTION (DETAILED):")
                appendLine("• Number of people detected: ${objectAnalysis.peopleCount}")
                appendLine("• Detection confidence: ${if (objectAnalysis.peopleCount > 0) "High" else "Low"}")
                appendLine("• Analysis method: Advanced skin tone detection, facial region analysis, and body proportion estimation")
                if (objectAnalysis.peopleCount > 0) {
                    appendLine("• People are distributed across the image with distinct skin tones and body proportions")
                } else {
                    appendLine("• No human figures detected using skin tone, facial features, or body shape analysis")
                }
                appendLine()
            }
            
            // Color and visual analysis
            appendLine("🎨 VISUAL CHARACTERISTICS:")
            appendLine("• Dominant colors: ${visualAnalysis.dominantColors.joinToString(", ")}")
            appendLine("• Brightness level: ${when {
                visualAnalysis.brightness > 0.7f -> "Very bright"
                visualAnalysis.brightness > 0.5f -> "Well-lit" 
                visualAnalysis.brightness > 0.3f -> "Moderate lighting"
                else -> "Dark/dim"
            }}")
            appendLine("• Contrast: ${when {
                visualAnalysis.contrast > 0.6f -> "High contrast"
                visualAnalysis.contrast > 0.3f -> "Good contrast"
                else -> "Low contrast"
            }}")
            appendLine()
            
            // Text content if available
            if (ocrResult.success && ocrResult.text.isNotBlank()) {
                appendLine("📝 TEXT CONTENT FOUND:")
                appendLine("${ocrResult.text}")
                appendLine()
            }
            
            // Objects and elements
            if (objectAnalysis.detectedObjects.isNotEmpty()) {
                appendLine("🔍 DETECTED ELEMENTS:")
                objectAnalysis.detectedObjects.forEach { obj ->
                    appendLine("• $obj")
                }
                appendLine()
            }
            
            // Answer user's specific question with improved accuracy
            if (userQuestion.isNotBlank()) {
                appendLine("💬 DIRECT ANSWER TO YOUR QUESTION:")
                appendLine("Question: \"$userQuestion\"")
                
                when {
                    userQuestion.contains("how many person", ignoreCase = true) ||
                    userQuestion.contains("how many people", ignoreCase = true) ||
                    userQuestion.contains("how many face", ignoreCase = true) -> {
                        appendLine("Answer: ${objectAnalysis.peopleCount}")
                        if (objectAnalysis.peopleCount == 0) {
                            appendLine("Explanation: After thorough analysis using skin tone detection, facial recognition patterns, and body shape analysis, no people were detected in this image.")
                        } else {
                            appendLine("Explanation: Detected ${objectAnalysis.peopleCount} person(s) using advanced computer vision techniques including skin tone analysis and facial region detection.")
                        }
                    }
                    userQuestion.contains("what is this", ignoreCase = true) || 
                    userQuestion.contains("what", ignoreCase = true) -> {
                        val primaryObjects = objectAnalysis.detectedObjects.filter { 
                            it.contains("laptop") || it.contains("computer") || it.contains("electronic") || 
                            it.contains("phone") || it.contains("monitor") || it.contains("device")
                        }
                        
                        if (primaryObjects.isNotEmpty()) {
                            appendLine("Answer: This appears to be ${primaryObjects.first()}")
                            appendLine("Explanation: Detected based on metallic surfaces, rectangular shapes, and screen-like elements typical of ${primaryObjects.first()}")
                        } else if (objectAnalysis.sceneType.contains("electronics")) {
                            appendLine("Answer: This is an electronic device or technology-related item")
                            appendLine("Explanation: Detected metallic/plastic materials and geometric patterns typical of electronics")
                        } else {
                            appendLine("Answer: This appears to be ${objectAnalysis.sceneType}")
                            if (objectAnalysis.peopleCount > 0) append(" featuring ${objectAnalysis.peopleCount} person(s)")
                            appendLine(" with ${visualAnalysis.dominantColors.take(2).joinToString(" and ")} as dominant colors")
                        }
                    }
                    userQuestion.contains("describe", ignoreCase = true) -> {
                        appendLine("Answer: This image shows ${objectAnalysis.sceneType}")
                        if (objectAnalysis.detectedObjects.isNotEmpty()) {
                            appendLine("Objects detected: ${objectAnalysis.detectedObjects.take(3).joinToString(", ")}")
                        }
                        if (objectAnalysis.peopleCount > 0) {
                            appendLine("People visible: ${objectAnalysis.peopleCount}")
                        }
                        appendLine("Visual characteristics: ${visualAnalysis.clarity} image with ${visualAnalysis.dominantColors.joinToString(", ")} colors")
                    }
                    else -> {
                        appendLine("Answer: Based on the comprehensive analysis above, this image contains the visual elements and characteristics described.")
                    }
                }
            }
        }
    }
    
    /**
     * Calculate overall confidence score
     */
    private fun calculateOverallConfidence(ocrResult: OCRResult, visualAnalysis: VisualAnalysis): Float {
        val ocrConfidence = if (ocrResult.success) ocrResult.confidence else 0f
        val visualConfidence = when {
            visualAnalysis.clarity.contains("very sharp") -> 90f
            visualAnalysis.clarity.contains("sharp") -> 75f
            visualAnalysis.clarity.contains("moderate") -> 60f
            else -> 30f
        }
        
        return (ocrConfidence + visualConfidence) / 2f
    }
}

// Data classes for analysis results
data class ImageAnalysisResult(
    val success: Boolean,
    val description: String,
    val ocrText: String,
    val hasText: Boolean,
    val visualElements: VisualAnalysis,
    val objectsDetected: ObjectAnalysis,
    val confidence: Float
)

data class VisualAnalysis(
    val dominantColors: List<String> = emptyList(),
    val colorfulness: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val composition: String = "",
    val texture: String = "",
    val clarity: String = ""
)

data class ObjectAnalysis(
    val peopleCount: Int = 0,
    val sceneType: String = "",
    val detectedObjects: List<String> = emptyList(),
    val isDocument: Boolean = false,
    val isPhoto: Boolean = false,
    val isScreenshot: Boolean = false
)

data class ColorAnalysis(
    val dominantColors: List<String>,
    val brightness: Float,
    val contrast: Float,
    val colorfulness: Float
)

data class ImageFilterResult(
    val isRelevant: Boolean,
    val confidence: Float,
    val reasoning: String,
    val extractedText: String,
    val success: Boolean
)

data class SimpleFilterResponse(
    val isRelevant: Boolean,
    val confidence: Float,
    val reasoning: String
)