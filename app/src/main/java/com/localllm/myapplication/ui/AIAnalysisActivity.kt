package com.localllm.myapplication.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localllm.myapplication.ui.theme.MyApplicationTheme
import com.localllm.myapplication.ui.viewmodel.AIAnalysisViewModel
import com.localllm.myapplication.ui.components.*

class AIAnalysisActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                AIAnalysisScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAnalysisScreen(viewModel: AIAnalysisViewModel = viewModel()) {
    val context = LocalContext.current
    var textInput by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
        uri?.let { 
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                viewModel.processImage(bitmap)
            } catch (e: Exception) {
                viewModel.setError("Failed to load image: ${e.message}")
            }
        }
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            viewModel.processImage(it)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "AI Analysis Center",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Image Analysis",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pick Image")
                    }
                    
                    Button(
                        onClick = { cameraLauncher.launch(null) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Take Photo")
                    }
                }
            }
        }
        
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Text Analysis",
                    style = MaterialTheme.typography.titleMedium
                )
                
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Enter text to analyze") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                
                Button(
                    onClick = { 
                        if (textInput.isNotBlank()) {
                            viewModel.processText(textInput)
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                    enabled = textInput.isNotBlank()
                ) {
                    Text("Analyze Text")
                }
            }
        }
        
        if (viewModel.isLoading) {
            Card {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        
        viewModel.errorMessage?.let { error ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        AIResultsDisplay(viewModel = viewModel)
    }
}

@Composable
fun AIResultsDisplay(viewModel: AIAnalysisViewModel) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        viewModel.imageResults?.let { results ->
            item {
                ResultCard(title = "Object Detection") {
                    DisplayDetectionResults(results.objectDetection)
                }
            }
            
            item {
                ResultCard(title = "Image Classification") {
                    DisplayClassificationResults(results.imageClassification)
                }
            }
            
            item {
                ResultCard(title = "Face Detection") {
                    DisplayFaceResults(results.faceDetection)
                }
            }
        }
        
        viewModel.textResults?.let { results ->
            item {
                ResultCard(title = "Text Classification") {
                    DisplayClassificationResults(results.textClassification)
                }
            }
            
            item {
                ResultCard(title = "Language Detection") {
                    DisplayLanguageResults(results.languageDetection)
                }
            }
        }
    }
}

@Composable
fun ResultCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}