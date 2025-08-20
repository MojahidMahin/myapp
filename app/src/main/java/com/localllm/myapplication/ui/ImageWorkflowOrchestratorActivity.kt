package com.localllm.myapplication.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localllm.myapplication.service.ImageWorkflowOrchestrator
import com.localllm.myapplication.service.WorkflowResult
import com.localllm.myapplication.service.NotificationService
import com.localllm.myapplication.ui.theme.MyApplicationTheme
import com.localllm.myapplication.ui.theme.*
import kotlinx.coroutines.launch

class ImageWorkflowOrchestratorActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MyApplicationTheme {
                GradientBackground {
                    ImageWorkflowOrchestratorScreen(
                        onNavigateBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageWorkflowOrchestratorScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var instruction by remember { mutableStateOf("") }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var outputFormat by remember { mutableStateOf("json") }
    var deliveryMethod by remember { mutableStateOf<String?>(null) }
    var recipientEmails by remember { mutableStateOf("") }
    var telegramChatId by remember { mutableStateOf("") }
    var notificationTitle by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var workflowResult by remember { mutableStateOf<WorkflowResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showJsonResult by remember { mutableStateOf(false) }
    var expandedOutputFormat by remember { mutableStateOf(false) }
    var expandedDeliveryMethod by remember { mutableStateOf(false) }
    
    val orchestrator = remember { ImageWorkflowOrchestrator(context) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            attachedImageUri = data?.data
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("🎬 Image Workflow Orchestrator") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Welcome Card
            ModernCard {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = AccentBlue
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AI Image Workflow Orchestrator",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Process natural language instructions for image workflows",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    ModernSpacing()
                    
                    Text(
                        text = "💡 Examples:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentOrange
                    )
                    
                    ModernSmallSpacing()
                    
                    val examples = listOf(
                        "Find all receipts from last week and calculate total spending",
                        "Show me wedding photos from past month",
                        "Find screenshots from yesterday",
                        "Analyze all food images from last 24 hours",
                        "Find images with text from today"
                    )
                    
                    examples.forEach { example ->
                        Text(
                            text = "• $example",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
            
            // Instruction Input Card
            ModernCard {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = AccentGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Workflow Instruction",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    ModernSpacing()
                    
                    OutlinedTextField(
                        value = instruction,
                        onValueChange = { instruction = it },
                        label = { Text("Enter your instruction") },
                        placeholder = { Text("e.g., Find all receipts from last week and calculate total spending") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            focusedLabelColor = AccentBlue
                        )
                    )
                }
            }
            
            // Attached Image Card
            ModernCard {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = AccentBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Attach Image from Gallery (Optional)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        if (attachedImageUri != null) {
                            IconButton(
                                onClick = { attachedImageUri = null }
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Remove image",
                                    tint = AccentRed
                                )
                            }
                        }
                    }
                    
                    ModernSpacing()
                    
                    if (attachedImageUri != null) {
                        Surface(
                            color = AccentGreen.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = AccentGreen
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Gallery image attached",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AccentGreen,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        ModernSmallSpacing()
                    } else {
                        Text(
                            text = "Attach an image from gallery for similarity matching (useful for 'like this image' instructions)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ModernSmallSpacing()
                    }
                    
                    // Enhanced image picker button
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                            imagePickerLauncher.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (attachedImageUri != null) AccentOrange else AccentBlue
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (attachedImageUri != null) Icons.Default.Edit else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (attachedImageUri != null) "Change Attached Image" else "📷 Select Image from Gallery",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            // Output Format Card
            ModernCard {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = AccentOrange
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Output Format",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    ModernSpacing()
                    
                    // Enhanced dropdown styling
                    ExposedDropdownMenuBox(
                        expanded = expandedOutputFormat,
                        onExpandedChange = { expandedOutputFormat = !expandedOutputFormat },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = when (outputFormat) {
                                "json" -> "JSON"
                                "text" -> "Text"
                                "summary" -> "Summary"
                                else -> "JSON"
                            },
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Select output format") },
                            placeholder = { Text("Choose format...") },
                            trailingIcon = { 
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedOutputFormat)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentOrange,
                                focusedLabelColor = AccentOrange,
                                unfocusedBorderColor = AccentOrange.copy(alpha = 0.5f)
                            ),
                            singleLine = true
                        )
                        
                        ExposedDropdownMenu(
                            expanded = expandedOutputFormat,
                            onDismissRequest = { expandedOutputFormat = false }
                        ) {
                            listOf(
                                "json" to "JSON",
                                "text" to "Text", 
                                "summary" to "Summary"
                            ).forEach { (value, display) ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            text = display,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    onClick = {
                                        outputFormat = value
                                        expandedOutputFormat = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // Delivery Method Card
            ModernCard {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = AccentGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Delivery Method (Optional)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    ModernSpacing()
                    
                    // Enhanced delivery method dropdown
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
                                else -> "❌ None"
                            },
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Select delivery method") },
                            placeholder = { Text("Choose delivery method...") },
                            trailingIcon = { 
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDeliveryMethod)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGreen,
                                focusedLabelColor = AccentGreen,
                                unfocusedBorderColor = AccentGreen.copy(alpha = 0.5f)
                            ),
                            singleLine = true
                        )
                        
                        ExposedDropdownMenu(
                            expanded = expandedDeliveryMethod,
                            onDismissRequest = { expandedDeliveryMethod = false }
                        ) {
                            listOf(
                                null to "❌ None",
                                "email" to "📧 Email",
                                "telegram" to "📱 Telegram",
                                "notification" to "🔔 Notification"
                            ).forEach { (value, display) ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            text = display,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    onClick = {
                                        deliveryMethod = value
                                        expandedDeliveryMethod = false
                                        // Reset fields when delivery method changes
                                        recipientEmails = ""
                                        telegramChatId = ""
                                        notificationTitle = ""
                                    }
                                )
                            }
                        }
                    }
                    
                    // Dynamic recipient fields based on delivery method
                    if (deliveryMethod != null) {
                        ModernSpacing()
                        
                        when (deliveryMethod) {
                            "email" -> {
                                OutlinedTextField(
                                    value = recipientEmails,
                                    onValueChange = { recipientEmails = it },
                                    label = { Text("📧 Recipient Emails") },
                                    placeholder = { Text("email1@example.com, email2@example.com") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentGreen,
                                        focusedLabelColor = AccentGreen
                                    ),
                                    singleLine = false,
                                    minLines = 2,
                                    maxLines = 3
                                )
                            }
                            "telegram" -> {
                                OutlinedTextField(
                                    value = telegramChatId,
                                    onValueChange = { telegramChatId = it },
                                    label = { Text("📱 Telegram Chat ID") },
                                    placeholder = { Text("Enter chat ID (e.g., 123456789)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentGreen,
                                        focusedLabelColor = AccentGreen
                                    ),
                                    singleLine = true
                                )
                            }
                            "notification" -> {
                                OutlinedTextField(
                                    value = notificationTitle,
                                    onValueChange = { notificationTitle = it },
                                    label = { Text("🔔 Notification Title") },
                                    placeholder = { Text("Enter notification title") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentGreen,
                                        focusedLabelColor = AccentGreen
                                    ),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
            }
            
            // Process Button
            ModernPrimaryButton(
                text = if (isProcessing) "Processing..." else "🎬 Process Workflow",
                onClick = {
                    if (instruction.isNotBlank() && !isProcessing) {
                        scope.launch {
                            isProcessing = true
                            errorMessage = null
                            workflowResult = null
                            
                            try {
                                val result = orchestrator.processWorkflowInstruction(
                                    instruction = instruction,
                                    referenceImageUri = attachedImageUri
                                )
                                
                                result.fold(
                                    onSuccess = { workflowResult = it },
                                    onFailure = { errorMessage = it.message }
                                )
                            } catch (e: Exception) {
                                errorMessage = e.message
                            } finally {
                                isProcessing = false
                            }
                        }
                    }
                },
                enabled = instruction.isNotBlank() && !isProcessing,
                modifier = Modifier.height(56.dp)
            )
            
            // Error Display
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = AccentRed.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = AccentRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.titleMedium,
                                color = AccentRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        ModernSmallSpacing()
                        
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            // Results Display
            if (workflowResult != null) {
                // Human Readable Result
                ModernCard {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = AccentGreen
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Workflow Result",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Row {
                                IconButton(
                                    onClick = { showJsonResult = !showJsonResult }
                                ) {
                                    Icon(
                                        if (showJsonResult) Icons.Default.Close else Icons.Default.Info,
                                        contentDescription = if (showJsonResult) "Hide JSON" else "Show JSON",
                                        tint = AccentBlue
                                    )
                                }
                                
                                IconButton(
                                    onClick = {
                                        val shareIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, orchestrator.formatResultAsHumanReadable(workflowResult!!))
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Workflow Result"))
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = "Share result",
                                        tint = AccentOrange
                                    )
                                }
                            }
                        }
                        
                        ModernSpacing()
                        
                        if (showJsonResult) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = orchestrator.formatResultAsJson(workflowResult!!),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    ),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            Text(
                                text = orchestrator.formatResultAsHumanReadable(workflowResult!!),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 20.sp
                            )
                        }
                        
                        ModernSpacing()
                        
                        // Action Summary
                        Surface(
                            color = AccentBlue.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = "🎯 Summary",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentBlue
                                )
                                ModernSmallSpacing()
                                Text(
                                    text = "Found ${workflowResult!!.imagesMatched} matching images out of ${workflowResult!!.imagesChecked} checked. Action: ${workflowResult!!.action}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            
            // Processing Indicator
            if (isProcessing) {
                ModernCard {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = AccentBlue
                        )
                        ModernSpacing()
                        Text(
                            text = "Processing your workflow instruction...",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                        ModernSmallSpacing()
                        Text(
                            text = "This may take a few moments while we analyze your gallery images",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}