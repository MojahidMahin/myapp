package com.localllm.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localllm.myapplication.data.*

/**
 * Composable for creating image analysis actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageAnalysisActionComposer(
    onActionCreated: (MultiUserAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedActionType by remember { mutableStateOf(ImageAnalysisActionType.SINGLE_ANALYSIS) }
    
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
                text = "Image Analysis Action",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            // Action Type Selection
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
                        text = "Action Type",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ImageAnalysisActionType.values().forEach { actionType ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedActionType = actionType }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedActionType == actionType,
                                onClick = { selectedActionType = actionType }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = actionType.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = actionType.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Action-specific configuration
            when (selectedActionType) {
                ImageAnalysisActionType.SINGLE_ANALYSIS -> {
                    SingleImageAnalysisComposer(onActionCreated = onActionCreated)
                }
                ImageAnalysisActionType.BATCH_ANALYSIS -> {
                    BatchImageAnalysisComposer(onActionCreated = onActionCreated)
                }
                ImageAnalysisActionType.IMAGE_COMPARISON -> {
                    ImageComparisonComposer(onActionCreated = onActionCreated)
                }
            }
        }
    }
}

@Composable
private fun SingleImageAnalysisComposer(
    onActionCreated: (MultiUserAction.AIImageAnalysisAction) -> Unit
) {
    var imageSource by remember { mutableStateOf(ImageSourceType.TRIGGER_IMAGE) }
    var imageSourceValue by remember { mutableStateOf("") }
    var analysisType by remember { mutableStateOf(ImageAnalysisType.COMPREHENSIVE) }
    var analysisQuestions by remember { mutableStateOf("") }
    var outputVariable by remember { mutableStateOf("image_analysis_result") }
    var saveToFile by remember { mutableStateOf(false) }
    var enableOCR by remember { mutableStateOf(true) }
    var enableObjectDetection by remember { mutableStateOf(true) }
    var enablePeopleDetection by remember { mutableStateOf(true) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Image Source Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "Image Source",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                ImageSourceType.values().forEach { sourceType ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { imageSource = sourceType }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = imageSource == sourceType,
                            onClick = { imageSource = sourceType }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = sourceType.displayName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                if (imageSource != ImageSourceType.TRIGGER_IMAGE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = imageSourceValue,
                        onValueChange = { imageSourceValue = it },
                        label = { Text(getSourceValueLabel(imageSource)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(getSourceValuePlaceholder(imageSource)) }
                    )
                }
            }
        }
        
        // Analysis Configuration
        ImageAnalysisConfigurationSection(
            analysisType = analysisType,
            onAnalysisTypeChange = { analysisType = it },
            analysisQuestions = analysisQuestions,
            onAnalysisQuestionsChange = { analysisQuestions = it },
            enableOCR = enableOCR,
            onEnableOCRChange = { enableOCR = it },
            enableObjectDetection = enableObjectDetection,
            onEnableObjectDetectionChange = { enableObjectDetection = it },
            enablePeopleDetection = enablePeopleDetection,
            onEnablePeopleDetectionChange = { enablePeopleDetection = it }
        )
        
        // Output Configuration
        OutlinedTextField(
            value = outputVariable,
            onValueChange = { outputVariable = it },
            label = { Text("Output Variable Name") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("image_analysis_result") }
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = saveToFile,
                onCheckedChange = { saveToFile = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Save detailed analysis to file",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        // Create Action Button
        Button(
            onClick = {
                val imageSourceObj = when (imageSource) {
                    ImageSourceType.TRIGGER_IMAGE -> ImageSource.TriggerImageSource()
                    ImageSourceType.FILE_PATH -> ImageSource.FilePathSource(imageSourceValue)
                    ImageSourceType.VARIABLE -> ImageSource.VariableSource(imageSourceValue)
                    ImageSourceType.URI -> ImageSource.UriSource(imageSourceValue)
                }
                
                val questions = analysisQuestions.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                
                val action = MultiUserAction.AIImageAnalysisAction(
                    imageSource = imageSourceObj,
                    analysisType = analysisType,
                    analysisQuestions = questions,
                    enableOCR = enableOCR,
                    enableObjectDetection = enableObjectDetection,
                    enablePeopleDetection = enablePeopleDetection,
                    outputVariable = outputVariable,
                    saveAnalysisToFile = saveToFile
                )
                
                onActionCreated(action)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = outputVariable.isNotBlank() && (imageSource == ImageSourceType.TRIGGER_IMAGE || imageSourceValue.isNotBlank())
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Single Image Analysis Action")
        }
    }
}

@Composable
private fun BatchImageAnalysisComposer(
    onActionCreated: (MultiUserAction.AIBatchImageAnalysisAction) -> Unit
) {
    var analysisType by remember { mutableStateOf(ImageAnalysisType.COMPREHENSIVE) }
    var analysisQuestions by remember { mutableStateOf("") }
    var outputVariable by remember { mutableStateOf("batch_analysis_results") }
    var combineResults by remember { mutableStateOf(true) }
    var saveIndividual by remember { mutableStateOf(false) }
    var parallelProcessing by remember { mutableStateOf(true) }
    var enableOCR by remember { mutableStateOf(true) }
    var enableObjectDetection by remember { mutableStateOf(true) }
    var enablePeopleDetection by remember { mutableStateOf(true) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Note: Batch analysis will process all images from the trigger.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        )
        
        // Analysis Configuration
        ImageAnalysisConfigurationSection(
            analysisType = analysisType,
            onAnalysisTypeChange = { analysisType = it },
            analysisQuestions = analysisQuestions,
            onAnalysisQuestionsChange = { analysisQuestions = it },
            enableOCR = enableOCR,
            onEnableOCRChange = { enableOCR = it },
            enableObjectDetection = enableObjectDetection,
            onEnableObjectDetectionChange = { enableObjectDetection = it },
            enablePeopleDetection = enablePeopleDetection,
            onEnablePeopleDetectionChange = { enablePeopleDetection = it }
        )
        
        // Batch Options
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
                    text = "Batch Processing Options",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = combineResults,
                        onCheckedChange = { combineResults = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Combine results into summary",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = saveIndividual,
                        onCheckedChange = { saveIndividual = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save individual analyses",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = parallelProcessing,
                        onCheckedChange = { parallelProcessing = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Process images in parallel (faster)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        // Output Configuration
        OutlinedTextField(
            value = outputVariable,
            onValueChange = { outputVariable = it },
            label = { Text("Output Variable Name") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("batch_analysis_results") }
        )
        
        // Create Action Button
        Button(
            onClick = {
                val questions = analysisQuestions.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                
                val action = MultiUserAction.AIBatchImageAnalysisAction(
                    imageSources = listOf(ImageSource.TriggerImageSource()), // Will be populated with actual sources
                    analysisType = analysisType,
                    analysisQuestions = questions,
                    enableOCR = enableOCR,
                    enableObjectDetection = enableObjectDetection,
                    enablePeopleDetection = enablePeopleDetection,
                    outputVariable = outputVariable,
                    combineResults = combineResults,
                    saveIndividualAnalyses = saveIndividual,
                    parallelProcessing = parallelProcessing
                )
                
                onActionCreated(action)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = outputVariable.isNotBlank()
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Batch Analysis Action")
        }
    }
}

@Composable
private fun ImageComparisonComposer(
    onActionCreated: (MultiUserAction.AIImageComparisonAction) -> Unit
) {
    var comparisonType by remember { mutableStateOf(ImageComparisonType.COMPREHENSIVE) }
    var outputVariable by remember { mutableStateOf("image_comparison_result") }
    var includeDetailedDifferences by remember { mutableStateOf(true) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Note: Image comparison will use the first trigger image as primary and compare with additional images.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        )
        
        // Comparison Type Selection
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
                    text = "Comparison Type",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                ImageComparisonType.values().forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { comparisonType = type }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = comparisonType == type,
                            onClick = { comparisonType = type }
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
                                text = getComparisonTypeDescription(type),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        
        // Comparison Options
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = includeDetailedDifferences,
                onCheckedChange = { includeDetailedDifferences = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Include detailed differences in report",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        // Output Configuration
        OutlinedTextField(
            value = outputVariable,
            onValueChange = { outputVariable = it },
            label = { Text("Output Variable Name") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("image_comparison_result") }
        )
        
        // Create Action Button
        Button(
            onClick = {
                val action = MultiUserAction.AIImageComparisonAction(
                    primaryImageSource = ImageSource.TriggerImageSource(0),
                    comparisonImageSources = listOf(ImageSource.TriggerImageSource(1)), // Will be populated with actual sources
                    comparisonType = comparisonType,
                    outputVariable = outputVariable,
                    includeDetailedDifferences = includeDetailedDifferences
                )
                
                onActionCreated(action)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = outputVariable.isNotBlank()
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Image Comparison Action")
        }
    }
}

@Composable
private fun ImageAnalysisConfigurationSection(
    analysisType: ImageAnalysisType,
    onAnalysisTypeChange: (ImageAnalysisType) -> Unit,
    analysisQuestions: String,
    onAnalysisQuestionsChange: (String) -> Unit,
    enableOCR: Boolean,
    onEnableOCRChange: (Boolean) -> Unit,
    enableObjectDetection: Boolean,
    onEnableObjectDetectionChange: (Boolean) -> Unit,
    enablePeopleDetection: Boolean,
    onEnablePeopleDetectionChange: (Boolean) -> Unit
) {
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
                text = "Analysis Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Analysis Type
            Text(
                text = "Analysis Type",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            ImageAnalysisType.values().forEach { type ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAnalysisTypeChange(type) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = analysisType == type,
                        onClick = { onAnalysisTypeChange(type) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = type.name.replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Analysis Questions
            OutlinedTextField(
                value = analysisQuestions,
                onValueChange = onAnalysisQuestionsChange,
                label = { Text("Analysis Questions") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g., 'How many people?', 'What objects are visible?'") },
                maxLines = 3
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Analysis Features
            Text(
                text = "Analysis Features",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = enableOCR,
                    onCheckedChange = onEnableOCRChange
                )
                Text(
                    text = "OCR",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                
                Checkbox(
                    checked = enableObjectDetection,
                    onCheckedChange = onEnableObjectDetectionChange
                )
                Text(
                    text = "Objects",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                
                Checkbox(
                    checked = enablePeopleDetection,
                    onCheckedChange = onEnablePeopleDetectionChange
                )
                Text(
                    text = "People",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// Helper enums and functions
enum class ImageAnalysisActionType(val displayName: String, val description: String) {
    SINGLE_ANALYSIS("Single Image Analysis", "Analyze a single image from trigger or specified source"),
    BATCH_ANALYSIS("Batch Image Analysis", "Analyze multiple images and combine results"),
    IMAGE_COMPARISON("Image Comparison", "Compare images and find differences/similarities")
}

enum class ImageSourceType(val displayName: String) {
    TRIGGER_IMAGE("From Trigger"),
    FILE_PATH("File Path"),
    VARIABLE("From Variable"),
    URI("URI/URL")
}

private fun getSourceValueLabel(sourceType: ImageSourceType): String {
    return when (sourceType) {
        ImageSourceType.FILE_PATH -> "File Path"
        ImageSourceType.VARIABLE -> "Variable Name"
        ImageSourceType.URI -> "URI/URL"
        ImageSourceType.TRIGGER_IMAGE -> ""
    }
}

private fun getSourceValuePlaceholder(sourceType: ImageSourceType): String {
    return when (sourceType) {
        ImageSourceType.FILE_PATH -> "/storage/emulated/0/Pictures/image.jpg"
        ImageSourceType.VARIABLE -> "image_file_path"
        ImageSourceType.URI -> "content://media/external/images/media/123"
        ImageSourceType.TRIGGER_IMAGE -> ""
    }
}

private fun getComparisonTypeDescription(type: ImageComparisonType): String {
    return when (type) {
        ImageComparisonType.VISUAL_SIMILARITY -> "Compare visual similarity and colors"
        ImageComparisonType.OBJECT_DIFFERENCES -> "Compare detected objects"
        ImageComparisonType.TEXT_DIFFERENCES -> "Compare OCR text content"
        ImageComparisonType.COLOR_DIFFERENCES -> "Compare color schemes"
        ImageComparisonType.STRUCTURAL_DIFFERENCES -> "Compare composition and structure"
        ImageComparisonType.COMPREHENSIVE -> "All comparison types"
    }
}