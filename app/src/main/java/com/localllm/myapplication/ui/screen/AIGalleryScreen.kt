package com.localllm.myapplication.ui.screen

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localllm.myapplication.command.ai.TranscriptionMode
import com.localllm.myapplication.data.AIFeature
import com.localllm.myapplication.data.AIFeatureType
import com.localllm.myapplication.data.PromptTemplate
import com.localllm.myapplication.data.TemplateParameter
import com.localllm.myapplication.data.ParameterType
import com.localllm.myapplication.ui.viewmodel.AIGalleryViewModel
import com.localllm.myapplication.ui.components.TemplateParameterInput
import com.localllm.myapplication.ui.components.PromptLabResultCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIGalleryScreen(
    viewModel: AIGalleryViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedFeature by viewModel.selectedFeature
    val availableFeatures by viewModel.availableFeatures
    val isModelLoading by viewModel.isModelLoading
    val isProcessing by viewModel.isProcessing
    val modelLoadError by viewModel.modelLoadError
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { 
                Text(
                    text = selectedFeature?.label ?: "AI Gallery",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (selectedFeature != null) {
                    IconButton(onClick = { viewModel.goBackToFeatureSelection() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Features")
                    }
                }
                IconButton(onClick = { viewModel.resetSession() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset")
                }
                if (isProcessing) {
                    IconButton(onClick = { viewModel.stopGeneration() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Stop Generation")
                    }
                }
            }
        )
        
        // Feature Selection or Content
        if (selectedFeature == null) {
            FeatureSelectionScreen(
                features = availableFeatures,
                onFeatureSelected = { viewModel.selectFeature(it) },
                isModelLoading = isModelLoading,
                modelLoadError = modelLoadError,
                onLoadModel = { path -> viewModel.loadModel(path) }
            )
        } else {
            // Feature-specific content
            when (selectedFeature) {
                AIFeatureType.LLM_CHAT -> {
                    ChatFeatureScreen(
                        viewModel = viewModel,
                        isProcessing = isProcessing
                    )
                }
                AIFeatureType.ASK_IMAGE -> {
                    AskImageFeatureScreen(
                        viewModel = viewModel,
                        isProcessing = isProcessing
                    )
                }
                AIFeatureType.PROMPT_LAB -> {
                    PromptLabFeatureScreen(
                        viewModel = viewModel,
                        isProcessing = isProcessing
                    )
                }
                AIFeatureType.AUDIO_TRANSCRIPTION -> {
                    AudioTranscriptionFeatureScreen(
                        viewModel = viewModel,
                        isProcessing = isProcessing
                    )
                }
                AIFeatureType.GALLERY_ANALYSIS -> {
                    GalleryAnalysisFeatureScreen(
                        viewModel = viewModel,
                        isProcessing = isProcessing
                    )
                }
                else -> {
                    Text(
                        text = "Feature coming soon!",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureSelectionScreen(
    features: List<AIFeature>,
    onFeatureSelected: (AIFeatureType) -> Unit,
    isModelLoading: Boolean,
    modelLoadError: String?,
    onLoadModel: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "AI Gallery Features",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Select a feature to explore AI capabilities",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        // Model loading section
        item {
            ModelLoadingSection(
                isModelLoading = isModelLoading,
                modelLoadError = modelLoadError,
                onLoadModel = onLoadModel
            )
        }
        
        // Feature cards
        items(features) { feature ->
            FeatureCard(
                feature = feature,
                onClick = { onFeatureSelected(feature.type) }
            )
        }
    }
}

@Composable
fun ModelLoadingSection(
    isModelLoading: Boolean,
    modelLoadError: String?,
    onLoadModel: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Model Management",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (isModelLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Loading model...")
                }
            } else {
                Column {
                    Button(
                        onClick = { onLoadModel("/data/user/0/com.localllm.myapplication/cache/gemma-3n-E2B-it-int4.task") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load Default Model (Cache)")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { onLoadModel("gemma-3n-E2B-it-int4.task") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load Model (Assets)")
                    }
                }
            }
            
            modelLoadError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureCard(
    feature: AIFeature,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = feature.type.icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feature.type.label,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    text = feature.type.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ChatFeatureScreen(
    viewModel: AIGalleryViewModel,
    isProcessing: Boolean
) {
    val chatMessages by viewModel.chatMessages
    var currentInput by remember { mutableStateOf("") }
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chatMessages) { message ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = if (message.isFromUser) "You" else "AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = message.text,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        message.image?.let { bitmap ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "[Image attached: ${bitmap.width}x${bitmap.height}]",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            if (isProcessing) {
                item {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI is thinking...")
                    }
                }
            }
        }
        
        // Input area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            selectedImage?.let { bitmap ->
                Card {
                    Text(
                        text = "Image selected: ${bitmap.width}x${bitmap.height}",
                        modifier = Modifier.padding(8.dp),
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = currentInput,
                    onValueChange = { currentInput = it },
                    placeholder = { Text("Type a message...") },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(
                    onClick = { 
                        viewModel.sendChatMessage(currentInput, selectedImage)
                        currentInput = ""
                        selectedImage = null
                    },
                    enabled = !isProcessing && currentInput.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val context = LocalContext.current
            val imagePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                uri?.let { uriValue ->
                    selectedImage = try {
                        android.provider.MediaStore.Images.Media.getBitmap(
                            context.contentResolver, uriValue
                        )
                    } catch (e: Exception) {
                        null // Handle error by setting to null
                    }
                }
            }
            
            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing
            ) {
                Text("Add Image")
            }
        }
    }
}

@Composable
fun AskImageFeatureScreen(
    viewModel: AIGalleryViewModel, 
    isProcessing: Boolean
) {
    // Same as ChatFeatureScreen but with emphasis on image input
    ChatFeatureScreen(viewModel, isProcessing)
}

@Composable  
fun PromptLabFeatureScreen(
    viewModel: AIGalleryViewModel,
    isProcessing: Boolean
) {
    val availableTemplates by viewModel.availableTemplates
    val selectedTemplate by viewModel.selectedTemplate
    val templateParameters by viewModel.templateParameters
    val promptLabResults by viewModel.promptLabResults
    
    var userInput by remember { mutableStateOf("") }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Template selection
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(availableTemplates) { template ->
                FilterChip(
                    onClick = { viewModel.selectPromptTemplate(template) },
                    label = { Text(template.type.label) },
                    selected = selectedTemplate == template
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Template parameters
        selectedTemplate?.let { template ->
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                template.parameters.forEach { param ->
                    TemplateParameterInput(
                        parameter = param,
                        currentValue = templateParameters[param.key] ?: param.defaultValue,
                        onValueChange = { newValue ->
                            viewModel.updateTemplateParameter(param.key, newValue)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        
        // Input and execute
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = userInput,
                onValueChange = { userInput = it },
                label = { Text("Enter your input") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { 
                    viewModel.executePromptTemplate(userInput)
                    userInput = ""
                },
                enabled = !isProcessing && selectedTemplate != null && userInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Processing...")
                } else {
                    Text("Execute Template")
                }
            }
        }
        
        // Results
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(promptLabResults) { result ->
                PromptLabResultCard(result = result)
            }
        }
    }
}

@Composable
fun AudioTranscriptionFeatureScreen(
    viewModel: AIGalleryViewModel,
    isProcessing: Boolean  
) {
    val transcriptionMode by viewModel.transcriptionMode
    val transcriptionResults by viewModel.transcriptionResults
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Mode selection
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Transcription Mode",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TranscriptionMode.values().forEach { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = transcriptionMode == mode,
                        onClick = { viewModel.setTranscriptionMode(mode) }
                    )
                    Text(
                        text = mode.name.replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercase() }
                    )
                }
            }
        }
        
        // Audio input (placeholder)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Audio Recording Feature")
                Text(
                    "Coming soon - record audio for transcription",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Results
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(transcriptionResults) { result ->
                Card {
                    Text(
                        text = result,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryAnalysisFeatureScreen(
    viewModel: AIGalleryViewModel,
    isProcessing: Boolean
) {
    var prompt by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var deliveryMethod by remember { mutableStateOf("notification") }
    var recipientEmail by remember { mutableStateOf("") }
    var telegramChatId by remember { mutableStateOf("") }
    var analysisResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var expandedDeliveryMethod by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    // Image picker launcher for multiple images
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val bitmaps = mutableListOf<Bitmap>()
            uris.forEach { uri ->
                try {
                    val bitmap = android.provider.MediaStore.Images.Media.getBitmap(
                        context.contentResolver, uri
                    )
                    bitmaps.add(bitmap)
                } catch (e: Exception) {
                    Log.e("GalleryAnalysis", "Error loading image", e)
                }
            }
            selectedImages = bitmaps
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "🤖 Smart Gallery Analysis",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Upload images and let Gemma 3N decide whether to use OCR or face detection, then analyze the results.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Image Selection Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "📷 Select Images (${selectedImages.size} selected)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (selectedImages.isNotEmpty()) {
                        IconButton(onClick = { selectedImages = emptyList() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear images")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("📸 Attach Images from Gallery")
                }
                
                if (selectedImages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✅ ${selectedImages.size} image(s) ready for smart analysis",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        // Prompt Input Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "💬 Analysis Prompt",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Enter your analysis request") },
                    placeholder = { Text("e.g., 'What do you see in these images?' or 'Extract all text'") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isProcessing
                )
            }
        }
        
        // Delivery Method Card  
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "📤 Delivery Method",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                ExposedDropdownMenuBox(
                    expanded = expandedDeliveryMethod,
                    onExpandedChange = { expandedDeliveryMethod = !expandedDeliveryMethod },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = when (deliveryMethod) {
                            "email" -> "📧 Email"
                            "telegram" -> "📱 Telegram"
                            "notification" -> "🔔 Notification"
                            else -> "🔔 Notification"
                        },
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Select delivery method") },
                        trailingIcon = { 
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDeliveryMethod)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        enabled = !isProcessing
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expandedDeliveryMethod,
                        onDismissRequest = { expandedDeliveryMethod = false }
                    ) {
                        listOf(
                            "notification" to "🔔 Notification",
                            "email" to "📧 Email",
                            "telegram" to "📱 Telegram"
                        ).forEach { (value, display) ->
                            DropdownMenuItem(
                                text = { Text(display) },
                                onClick = {
                                    deliveryMethod = value
                                    expandedDeliveryMethod = false
                                    // Reset fields when delivery method changes
                                    recipientEmail = ""
                                    telegramChatId = ""
                                }
                            )
                        }
                    }
                }
                
                // Dynamic fields based on delivery method
                if (deliveryMethod == "email") {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = recipientEmail,
                        onValueChange = { recipientEmail = it },
                        label = { Text("📧 Recipient Email") },
                        placeholder = { Text("email@example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing
                    )
                } else if (deliveryMethod == "telegram") {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = telegramChatId,
                        onValueChange = { telegramChatId = it },
                        label = { Text("📱 Telegram Chat ID") },
                        placeholder = { Text("123456789") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing
                    )
                }
            }
        }
        
        // Process Button
        Button(
            onClick = {
                if (selectedImages.isNotEmpty() && prompt.isNotBlank()) {
                    viewModel.processGalleryAnalysis(
                        images = selectedImages,
                        prompt = prompt,
                        deliveryMethod = deliveryMethod,
                        recipientEmail = recipientEmail,
                        telegramChatId = telegramChatId
                    ) { results ->
                        analysisResults = results
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessing && selectedImages.isNotEmpty() && prompt.isNotBlank()
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Processing...")
            } else {
                Icon(Icons.Default.Star, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("🤖 Start Smart Analysis")
            }
        }
        
        // Results
        if (analysisResults.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "📊 Analysis Results",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    analysisResults.forEach { result ->
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}