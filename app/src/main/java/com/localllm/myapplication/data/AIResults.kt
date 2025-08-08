package com.localllm.myapplication.data

import android.graphics.RectF

sealed class AIResult<out T> {
    data class Success<T>(val data: T) : AIResult<T>()
    data class Error(val message: String) : AIResult<Nothing>()
    object Loading : AIResult<Nothing>()
}

data class DetectionResult(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF
)

data class ClassificationResult(
    val label: String,
    val confidence: Float
)

data class FaceDetectionResult(
    val boundingBox: RectF,
    val confidence: Float,
    val landmarks: List<FaceLandmark>? = null
)

data class FaceLandmark(
    val type: LandmarkType,
    val x: Float,
    val y: Float
)

enum class LandmarkType {
    LEFT_EYE,
    RIGHT_EYE,
    NOSE_TIP,
    MOUTH_LEFT,
    MOUTH_RIGHT
}

data class LanguageDetectionResult(
    val languageTag: String,
    val confidence: Float
)