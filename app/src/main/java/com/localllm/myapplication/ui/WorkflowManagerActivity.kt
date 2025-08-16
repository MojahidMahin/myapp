package com.localllm.myapplication.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.localllm.myapplication.service.integration.GmailIntegrationService
import com.localllm.myapplication.service.integration.AuthConsentRequiredException
import com.localllm.myapplication.ui.screen.TelegramBotDynamicScreen
import com.localllm.myapplication.data.*
import com.localllm.myapplication.di.AppContainer
import com.localllm.myapplication.ui.theme.MyApplicationTheme
import com.localllm.myapplication.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Activity for managing workflows with Gmail integration
 */
class WorkflowManagerActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MyApplicationTheme {
                GradientBackground {
                    WorkflowManagerScreen(
                        onNavigateBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkflowManagerScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Initialize workflow services
    val userManager = remember { AppContainer.provideUserManager(context) }
    val workflowRepository = remember { AppContainer.provideWorkflowRepository(context) }
    val triggerManager = remember { AppContainer.provideTriggerManager(context) }
    
    // Initialize demo user and start trigger manager
    LaunchedEffect(Unit) {
        userManager.initializeDemoUser()
        
        // Check if Gmail is already signed in and register it
        try {
            val existingAccount = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
            if (existingAccount != null) {
                val existingGmailService = GmailIntegrationService(context)
                val handleResult = existingGmailService.handleSignInResult(existingAccount)
                handleResult.fold(
                    onSuccess = {
                        userManager.registerGmailService("user_1", existingGmailService)
                        android.util.Log.i("WorkflowManager", "Existing Gmail account registered for workflows")
                    },
                    onFailure = { error ->
                        android.util.Log.w("WorkflowManager", "Failed to register existing Gmail: ${error.message}")
                    }
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("WorkflowManager", "Failed to check existing Gmail sign-in", e)
        }
        
        // Check if Telegram is already configured and register it
        val telegramPrefs = com.localllm.myapplication.data.TelegramPreferences(context)
        val savedToken = telegramPrefs.getBotToken()
        if (!savedToken.isNullOrBlank()) {
            try {
                val telegramService = com.localllm.myapplication.service.integration.TelegramBotService(context)
                // Initialize the service with the saved token
                val initResult = telegramService.initializeBot(savedToken)
                initResult.fold(
                    onSuccess = { botInfo ->
                        userManager.registerTelegramService("user_1", telegramService)
                        android.util.Log.i("WorkflowManager", "Existing Telegram bot registered for workflows: $botInfo")
                    },
                    onFailure = { error ->
                        android.util.Log.w("WorkflowManager", "Failed to initialize existing Telegram bot: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("WorkflowManager", "Failed to register existing Telegram service", e)
            }
        }
        
        // Start the trigger manager to enable automatic workflows
        triggerManager.start()
        android.util.Log.i("WorkflowManager", "Trigger manager started for automatic workflows")
    }
    
    // Gmail integration service
    val gmailService = remember { GmailIntegrationService(context) }
    
    // State management
    var selectedTab by remember { mutableStateOf(0) }
    var gmailSignedIn by remember { mutableStateOf(false) }
    var gmailAccount by remember { mutableStateOf<String?>(null) }
    var emails by remember { mutableStateOf<List<GmailIntegrationService.EmailMessage>>(emptyList()) }
    var isLoadingEmails by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var emailCount by remember { mutableStateOf("5") }
    var filterKeywords by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("all") } // "all", "subject", "from", "body" 
    
    // Gmail sign-in launcher
    val gmailSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            // Handle Gmail sign-in result
            scope.launch {
                try {
                    if (data != null) {
                        // Parse the Google Sign-In result
                        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
                        val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                        
                        // Handle successful sign-in with the Gmail service
                        val handleResult = gmailService.handleSignInResult(account)
                        handleResult.fold(
                            onSuccess = {
                                gmailSignedIn = true
                                gmailAccount = account.email
                                errorMessage = null
                                
                                // Register Gmail service with UserManager for workflows
                                try {
                                    userManager.registerGmailService("user_1", gmailService)
                                    android.util.Log.i("WorkflowManager", "Gmail service registered for workflows")
                                } catch (e: Exception) {
                                    android.util.Log.e("WorkflowManager", "Failed to register Gmail service for workflows", e)
                                }
                                
                                android.widget.Toast.makeText(
                                    context,
                                    "✅ Gmail signed in successfully! Workflows can now access Gmail.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            },
                            onFailure = { error ->
                                Log.e("WorkflowManager", "Failed to setup Gmail service", error)
                                errorMessage = "Failed to setup Gmail service: ${error.message}"
                                gmailSignedIn = false
                                gmailAccount = null
                            }
                        )
                    }
                } catch (e: com.google.android.gms.common.api.ApiException) {
                    Log.e("WorkflowManager", "Gmail sign-in failed", e)
                    errorMessage = "Gmail sign-in failed: ${e.message}"
                    gmailSignedIn = false
                    gmailAccount = null
                } catch (e: Exception) {
                    Log.e("WorkflowManager", "Gmail sign-in failed", e)
                    errorMessage = "Gmail sign-in failed: ${e.message}"
                    gmailSignedIn = false
                    gmailAccount = null
                }
            }
        } else {
            // Sign-in was cancelled or failed
            Log.w("WorkflowManager", "Gmail sign-in cancelled or failed")
            gmailSignedIn = false
            gmailAccount = null
        }
    }
    
    // Initialize Gmail service
    LaunchedEffect(Unit) {
        gmailService.initialize()
        // Check if already signed in
        if (gmailService.isSignedIn()) {
            gmailSignedIn = true
            gmailAccount = gmailService.getCurrentUserEmail()
        }
    }
    
    val tabs = listOf("Workflows", "Gmail", "Telegram", "Settings")
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Workflow Manager") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        val intent = Intent(context, WorkflowMonitorActivity::class.java)
                        context.startActivity(intent)
                    }
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Monitor")
                }
            }
        )
        
        // Tab Row
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    text = { Text(title) },
                    selected = selectedTab == index,
                    onClick = { selectedTab = index }
                )
            }
        }
        
        // Tab Content
        when (selectedTab) {
            0 -> WorkflowsTab()
            1 -> GmailTab(
                gmailService = gmailService,
                gmailSignedIn = gmailSignedIn,
                gmailAccount = gmailAccount,
                emails = emails,
                isLoadingEmails = isLoadingEmails,
                errorMessage = errorMessage,
                emailCount = emailCount,
                filterKeywords = filterKeywords,
                filterType = filterType,
                onEmailCountChanged = { emailCount = it },
                onFilterKeywordsChanged = { filterKeywords = it },
                onFilterTypeChanged = { filterType = it },
                onSignIn = {
                    scope.launch {
                        try {
                            val signInClient = gmailService.getSignInClient()
                            if (signInClient != null) {
                                val signInIntent = signInClient.signInIntent
                                gmailSignInLauncher.launch(signInIntent)
                            }
                        } catch (e: Exception) {
                            Log.e("WorkflowManager", "Failed to start Gmail sign-in", e)
                            errorMessage = "Failed to start Gmail sign-in: ${e.message}"
                        }
                    }
                },
                onSignOut = {
                    scope.launch {
                        gmailService.signOut()
                        gmailSignedIn = false
                        gmailAccount = null
                        emails = emptyList()
                        errorMessage = null
                        filterKeywords = ""
                        filterType = "all"
                        
                        android.widget.Toast.makeText(
                            context,
                            "Gmail signed out successfully",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onRefreshEmails = {
                    scope.launch {
                        isLoadingEmails = true
                        try {
                            val count = emailCount.toIntOrNull() ?: 5
                            
                            // Create filter condition based on user input
                            val condition = if (filterKeywords.isBlank()) {
                                GmailIntegrationService.EmailCondition(isUnreadOnly = false)
                            } else {
                                when (filterType) {
                                    "subject" -> GmailIntegrationService.EmailCondition(
                                        subjectFilter = filterKeywords,
                                        isUnreadOnly = false
                                    )
                                    "from" -> GmailIntegrationService.EmailCondition(
                                        fromFilter = filterKeywords,
                                        isUnreadOnly = false
                                    )
                                    "body" -> GmailIntegrationService.EmailCondition(
                                        bodyFilter = filterKeywords,
                                        isUnreadOnly = false
                                    )
                                    else -> GmailIntegrationService.EmailCondition(
                                        bodyFilter = filterKeywords, // "all" searches in body by default
                                        isUnreadOnly = false
                                    )
                                }
                            }
                            
                            val result = gmailService.checkForNewEmails(condition, count)
                            result.fold(
                                onSuccess = { fetchedEmails ->
                                    emails = fetchedEmails
                                    errorMessage = null
                                },
                                onFailure = { error ->
                                    when (error) {
                                        is AuthConsentRequiredException -> {
                                            errorMessage = "Please sign in again to grant Gmail permissions"
                                            // Re-trigger sign-in flow
                                            scope.launch {
                                                gmailService.signOut()
                                                gmailSignedIn = false
                                                gmailAccount = null
                                            }
                                        }
                                        else -> errorMessage = error.message ?: "Failed to load emails"
                                    }
                                }
                            )
                        } catch (e: Exception) {
                            errorMessage = "Error loading emails: ${e.message}"
                        } finally {
                            isLoadingEmails = false
                        }
                    }
                }
            )
            2 -> TelegramBotDynamicScreen()
            3 -> SettingsTab(
                gmailSignedIn = gmailSignedIn,
                gmailAccount = gmailAccount,
                onChangeGmail = {
                    scope.launch {
                        // Sign out current account
                        gmailService.signOut()
                        gmailSignedIn = false
                        gmailAccount = null
                        emails = emptyList()
                        errorMessage = null
                        filterKeywords = ""
                        filterType = "all"
                        
                        // Start new sign-in
                        try {
                            val signInClient = gmailService.getSignInClient()
                            if (signInClient != null) {
                                val signInIntent = signInClient.signInIntent
                                gmailSignInLauncher.launch(signInIntent)
                            }
                        } catch (e: Exception) {
                            errorMessage = "Failed to change Gmail account: ${e.message}"
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun WorkflowsTab() {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Welcome Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "⚙️ Workflow Automation",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create and manage automated workflows for your AI tasks with Gmail integration",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        item {
            Text(
                text = "Available Workflows",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        // Real workflows from repository  
        item {
            WorkflowsList()
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Create New Workflow Button
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Create New Workflow",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create automated workflows between Gmail and Telegram with AI assistance",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    ModernSpacing()
                    ModernPrimaryButton(
                        text = "✨ Create Workflow",
                        onClick = { 
                            // Launch dynamic workflow creation screen
                            val intent = Intent(context, DynamicWorkflowBuilderActivity::class.java)
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GmailTab(
    gmailService: GmailIntegrationService,
    gmailSignedIn: Boolean,
    gmailAccount: String?,
    emails: List<GmailIntegrationService.EmailMessage>,
    isLoadingEmails: Boolean,
    errorMessage: String?,
    emailCount: String,
    filterKeywords: String,
    filterType: String,
    onEmailCountChanged: (String) -> Unit,
    onFilterKeywordsChanged: (String) -> Unit,
    onFilterTypeChanged: (String) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRefreshEmails: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Gmail Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (gmailSignedIn) 
                        Color.Green.copy(alpha = 0.1f) 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gmail Integration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (gmailSignedIn && gmailAccount != null) {
                                Text(
                                    text = "✅ Signed in as: $gmailAccount",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Green
                                )
                            } else {
                                Text(
                                    text = "❌ Not signed in",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Red
                                )
                            }
                        }
                        
                        if (gmailSignedIn) {
                            ModernDangerButton(
                                text = "🚪 Sign Out",
                                onClick = onSignOut
                            )
                        } else {
                            ModernSuccessButton(
                                text = "🔐 Sign In to Gmail",
                                onClick = onSignIn
                            )
                        }
                    }
                }
            }
        }
        
        if (gmailSignedIn) {
            item {
                // Email Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Emails",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Button(
                        onClick = onRefreshEmails,
                        enabled = !isLoadingEmails
                    ) {
                        if (isLoadingEmails) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refresh")
                    }
                }
            }
            
            item {
                // Email count input
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Number of emails to fetch:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        
                        OutlinedTextField(
                            value = emailCount,
                            onValueChange = { newValue ->
                                // Only allow numeric input
                                if (newValue.all { it.isDigit() } && newValue.length <= 3) {
                                    onEmailCountChanged(newValue)
                                }
                            },
                            label = { Text("Count") },
                            singleLine = true,
                            modifier = Modifier.width(100.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            )
                        )
                    }
                }
            }
            
            item {
                // Filter controls
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Email Filter",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Filter type dropdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Filter by:",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            
                            var expanded by remember { mutableStateOf(false) }
                            val filterOptions = listOf(
                                "all" to "All (Body)",
                                "subject" to "Subject",
                                "from" to "From",
                                "body" to "Body"
                            )
                            
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded },
                                modifier = Modifier.width(150.dp)
                            ) {
                                OutlinedTextField(
                                    value = filterOptions.find { it.first == filterType }?.second ?: "All (Body)",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    modifier = Modifier.menuAnchor(),
                                    singleLine = true
                                )
                                
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    filterOptions.forEach { (value, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                onFilterTypeChanged(value)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Keywords input
                        OutlinedTextField(
                            value = filterKeywords,
                            onValueChange = onFilterKeywordsChanged,
                            label = { Text("Keywords") },
                            placeholder = { Text("Enter keywords to filter emails...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (filterKeywords.isNotEmpty()) {
                                    IconButton(onClick = { onFilterKeywordsChanged("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                        
                        if (filterKeywords.isNotEmpty()) {
                            val filterOptions = listOf(
                                "all" to "All (Body)",
                                "subject" to "Subject",
                                "from" to "From",
                                "body" to "Body"
                            )
                            Text(
                                text = "🔍 Filtering emails by ${filterOptions.find { it.first == filterType }?.second?.lowercase()} containing: \"$filterKeywords\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            
            if (errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Red.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "⚠️ Error",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Red,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage,
                                color = Color.Red,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (errorMessage.contains("Gmail API is not enabled")) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "💡 Next steps:\n1. Visit Google Cloud Console\n2. Enable Gmail API for your project\n3. Wait 2-3 minutes\n4. Try again",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            if (emails.isEmpty() && !isLoadingEmails) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No emails found",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Tap refresh to load your recent emails",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(emails) { email ->
                    EmailCard(email = email)
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    gmailSignedIn: Boolean,
    gmailAccount: String?,
    onChangeGmail: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Gmail Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        // Gmail Account Settings
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Account Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                if (gmailSignedIn && gmailAccount != null) {
                    Text(
                        text = "Current account: $gmailAccount",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onChangeGmail,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Change Gmail Account")
                    }
                } else {
                    Text(
                        text = "No Gmail account connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Sign in to Gmail from the Gmail tab to manage your account",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Workflow Settings
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Workflow Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Configure automatic email processing and AI analysis settings",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { /* Open workflow settings */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configure Workflows")
                }
            }
        }
    }
}

@Composable
private fun EmailCard(email: GmailIntegrationService.EmailMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = email.subject,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (!email.isRead) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = "From: ${email.from}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = email.body.take(100) + if (email.body.length > 100) "..." else "",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                if (!email.isRead) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(8.dp)
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun WorkflowsList() {
    val context = LocalContext.current
    val workflowRepository = remember { AppContainer.provideWorkflowRepository(context) }
    val userManager = remember { AppContainer.provideUserManager(context) }
    var workflows by remember { mutableStateOf<List<Workflow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // Function to refresh workflows
    val refreshWorkflows = {
        isLoading = true
        refreshTrigger += 1
    }
    
    LaunchedEffect(refreshTrigger) {
        try {
            // Use default user ID for demo - in real app this would come from authentication
            val currentUserId = "user_1"
            
            workflowRepository.getAllWorkflows().fold(
                onSuccess = { allWorkflows ->
                    workflows = allWorkflows
                    isLoading = false
                    android.util.Log.d("WorkflowsList", "Loaded ${allWorkflows.size} workflows")
                },
                onFailure = { error ->
                    android.util.Log.e("WorkflowsList", "Error loading workflows: ${error.message}")
                    isLoading = false
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("WorkflowsList", "Exception loading workflows", e)
            isLoading = false
        }
    }
    
    // Refresh when activity resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                android.util.Log.d("WorkflowsList", "Activity resumed, refreshing workflows")
                refreshWorkflows()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (workflows.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🤖 No workflows created yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Create your first workflow to automate tasks between Gmail and Telegram!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    // Launch dynamic workflow creation screen
                    val intent = Intent(context, DynamicWorkflowBuilderActivity::class.java)
                    context.startActivity(intent)
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create New Workflow")
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            workflows.forEach { workflow ->
                WorkflowCard(
                    workflow = workflow,
                    onToggle = { enabled ->
                        android.util.Log.d("WorkflowCard", "Toggle workflow ${workflow.name}: $enabled")
                        // Update workflow enabled state
                        if (workflow is MultiUserWorkflow) {
                            val updatedWorkflow = workflow.copy(isEnabled = enabled)
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                workflowRepository.updateWorkflow(updatedWorkflow)
                                // Refresh workflows list
                                workflowRepository.getAllWorkflows().onSuccess { updatedWorkflows ->
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                        workflows = updatedWorkflows
                                    }
                                }
                            }
                        }
                    },
                    onEdit = {
                        android.util.Log.d("WorkflowCard", "Edit workflow: ${workflow.name}")
                        // Navigate to workflow editor
                        val intent = Intent(context, DynamicWorkflowBuilderActivity::class.java).apply {
                            putExtra("workflow_id", workflow.id)
                            putExtra("edit_mode", true)
                        }
                        context.startActivity(intent)
                    },
                    onRun = {
                        android.util.Log.d("WorkflowCard", "Run workflow: ${workflow.name}")
                        // Run workflow manually
                        val workflowEngine = AppContainer.provideWorkflowEngine(context)
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                android.widget.Toast.makeText(
                                    context,
                                    "▶️ Running workflow: ${workflow.name}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                
                                val result = workflowEngine.executeWorkflow(
                                    workflowId = workflow.id,
                                    triggerUserId = "user_1",
                                    triggerData = mapOf("manual_trigger" to true)
                                )
                                result.fold(
                                    onSuccess = { executionResult ->
                                        android.util.Log.i("WorkflowCard", "Workflow executed successfully: ${executionResult.message}")
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                            android.widget.Toast.makeText(
                                                context,
                                                "✅ Workflow executed successfully!",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    onFailure = { error ->
                                        android.util.Log.e("WorkflowCard", "Workflow execution failed: ${error.message}")
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                            android.widget.Toast.makeText(
                                                context,
                                                "❌ Workflow failed: ${error.message}",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("WorkflowCard", "Error executing workflow", e)
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    android.widget.Toast.makeText(
                                        context,
                                        "❌ Error: ${e.message}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    },
                    onDelete = {
                        android.util.Log.d("WorkflowCard", "Delete workflow: ${workflow.name}")
                        // Delete workflow
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            workflowRepository.deleteWorkflow(workflow.id)
                            // Refresh workflows list
                            workflowRepository.getAllWorkflows().onSuccess { updatedWorkflows ->
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    workflows = updatedWorkflows
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun WorkflowCard(
    workflow: Workflow,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userManager = remember { AppContainer.provideUserManager(context) }
    var showShareDialog by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workflow.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = workflow.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = workflow.isEnabled,
                    onCheckedChange = onToggle
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary Run button - most used action
                Button(
                    onClick = onRun,
                    enabled = workflow.isEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run Workflow")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Dropdown menu for other actions
                Box {
                    var expanded by remember { mutableStateOf(false) }
                    
                    IconButton(
                        onClick = { expanded = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Share Workflow")
                                }
                            },
                            onClick = {
                                showShareDialog = true
                                expanded = false
                            }
                        )
                        
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Check Gmail Now")
                                }
                            },
                            onClick = {
                                // Manually check Gmail for this workflow
                                android.util.Log.d("WorkflowCard", "Manual Gmail check for workflow: ${workflow.name}")
                                val triggerManager = AppContainer.provideTriggerManager(context)
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    try {
                                        android.widget.Toast.makeText(
                                            context,
                                            "🔍 Checking Gmail for new emails...",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        
                                        val result = triggerManager.checkTriggers()
                                        result.fold(
                                            onSuccess = { results ->
                                                val triggeredCount = results.count { it.triggered }
                                                val workflowResult = results.find { it.workflowId == workflow.id }
                                                
                                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                                    if (triggeredCount > 0) {
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "✅ Found new emails! Workflow triggered.",
                                                            android.widget.Toast.LENGTH_LONG
                                                        ).show()
                                                    } else {
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "📧 No new emails found. Result: ${workflowResult?.message ?: "No result"}",
                                                            android.widget.Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                }
                                            },
                                            onFailure = { error ->
                                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "❌ Gmail check failed: ${error.message}",
                                                        android.widget.Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        )
                                    } catch (e: Exception) {
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                            android.widget.Toast.makeText(
                                                context,
                                                "❌ Error: ${e.message}",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                                expanded = false
                            }
                        )
                        
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Test with Fake Email")
                                }
                            },
                            onClick = {
                                // Test workflow with fake email data
                                android.util.Log.d("WorkflowCard", "Testing workflow with fake email: ${workflow.name}")
                                val workflowEngine = AppContainer.provideWorkflowEngine(context)
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    try {
                                        android.widget.Toast.makeText(
                                            context,
                                            "🧪 Testing workflow with fake email...",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        
                                        val fakeEmailData = mapOf(
                                            "email_from" to "test@example.com",
                                            "email_subject" to "Test Email for Workflow",
                                            "email_body" to "This is a test email to trigger your autoreply workflow. Please reply to this message.",
                                            "trigger_email_id" to "fake_email_${System.currentTimeMillis()}",
                                            "test_mode" to true
                                        )
                                        
                                        val result = workflowEngine.executeWorkflow(
                                            workflowId = workflow.id,
                                            triggerUserId = "user_1",
                                            triggerData = fakeEmailData
                                        )
                                        result.fold(
                                            onSuccess = { executionResult ->
                                                android.util.Log.i("WorkflowCard", "Test executed successfully: ${executionResult.message}")
                                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "✅ Test successful! Check logs for details.",
                                                        android.widget.Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            },
                                            onFailure = { error ->
                                                android.util.Log.e("WorkflowCard", "Test failed: ${error.message}")
                                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "❌ Test failed: ${error.message}",
                                                        android.widget.Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        )
                                    } catch (e: Exception) {
                                        android.util.Log.e("WorkflowCard", "Test error", e)
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                            android.widget.Toast.makeText(
                                                context,
                                                "❌ Test error: ${e.message}",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                                expanded = false
                            }
                        )
                        
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Edit Workflow")
                                }
                            },
                            onClick = {
                                onEdit()
                                expanded = false
                            }
                        )
                        
                        androidx.compose.material3.HorizontalDivider()
                        
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Delete, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Delete Workflow",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            onClick = {
                                onDelete()
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Workflow Sharing Dialog
    if (showShareDialog) {
        WorkflowSharingDialog(
            workflow = workflow,
            userManager = userManager,
            onDismiss = { showShareDialog = false },
            onShare = { targetUsers, permissions ->
                scope.launch {
                    // Share workflow with selected users
                    shareWorkflow(workflow, targetUsers, permissions, userManager)
                    showShareDialog = false
                }
            }
        )
    }
}

@Composable
fun WorkflowSharingDialog(
    workflow: Workflow,
    userManager: com.localllm.myapplication.service.UserManager,
    onDismiss: () -> Unit,
    onShare: (List<WorkflowUser>, Set<Permission>) -> Unit
) {
    var selectedUsers by remember { mutableStateOf<List<WorkflowUser>>(emptyList()) }
    var availableUsers by remember { mutableStateOf<List<WorkflowUser>>(emptyList()) }
    var selectedPermissions by remember { mutableStateOf(setOf(Permission.VIEW_WORKFLOW)) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load available users
    LaunchedEffect(Unit) {
        userManager.getAllUsers().fold(
            onSuccess = { users ->
                availableUsers = users.filter { user ->
                    user.id != userManager.getCurrentUserId()
                }
                isLoading = false
            },
            onFailure = {
                isLoading = false
            }
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Share Workflow: ${workflow.name}")
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "Select users to share with:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                } else if (availableUsers.isEmpty()) {
                    item {
                        Text(
                            text = "No users available to share with or workflow is already shared with all users.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(availableUsers) { user ->
                        val isSelected = selectedUsers.contains(user)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedUsers = if (isSelected) {
                                        selectedUsers - user
                                    } else {
                                        selectedUsers + user
                                    }
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedUsers = if (checked) {
                                        selectedUsers + user
                                    } else {
                                        selectedUsers - user
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = user.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (user.email != null) {
                                    Text(
                                        text = user.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (selectedUsers.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Permissions:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    item {
                        PermissionSelector(
                            selectedPermissions = selectedPermissions,
                            onPermissionsChanged = { selectedPermissions = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedUsers.isNotEmpty()) {
                        onShare(selectedUsers, selectedPermissions)
                    }
                },
                enabled = selectedUsers.isNotEmpty()
            ) {
                Text("Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PermissionSelector(
    selectedPermissions: Set<Permission>,
    onPermissionsChanged: (Set<Permission>) -> Unit
) {
    val availablePermissions = listOf(
        Permission.VIEW_WORKFLOW to "View workflow",
        Permission.EDIT_WORKFLOW to "Edit workflow", 
        Permission.CREATE_WORKFLOW to "Run workflow",
        Permission.DELETE_WORKFLOW to "Delete workflow"
    )
    
    Column {
        availablePermissions.forEach { (permission, description) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onPermissionsChanged(
                            if (selectedPermissions.contains(permission)) {
                                selectedPermissions - permission
                            } else {
                                selectedPermissions + permission
                            }
                        )
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = selectedPermissions.contains(permission),
                    onCheckedChange = { checked ->
                        onPermissionsChanged(
                            if (checked) {
                                selectedPermissions + permission
                            } else {
                                selectedPermissions - permission
                            }
                        )
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

suspend fun shareWorkflow(
    workflow: Workflow,
    targetUsers: List<WorkflowUser>,
    permissions: Set<Permission>,
    userManager: com.localllm.myapplication.service.UserManager
) {
    try {
        // Implementation would depend on your workflow repository's sharing capabilities
        // For now, this is a placeholder that demonstrates the structure
        
        val currentUserId = userManager.getCurrentUserId() ?: return
        
        // Create sharing request
        val sharingRequest = WorkflowSharingRequest(
            workflowId = workflow.id,
            sharedBy = currentUserId,
            targetUserIds = targetUsers.map { it.id },
            permissions = permissions,
            message = "Shared workflow: ${workflow.name}"
        )
        
        // Here you would call your repository's sharing method
        // workflowRepository.shareWorkflow(sharingRequest)
        
        // For now, just log the sharing action
        android.util.Log.d("WorkflowSharing", "Sharing workflow ${workflow.name} with ${targetUsers.size} users")
        
    } catch (e: Exception) {
        android.util.Log.e("WorkflowSharing", "Failed to share workflow", e)
    }
}

data class WorkflowSharingRequest(
    val workflowId: String,
    val sharedBy: String,
    val targetUserIds: List<String>,
    val permissions: Set<Permission>,
    val message: String
)