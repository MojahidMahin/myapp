package com.localllm.myapplication.ui.screen

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.localllm.myapplication.data.ChatMessage
import com.localllm.myapplication.ui.viewmodel.ChatViewModel
import com.localllm.myapplication.ui.AIAnalysisActivity
import java.io.File


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    Log.d("ChatScreen", "ChatScreen composable called")
    val chatSession by viewModel.chatSession
    val isModelLoading by viewModel.isModelLoading
    val isGeneratingResponse by viewModel.isGeneratingResponse
    val canStopGeneration by viewModel.canStopGeneration
    val currentModelPath by viewModel.currentModelPath
    
    Log.d("ChatScreen", "State loaded - modelLoading: $isModelLoading, modelPath: $currentModelPath")
    
    var messageText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    val context = LocalContext.current
    val listState = rememberLazyListState()
    
    
    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
        uri?.let { 
            try {
                // Convert URI to Bitmap immediately for multimodal processing
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                selectedImageBitmap = bitmap
                Log.d("ChatScreen", "Image loaded as bitmap: ${bitmap?.width}x${bitmap?.height}")
            } catch (e: Exception) {
                Log.e("ChatScreen", "Failed to load image as bitmap", e)
                selectedImageBitmap = null
                selectedImageUri = null
            }
        }
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
                IconButton(
                    onClick = {
                        val intent = android.content.Intent(context, AIAnalysisActivity::class.java)
                        context.startActivity(intent)
                    }
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "AI Analysis")
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
                                Spacer(modifier = Modifier.width(8.dp))
                                if (canStopGeneration) {
                                    IconButton(
                                        onClick = { viewModel.stopGeneration() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Stop Generation",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
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
    val clipboardManager = LocalClipboardManager.current
    var showCopyIcon by remember { mutableStateOf(false) }
    
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
                .padding(vertical = 2.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            showCopyIcon = true
                        },
                        onTap = {
                            if (showCopyIcon) {
                                showCopyIcon = false
                            }
                        }
                    )
                },
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
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.isFromUser) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (showCopyIcon && message.text.isNotBlank()) {
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(message.text))
                                showCopyIcon = false
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "Copy text",
                                modifier = Modifier.size(16.dp),
                                tint = if (message.isFromUser) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}