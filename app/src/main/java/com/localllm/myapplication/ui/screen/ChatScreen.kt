package com.localllm.myapplication.ui.screen

import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.localllm.myapplication.data.ChatMessage
import com.localllm.myapplication.ui.viewmodel.ChatViewModel
import java.io.File

// Helper functions for file handling
private fun getRealPathFromUri(context: android.content.Context, uri: Uri): String? {
    return try {
        when {
            DocumentsContract.isDocumentUri(context, uri) -> {
                // For document URIs, try to copy to cache first
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val fileName = getFileName(context, uri) ?: "model.task"
                    val cacheFile = File(context.cacheDir, fileName)
                    cacheFile.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    inputStream.close()
                    Log.d("ChatScreen", "Copied model to cache: ${cacheFile.absolutePath}")
                    cacheFile.absolutePath
                } else null
            }
            uri.scheme == "file" -> uri.path
            else -> {
                // Last resort: copy to cache
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val cacheFile = File(context.cacheDir, "temp_model.task")
                    cacheFile.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    inputStream.close()
                    cacheFile.absolutePath
                } else null
            }
        }
    } catch (e: Exception) {
        Log.e("ChatScreen", "Error resolving file path", e)
        null
    }
}

// Helper to get filename from URI
private fun getFileName(context: android.content.Context, uri: Uri): String? {
    return try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) it.getString(nameIndex) else null
            } else null
        }
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val chatSession by viewModel.chatSession
    val isModelLoading by viewModel.isModelLoading
    val isGeneratingResponse by viewModel.isGeneratingResponse
    val currentModelPath by viewModel.currentModelPath
    
    var messageText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    val context = LocalContext.current
    val listState = rememberLazyListState()
    
    // File picker for model
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            Log.d("ChatScreen", "Selected file URI: $uri")
            
            // Try to get the real file path
            val path = getRealPathFromUri(context, uri)
            Log.d("ChatScreen", "Resolved file path: $path")
            
            if (path != null && (path.endsWith(".task") || path.endsWith(".tflite"))) {
                Log.d("ChatScreen", "Loading model from: $path")
                viewModel.loadModel(path)
            } else {
                Log.e("ChatScreen", "Invalid file selected or path resolution failed. Path: $path")
                // Show error message to user
            }
        }
    }
    
    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
        // Convert URI to Bitmap here if needed
        // For now, we'll handle bitmap conversion in the viewModel
    }
    
    // Auto scroll to bottom when new messages arrive
    LaunchedEffect(chatSession.messages.size) {
        if (chatSession.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatSession.messages.size - 1)
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar with model info
        TopAppBar(
            title = {
                Column {
                    Text("Local LLM Chat")
                    if (chatSession.isModelLoaded) {
                        val modelName = currentModelPath?.let { path ->
                            if (path.contains("/")) {
                                path.substringAfterLast("/")
                            } else {
                                path
                            }
                        } ?: "Unknown"
                        Text(
                            "Model loaded: $modelName",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            "No model loaded",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            actions = {
                Button(
                    onClick = { 
                        Log.d("ChatScreen", "Model picker button clicked")
                        modelPickerLauncher.launch("*/*") 
                    },
                    enabled = !isModelLoading
                ) {
                    if (isModelLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Load Model")
                    }
                }
            }
        )
        
        // Chat messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chatSession.messages) { message ->
                ChatMessageItem(message = message)
            }
            
            if (isGeneratingResponse) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            modifier = Modifier.padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generating response...")
                            }
                        }
                    }
                }
            }
        }
        
        // Selected image preview
        selectedImageUri?.let { uri ->
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .height(100.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Selected image",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Image selected", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { 
                            selectedImageUri = null
                            selectedImageBitmap = null
                        }) {
                            Text("Remove")
                        }
                    }
                }
            }
        }
        
        // Input area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Type your message...") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Image picker button
                    IconButton(
                        onClick = { imagePickerLauncher.launch("image/*") }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add image")
                    }
                    
                    // Send button
                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendMessage(messageText, selectedImageBitmap)
                                messageText = ""
                                selectedImageUri = null
                                selectedImageBitmap = null
                            }
                        },
                        enabled = messageText.isNotBlank() && !isGeneratingResponse
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(vertical = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isFromUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Show image if available
                message.image?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Message image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isFromUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}