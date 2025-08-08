package com.localllm.myapplication.service.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult
import com.localllm.myapplication.config.ConfigLoader
import com.localllm.myapplication.data.AIResult
import com.localllm.myapplication.data.FaceDetectionResult
import com.localllm.myapplication.data.FaceLandmark
import com.localllm.myapplication.data.LandmarkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FaceDetectionService(
    private val context: Context,
    private val configLoader: ConfigLoader
) {
    private var faceDetector: FaceDetector? = null
    
    private fun initializeDetector(): FaceDetector {
        return faceDetector ?: run {
            val options = FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(
                    com.google.mediapipe.tasks.core.BaseOptions.builder()
                        .setModelAssetPath("face_detection_short_range.tflite")
                        .build()
                )
                .setMinDetectionConfidence(0.5f)
                .build()
            
            val detector = FaceDetector.createFromOptions(context, options)
            faceDetector = detector
            detector
        }
    }
    
    suspend fun detectFaces(bitmap: Bitmap): AIResult<List<FaceDetectionResult>> = withContext(Dispatchers.IO) {
        try {
            val detector = initializeDetector()
            
            // Convert Android Bitmap to MediaPipe MPImage
            val mpImage = BitmapImageBuilder(bitmap).build()
            
            // Run inference
            val result: FaceDetectorResult = detector.detect(mpImage)
            
            // Convert MediaPipe results to app format
            val detectionResults = result.detections().map { detection ->
                val boundingBox = detection.boundingBox()
                val rectF = RectF(
                    boundingBox.left,
                    boundingBox.top,
                    boundingBox.right,
                    boundingBox.bottom
                )
                
                // TODO: Extract landmarks when MediaPipe API is better understood
                val landmarks = emptyList<FaceLandmark>()
                
                FaceDetectionResult(
                    boundingBox = rectF,
                    confidence = detection.categories().firstOrNull()?.score() ?: 0f,
                    landmarks = landmarks
                )
            }
            
            AIResult.Success(detectionResults)
        } catch (e: Exception) {
            AIResult.Error("Face detection failed: ${e.message}")
        }
    }
    
    fun cleanup() {
        faceDetector?.close()
        faceDetector = null
    }
}