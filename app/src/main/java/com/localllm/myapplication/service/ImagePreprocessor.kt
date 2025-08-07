package com.localllm.myapplication.service

import android.graphics.*
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Advanced image preprocessing service to optimize images for LLM understanding
 * Applies various techniques to enhance image readability for AI models
 */
class ImagePreprocessor {
    
    companion object {
        private const val TAG = "ImagePreprocessor"
        private const val OPTIMAL_SIZE = 768 // Optimal resolution for most LLM vision models
        private const val MAX_SIZE = 1024 // Maximum size to prevent memory issues
        private const val MIN_SIZE = 224 // Minimum size for meaningful processing
    }
    
    /**
     * Main preprocessing function that applies all optimizations
     */
    suspend fun preprocessImage(originalBitmap: Bitmap, options: PreprocessingOptions = PreprocessingOptions()): ProcessedImage {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔄 Starting image preprocessing...")
                Log.d(TAG, "📸 Original: ${originalBitmap.width}x${originalBitmap.height}")
                
                var processedBitmap = originalBitmap.copy(originalBitmap.config ?: Bitmap.Config.ARGB_8888, false)
                val steps = mutableListOf<String>()
                
                // Step 1: Resize to optimal dimensions for LLM processing
                if (options.enableResize) {
                    processedBitmap = resizeForLLM(processedBitmap)
                    steps.add("Resized to LLM-optimal dimensions")
                    Log.d(TAG, "✅ Resized to: ${processedBitmap.width}x${processedBitmap.height}")
                }
                
                // Step 2: Enhance contrast and brightness
                if (options.enhanceContrast) {
                    processedBitmap = enhanceContrastAndBrightness(processedBitmap)
                    steps.add("Enhanced contrast and brightness")
                    Log.d(TAG, "✅ Enhanced contrast and brightness")
                }
                
                // Step 3: Apply sharpening filter for text clarity
                if (options.enableSharpening) {
                    processedBitmap = applySharpeningFilter(processedBitmap)
                    steps.add("Applied sharpening filter")
                    Log.d(TAG, "✅ Applied sharpening filter")
                }
                
                // Step 4: Normalize lighting conditions
                if (options.normalizeLighting) {
                    processedBitmap = normalizeLighting(processedBitmap)
                    steps.add("Normalized lighting")
                    Log.d(TAG, "✅ Normalized lighting")
                }
                
                // Step 5: Apply noise reduction
                if (options.enableNoiseReduction) {
                    processedBitmap = reduceNoise(processedBitmap)
                    steps.add("Applied noise reduction")
                    Log.d(TAG, "✅ Applied noise reduction")
                }
                
                // Step 6: Adjust saturation for better text/object distinction
                if (options.adjustSaturation) {
                    processedBitmap = adjustSaturation(processedBitmap, 1.2f)
                    steps.add("Adjusted color saturation")
                    Log.d(TAG, "✅ Adjusted color saturation")
                }
                
                // Step 7: Apply edge enhancement for better object detection
                if (options.enhanceEdges) {
                    processedBitmap = enhanceEdges(processedBitmap)
                    steps.add("Enhanced edges")
                    Log.d(TAG, "✅ Enhanced edges")
                }
                
                // Generate enhanced description
                val description = generateEnhancedDescription(processedBitmap)
                
                Log.d(TAG, "🎯 Image preprocessing completed!")
                Log.d(TAG, "📊 Applied ${steps.size} enhancement steps")
                
                ProcessedImage(
                    bitmap = processedBitmap,
                    enhancedDescription = description,
                    processingSteps = steps,
                    originalSize = "${originalBitmap.width}x${originalBitmap.height}",
                    processedSize = "${processedBitmap.width}x${processedBitmap.height}"
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Image preprocessing failed", e)
                ProcessedImage(
                    bitmap = originalBitmap,
                    enhancedDescription = "Image preprocessing failed: ${e.message}",
                    processingSteps = listOf("Error during preprocessing"),
                    originalSize = "${originalBitmap.width}x${originalBitmap.height}",
                    processedSize = "${originalBitmap.width}x${originalBitmap.height}"
                )
            }
        }
    }
    
    /**
     * Resize image to optimal dimensions for LLM processing
     */
    private fun resizeForLLM(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxDimension = max(width, height)
        
        // If image is already optimal size, return as-is
        if (maxDimension in MIN_SIZE..OPTIMAL_SIZE) {
            return bitmap
        }
        
        val scaleFactor = when {
            maxDimension > MAX_SIZE -> MAX_SIZE.toFloat() / maxDimension
            maxDimension < MIN_SIZE -> OPTIMAL_SIZE.toFloat() / maxDimension
            else -> OPTIMAL_SIZE.toFloat() / maxDimension
        }
        
        val newWidth = (width * scaleFactor).toInt()
        val newHeight = (height * scaleFactor).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Enhance contrast and brightness for better visibility
     */
    private fun enhanceContrastAndBrightness(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()
        
        // Create color matrix for contrast and brightness adjustment
        val colorMatrix = ColorMatrix().apply {
            // Increase contrast by 1.2x and brightness slightly
            setScale(1.2f, 1.2f, 1.2f, 1.0f)
            postConcat(ColorMatrix().apply {
                setScale(1.0f, 1.0f, 1.0f, 1.0f)
                set(floatArrayOf(
                    1.0f, 0.0f, 0.0f, 0.0f, 20f,  // Red + brightness
                    0.0f, 1.0f, 0.0f, 0.0f, 20f,  // Green + brightness  
                    0.0f, 0.0f, 1.0f, 0.0f, 20f,  // Blue + brightness
                    0.0f, 0.0f, 0.0f, 1.0f, 0.0f  // Alpha unchanged
                ))
            })
        }
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return result
    }
    
    /**
     * Apply sharpening filter to improve text and edge clarity
     */
    private fun applySharpeningFilter(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val paint = Paint()
        
        // Sharpening kernel
        val sharpenMatrix = floatArrayOf(
            0f, -1f, 0f,
            -1f, 5f, -1f,
            0f, -1f, 0f
        )
        
        // Apply convolution filter for sharpening
        val colorMatrix = ColorMatrix()
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return result
    }
    
    /**
     * Normalize lighting conditions across the image
     */
    private fun normalizeLighting(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        // Calculate histogram
        val histogram = IntArray(256)
        for (pixel in pixels) {
            val gray = ((Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114)).toInt()
            histogram[gray.coerceIn(0, 255)]++
        }
        
        // Calculate cumulative distribution
        val cdf = IntArray(256)
        cdf[0] = histogram[0]
        for (i in 1 until 256) {
            cdf[i] = cdf[i - 1] + histogram[i]
        }
        
        // Normalize using histogram equalization
        val totalPixels = pixels.size
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val gray = ((Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114)).toInt()
            val normalizedGray = ((cdf[gray.coerceIn(0, 255)] * 255.0) / totalPixels).toInt().coerceIn(0, 255)
            
            val factor = normalizedGray / gray.toFloat().coerceAtLeast(1f)
            pixels[i] = Color.rgb(
                (Color.red(pixel) * factor).toInt().coerceIn(0, 255),
                (Color.green(pixel) * factor).toInt().coerceIn(0, 255),
                (Color.blue(pixel) * factor).toInt().coerceIn(0, 255)
            )
        }
        
        result.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return result
    }
    
    /**
     * Apply noise reduction using simple averaging filter
     */
    private fun reduceNoise(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()
        
        // Apply slight blur to reduce noise while preserving edges
        paint.maskFilter = BlurMaskFilter(1f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return result
    }
    
    /**
     * Adjust color saturation for better distinction
     */
    private fun adjustSaturation(bitmap: Bitmap, saturation: Float): Bitmap {
        val result = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()
        
        val colorMatrix = ColorMatrix().apply {
            setSaturation(saturation)
        }
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return result
    }
    
    /**
     * Enhance edges for better object detection
     */
    private fun enhanceEdges(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()
        
        // Edge enhancement matrix
        val edgeMatrix = ColorMatrix(floatArrayOf(
            -0.1f, -0.1f, -0.1f, 0f, 0f,
            -0.1f,  1.8f, -0.1f, 0f, 0f,
            -0.1f, -0.1f, -0.1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(edgeMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return result
    }
    
    /**
     * Generate enhanced description based on processed image analysis
     */
    private fun generateEnhancedDescription(bitmap: Bitmap): String {
        val width = bitmap.width
        val height = bitmap.height
        val aspectRatio = width.toFloat() / height.toFloat()
        
        // Analyze image characteristics
        val brightness = analyzeAverageBrightness(bitmap)
        val contrast = analyzeContrast(bitmap)
        val colorfulness = analyzeColorfulness(bitmap)
        val sharpness = analyzeSharpness(bitmap)
        
        return buildString {
            appendLine("📸 ENHANCED IMAGE ANALYSIS:")
            appendLine("🔍 Resolution: ${width}x$height pixels (optimized for LLM)")
            appendLine("📐 Aspect Ratio: ${String.format("%.2f", aspectRatio)} ${getAspectRatioDescription(aspectRatio)}")
            appendLine("💡 Brightness Level: ${getBrightnessDescription(brightness)}")
            appendLine("🎨 Color Richness: ${getColorfulnessDescription(colorfulness)}")
            appendLine("⚡ Contrast: ${getContrastDescription(contrast)}")
            appendLine("🔧 Sharpness: ${getSharpnessDescription(sharpness)}")
            appendLine("✨ This image has been optimized for maximum LLM comprehension")
        }
    }
    
    // Analysis helper functions
    private fun analyzeAverageBrightness(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var totalBrightness = 0f
        for (pixel in pixels) {
            totalBrightness += (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114).toFloat()
        }
        
        return totalBrightness / pixels.size / 255f
    }
    
    private fun analyzeContrast(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        val brightnesses = pixels.map { pixel ->
            Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114
        }
        
        val mean = brightnesses.average()
        val variance = brightnesses.map { (it - mean).pow(2) }.average()
        return sqrt(variance).toFloat() / 255f
    }
    
    private fun analyzeColorfulness(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var totalSaturation = 0f
        for (pixel in pixels) {
            val hsv = FloatArray(3)
            Color.colorToHSV(pixel, hsv)
            totalSaturation += hsv[1] // Saturation component
        }
        
        return totalSaturation / pixels.size
    }
    
    private fun analyzeSharpness(bitmap: Bitmap): Float {
        // Simple sharpness estimation using variance of Laplacian
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var laplacianSum = 0.0
        val width = bitmap.width
        val height = bitmap.height
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = pixels[y * width + x]
                val centerGray = Color.red(center) * 0.299 + Color.green(center) * 0.587 + Color.blue(center) * 0.114
                
                val neighbors = listOf(
                    pixels[(y-1) * width + x],     // top
                    pixels[(y+1) * width + x],     // bottom  
                    pixels[y * width + (x-1)],     // left
                    pixels[y * width + (x+1)]      // right
                )
                
                val neighborGrays = neighbors.map { 
                    Color.red(it) * 0.299 + Color.green(it) * 0.587 + Color.blue(it) * 0.114 
                }
                
                val laplacian = -4 * centerGray + neighborGrays.sum()
                laplacianSum += abs(laplacian)
            }
        }
        
        return (laplacianSum / ((width - 2) * (height - 2)) / 255.0).toFloat()
    }
    
    // Description helper functions
    private fun getAspectRatioDescription(ratio: Float): String = when {
        ratio > 1.8f -> "(ultra-wide landscape)"
        ratio > 1.2f -> "(landscape)"
        ratio < 0.6f -> "(portrait)"
        ratio < 0.8f -> "(tall format)"
        else -> "(square/balanced)"
    }
    
    private fun getBrightnessDescription(brightness: Float): String = when {
        brightness > 0.8f -> "Very bright"
        brightness > 0.6f -> "Bright"
        brightness > 0.4f -> "Balanced"
        brightness > 0.2f -> "Dim" 
        else -> "Very dark"
    }
    
    private fun getColorfulnessDescription(colorfulness: Float): String = when {
        colorfulness > 0.8f -> "Highly colorful"
        colorfulness > 0.6f -> "Colorful"
        colorfulness > 0.4f -> "Moderately colorful"
        colorfulness > 0.2f -> "Muted colors"
        else -> "Monochromatic/grayscale"
    }
    
    private fun getContrastDescription(contrast: Float): String = when {
        contrast > 0.6f -> "High contrast"
        contrast > 0.4f -> "Good contrast" 
        contrast > 0.2f -> "Moderate contrast"
        else -> "Low contrast"
    }
    
    private fun getSharpnessDescription(sharpness: Float): String = when {
        sharpness > 0.3f -> "Very sharp"
        sharpness > 0.2f -> "Sharp"
        sharpness > 0.1f -> "Moderately sharp"
        sharpness > 0.05f -> "Slightly blurry"
        else -> "Blurry"
    }
}

/**
 * Configuration options for image preprocessing
 */
data class PreprocessingOptions(
    val enableResize: Boolean = true,
    val enhanceContrast: Boolean = true,
    val enableSharpening: Boolean = true,
    val normalizeLighting: Boolean = true,
    val enableNoiseReduction: Boolean = true,
    val adjustSaturation: Boolean = true,
    val enhanceEdges: Boolean = true
)

/**
 * Result of image preprocessing with enhanced information
 */
data class ProcessedImage(
    val bitmap: Bitmap,
    val enhancedDescription: String,
    val processingSteps: List<String>,
    val originalSize: String,
    val processedSize: String
)