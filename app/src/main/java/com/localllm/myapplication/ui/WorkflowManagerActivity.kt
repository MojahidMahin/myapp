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
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localllm.myapplication.service.integration.GmailIntegrationService
import com.localllm.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

/**
 * Activity for managing workflows with Gmail integration
 */
class WorkflowManagerActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
    
    // Gmail integration service
    val gmailService = remember { GmailIntegrationService(context) }
    
    // State management
    var selectedTab by remember { mutableStateOf(0) }
    var gmailSignedIn by remember { mutableStateOf(false) }
    var gmailAccount by remember { mutableStateOf<String?>(null) }
    var emails by remember { mutableStateOf<List<GmailIntegrationService.EmailMessage>>(emptyList()) }
    var isLoadingEmails by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Gmail sign-in launcher
    val gmailSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            // Handle Gmail sign-in result
            scope.launch {
                try {
                    // This would normally parse the result and sign in
                    // For now, we'll simulate successful sign-in
                    gmailSignedIn = true
                    gmailAccount = "user@gmail.com" // This should come from actual sign-in
                    errorMessage = null
                    
                    android.widget.Toast.makeText(
                        context,
                        "✅ Gmail signed in successfully!",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Log.e("WorkflowManager", "Gmail sign-in failed", e)
                    errorMessage = "Gmail sign-in failed: ${e.message}"
                }
            }
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
    
    val tabs = listOf("Workflows", "Gmail", "Settings")
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Workflow Manager") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                            val condition = GmailIntegrationService.EmailCondition(isUnreadOnly = true)
                            val result = gmailService.checkForNewEmails(condition, 20)
                            result.fold(
                                onSuccess = { emailList ->
                                    emails = emailList
                                    errorMessage = null
                                },
                                onFailure = { error ->
                                    errorMessage = "Failed to load emails: ${error.message}"
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
            2 -> SettingsTab(
                gmailSignedIn = gmailSignedIn,
                gmailAccount = gmailAccount,
                onChangeGmail = {
                    scope.launch {
                        // Sign out current account
                        gmailService.signOut()
                        gmailSignedIn = false
                        gmailAccount = null
                        emails = emptyList()
                        
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
        
        // Sample workflow items
        items(3) { index ->
            WorkflowCard(
                title = when(index) {
                    0 -> "Gmail → AI Analysis"
                    1 -> "Email Processing Pipeline"
                    else -> "Smart Email Automation"
                },
                description = when(index) {
                    0 -> "Automatically analyze incoming emails with AI"
                    1 -> "Process emails through multiple AI models"
                    else -> "Advanced email automation with AI responses"
                },
                isEnabled = index != 2,
                onToggle = { /* Toggle workflow */ },
                onEdit = { /* Edit workflow */ },
                onRun = { /* Run workflow */ }
            )
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
                        text = "Set up automated Gmail processing workflows",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { /* Create workflow */ }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Workflow")
                    }
                }
            }
        }
    }
}

@Composable
private fun GmailTab(
    gmailService: GmailIntegrationService,
    gmailSignedIn: Boolean,
    gmailAccount: String?,
    emails: List<GmailIntegrationService.EmailMessage>,
    isLoadingEmails: Boolean,
    errorMessage: String?,
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
                            OutlinedButton(
                                onClick = onSignOut,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.Red
                                )
                            ) {
                                Text("Sign Out")
                            }
                        } else {
                            Button(onClick = onSignIn) {
                                Text("Sign In")
                            }
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
            
            if (errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Red.copy(alpha = 0.1f)
                        )
                    ) {
                        Text(
                            text = "⚠️ $errorMessage",
                            modifier = Modifier.padding(16.dp),
                            color = Color.Red,
                            style = MaterialTheme.typography.bodyMedium
                        )
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
private fun WorkflowCard(
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onRun: () -> Unit
) {
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
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggle() }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRun,
                    modifier = Modifier.weight(1f),
                    enabled = isEnabled
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Run")
                }
                
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
            }
        }
    }
}