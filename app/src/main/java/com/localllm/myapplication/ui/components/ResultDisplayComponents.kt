package com.localllm.myapplication.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localllm.myapplication.data.*

@Composable
fun DisplayDetectionResults(result: AIResult<List<DetectionResult>>) {
    when (result) {
        is AIResult.Success -> {
            if (result.data.isEmpty()) {
                Text("No objects detected")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    result.data.forEach { detection ->
                        DetectionItem(detection)
                    }
                }
            }
        }
        is AIResult.Error -> {
            Text(
                text = result.message,
                color = MaterialTheme.colorScheme.error
            )
        }
        is AIResult.Loading -> {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun DisplayClassificationResults(result: AIResult<List<ClassificationResult>>) {
    when (result) {
        is AIResult.Success -> {
            if (result.data.isEmpty()) {
                Text("No classifications found")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    result.data.forEach { classification ->
                        ClassificationItem(classification)
                    }
                }
            }
        }
        is AIResult.Error -> {
            Text(
                text = result.message,
                color = MaterialTheme.colorScheme.error
            )
        }
        is AIResult.Loading -> {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun DisplayFaceResults(result: AIResult<List<FaceDetectionResult>>) {
    when (result) {
        is AIResult.Success -> {
            if (result.data.isEmpty()) {
                Text("No faces detected")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    result.data.forEachIndexed { index, face ->
                        FaceItem(face, index + 1)
                    }
                }
            }
        }
        is AIResult.Error -> {
            Text(
                text = result.message,
                color = MaterialTheme.colorScheme.error
            )
        }
        is AIResult.Loading -> {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun DisplayLanguageResults(result: AIResult<List<LanguageDetectionResult>>) {
    when (result) {
        is AIResult.Success -> {
            if (result.data.isEmpty()) {
                Text("No language detected")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    result.data.forEach { language ->
                        LanguageItem(language)
                    }
                }
            }
        }
        is AIResult.Error -> {
            Text(
                text = result.message,
                color = MaterialTheme.colorScheme.error
            )
        }
        is AIResult.Loading -> {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun DetectionItem(detection: DetectionResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = detection.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Bounds: (${detection.boundingBox.left.toInt()}, ${detection.boundingBox.top.toInt()}) - (${detection.boundingBox.right.toInt()}, ${detection.boundingBox.bottom.toInt()})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ConfidenceChip(detection.confidence)
    }
}

@Composable
private fun ClassificationItem(classification: ClassificationResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = classification.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        ConfidenceChip(classification.confidence)
    }
}

@Composable
private fun FaceItem(face: FaceDetectionResult, faceNumber: Int) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Face $faceNumber",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            ConfidenceChip(face.confidence)
        }
        
        Text(
            text = "Bounds: (${face.boundingBox.left.toInt()}, ${face.boundingBox.top.toInt()}) - (${face.boundingBox.right.toInt()}, ${face.boundingBox.bottom.toInt()})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        face.landmarks?.let { landmarks ->
            Text(
                text = "Landmarks: ${landmarks.size} detected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LanguageItem(language: LanguageDetectionResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = language.languageTag,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        ConfidenceChip(language.confidence)
    }
}

@Composable
private fun ConfidenceChip(confidence: Float) {
    val percentage = (confidence * 100).toInt()
    val color = when {
        percentage >= 80 -> MaterialTheme.colorScheme.primary
        percentage >= 60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "${percentage}%",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}