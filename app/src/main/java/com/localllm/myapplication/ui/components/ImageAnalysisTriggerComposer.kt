package com.localllm.myapplication.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localllm.myapplication.data.*
import java.io.File

/**
 * Composable for creating image analysis triggers with optional image attachments
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageAnalysisTriggerComposer(
    onTriggerCreated: (MultiUserTrigger.ImageAnalysisTrigger) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Form state
    var triggerName by remember { mutableStateOf("") }
    var analysisType by remember { mutableStateOf(ImageAnalysisType.COMPREHENSIVE) }
    var analysisQuestions by remember { mutableStateOf("") }
    var analysisKeywords by remember { mutableStateOf("") }
    var triggerOnKeywordMatch by remember { mutableStateOf(false) }
    var minimumConfidence by remember { mutableStateOf(0.5f) }
    var enableOCR by remember { mutableStateOf(true) }
    var enableObjectDetection by remember { mutableStateOf(true) }
    var enablePeopleDetection by remember { mutableStateOf(true) }
    
    var imageAttachments by remember { mutableStateOf<List<ImageAttachment>>(emptyList()) }
    
    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        val newAttachments = uris.mapIndexed { index, uri ->
            createImageAttachmentFromUri(context, uri, index)
        }
        imageAttachments = imageAttachments + newAttachments
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Image Analysis Trigger",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            // Basic Configuration
            OutlinedTextField(
                value = triggerName,
                onValueChange = { triggerName = it },
                label = { Text("Trigger Name") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g., 'Analyze uploaded photos'") }
            )
            
            // Analysis Type Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Analysis Type",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ImageAnalysisType.values().forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { analysisType = type }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = analysisType == type,
                                onClick = { analysisType = type }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = type.name.replace("_", " ").lowercase()
                                        .replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = getAnalysisTypeDescription(type),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Analysis Questions
            OutlinedTextField(
                value = analysisQuestions,
                onValueChange = { analysisQuestions = it },
                label = { Text("Analysis Questions") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g., 'How many people are in this image?', 'What objects do you see?'") },
                maxLines = 3
            )
            
            // Analysis Keywords
            OutlinedTextField(
                value = analysisKeywords,
                onValueChange = { analysisKeywords = it },
                label = { Text("Keywords to Look For") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g., 'person, car, building' (comma-separated)") },
                maxLines = 2
            )
            
            // Trigger Options
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Trigger Options",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = triggerOnKeywordMatch,
                            onCheckedChange = { triggerOnKeywordMatch = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Only trigger when keywords are found",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    // Minimum Confidence Slider
                    Column {
                        Text(
                            text = "Minimum Confidence: ${String.format("%.0f", minimumConfidence * 100)}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = minimumConfidence,
                            onValueChange = { minimumConfidence = it },
                            valueRange = 0.0f..1.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // Analysis Features
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = enableOCR,
                            onCheckedChange = { enableOCR = it }
                        )
                        Text(
                            text = "OCR",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Checkbox(
                            checked = enableObjectDetection,
                            onCheckedChange = { enableObjectDetection = it }
                        )
                        Text(
                            text = "Objects",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Checkbox(
                            checked = enablePeopleDetection,
                            onCheckedChange = { enablePeopleDetection = it }
                        )
                        Text(
                            text = "People",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            // Image Attachments Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Image Attachments (${imageAttachments.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.size(width = 120.dp, height = 36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add Images",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    if (imageAttachments.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No images attached. Click 'Add Images' to upload.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(imageAttachments) { attachment ->
                                ImageAttachmentItem(
                                    attachment = attachment,
                                    onRemove = {
                                        imageAttachments = imageAttachments.filter { it.id != attachment.id }
                                    },
                                    onEdit = { updatedAttachment ->
                                        imageAttachments = imageAttachments.map {
                                            if (it.id == updatedAttachment.id) updatedAttachment else it
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // Create Trigger Button
            Button(
                onClick = {
                    if (triggerName.isNotBlank()) {
                        val questions = analysisQuestions.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                        
                        val keywords = analysisKeywords.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                        
                        val trigger = MultiUserTrigger.ImageAnalysisTrigger(
                            userId = "current_user", // This should be filled by the calling component
                            triggerName = triggerName,
                            imageAttachments = imageAttachments,
                            analysisQuestions = questions,
                            analysisKeywords = keywords,
                            analysisType = analysisType,
                            triggerOnKeywordMatch = triggerOnKeywordMatch,
                            minimumConfidence = minimumConfidence,
                            enableOCR = enableOCR,
                            enableObjectDetection = enableObjectDetection,
                            enablePeopleDetection = enablePeopleDetection
                        )
                        
                        onTriggerCreated(trigger)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = triggerName.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Image Analysis Trigger")
            }
        }
    }
}

@Composable
private fun ImageAttachmentItem(
    attachment: ImageAttachment,
    onRemove: () -> Unit,
    onEdit: (ImageAttachment) -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "${formatFileSize(attachment.fileSize)} • ${attachment.mimeType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (attachment.analysisQuestions.isNotEmpty()) {
                    Text(
                        text = "Questions: ${attachment.analysisQuestions.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            IconButton(onClick = { showEditDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit attachment"
                )
            }
            
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove attachment",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    
    if (showEditDialog) {
        ImageAttachmentEditDialog(
            attachment = attachment,
            onSave = { updatedAttachment ->
                onEdit(updatedAttachment)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageAttachmentEditDialog(
    attachment: ImageAttachment,
    onSave: (ImageAttachment) -> Unit,
    onDismiss: () -> Unit
) {
    var questions by remember { mutableStateOf(attachment.analysisQuestions.joinToString(", ")) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Image Attachment") },
        text = {
            Column {
                Text(
                    text = "File: ${attachment.fileName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = questions,
                    onValueChange = { questions = it },
                    label = { Text("Analysis Questions") },
                    placeholder = { Text("Specific questions for this image") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val questionsList = questions.split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    
                    onSave(attachment.copy(analysisQuestions = questionsList))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun createImageAttachmentFromUri(context: Context, uri: Uri, index: Int): ImageAttachment {
    val fileName = getFileNameFromUri(context, uri) ?: "image_$index.jpg"
    val fileSize = getFileSizeFromUri(context, uri)
    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
    
    return ImageAttachment(
        fileName = fileName,
        uri = uri.toString(),
        fileSize = fileSize,
        mimeType = mimeType
    )
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        }
    } catch (e: Exception) {
        null
    }
}

private fun getFileSizeFromUri(context: Context, uri: Uri): Long {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            cursor.moveToFirst()
            cursor.getLong(sizeIndex)
        } ?: 0L
    } catch (e: Exception) {
        0L
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

private fun getAnalysisTypeDescription(type: ImageAnalysisType): String {
    return when (type) {
        ImageAnalysisType.COMPREHENSIVE -> "Full analysis with OCR, objects, people, and visual elements"
        ImageAnalysisType.OCR_ONLY -> "Extract text only"
        ImageAnalysisType.OBJECT_DETECTION -> "Detect objects and items in the image"
        ImageAnalysisType.PEOPLE_DETECTION -> "Count and analyze people in the image"
        ImageAnalysisType.QUICK_SCAN -> "Fast basic analysis"
        ImageAnalysisType.CUSTOM -> "Custom analysis based on specific parameters"
    }
}