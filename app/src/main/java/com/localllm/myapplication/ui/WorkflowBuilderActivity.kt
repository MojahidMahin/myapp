package com.localllm.myapplication.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localllm.myapplication.data.*
import com.localllm.myapplication.di.AppContainer
import com.localllm.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.*

/**
 * Activity for creating new workflows
 * Provides templates and custom workflow creation
 */
class WorkflowBuilderActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MyApplicationTheme {
                WorkflowBuilderScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowBuilderScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val userManager = remember { AppContainer.provideUserManager(context) }
    val workflowRepository = remember { AppContainer.provideWorkflowRepository(context) }
    val workflowEngine = remember { AppContainer.provideWorkflowEngine(context) }
    
    var selectedTab by remember { mutableStateOf(0) }
    var showTemplateDetails by remember { mutableStateOf<WorkflowTemplate?>(null) }
    var isCreatingWorkflow by remember { mutableStateOf(false) }
    
    val templates = remember { WorkflowTemplates.getAllTemplates() }
    
    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            // Top App Bar
            TopAppBar(
                title = { Text("Create Workflow") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
            
            // Tab Row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Templates") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Custom") }
                )
            }
            
            // Content
            when (selectedTab) {
                0 -> TemplatesTab(
                    templates = templates,
                    onTemplateSelected = { template ->
                        showTemplateDetails = template
                    },
                    isCreating = isCreatingWorkflow,
                    onCreateFromTemplate = { template ->
                        isCreatingWorkflow = true
                        // Create workflow from template
                        createWorkflowFromTemplate(
                            template = template,
                            userManager = userManager,
                            workflowRepository = workflowRepository,
                            onComplete = {
                                isCreatingWorkflow = false
                                onBackPressed()
                            }
                        )
                    }
                )
                1 -> CustomWorkflowTab()
            }
        }
    }
    
    // Template Details Dialog
    showTemplateDetails?.let { template ->
        TemplateDetailsDialog(
            template = template,
            onDismiss = { showTemplateDetails = null },
            onUseTemplate = { 
                showTemplateDetails = null
                isCreatingWorkflow = true
                createWorkflowFromTemplate(
                    template = template,
                    userManager = userManager,
                    workflowRepository = workflowRepository,
                    onComplete = {
                        isCreatingWorkflow = false
                        onBackPressed()
                    }
                )
            }
        )
    }
}

@Composable
fun TemplatesTab(
    templates: List<WorkflowTemplate>,
    onTemplateSelected: (WorkflowTemplate) -> Unit,
    isCreating: Boolean,
    onCreateFromTemplate: (WorkflowTemplate) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Choose a template to get started quickly",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        items(templates) { template ->
            TemplateCard(
                template = template,
                onClick = { onTemplateSelected(template) },
                onQuickCreate = { onCreateFromTemplate(template) },
                isCreating = isCreating
            )
        }
    }
}

@Composable
fun TemplateCard(
    template: WorkflowTemplate,
    onClick: () -> Unit,
    onQuickCreate: () -> Unit,
    isCreating: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
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
                        text = template.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = template.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Category: ${template.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Button(
                    onClick = onQuickCreate,
                    enabled = !isCreating,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Use")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Platform icons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                template.platforms.forEach { platform ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = platform.name,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Setup time: ${template.estimatedSetupTime}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TemplateDetailsDialog(
    template: WorkflowTemplate,
    onDismiss: () -> Unit,
    onUseTemplate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(template.name) },
        text = {
            Column {
                Text(template.description)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Details:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("• Category: ${template.category}")
                Text("• Required users: ${template.requiredUsers}")
                if (template.optionalUsers > 0) {
                    Text("• Optional users: up to ${template.optionalUsers}")
                }
                Text("• Setup time: ${template.estimatedSetupTime}")
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("Platforms:", fontWeight = FontWeight.Bold)
                template.platforms.forEach { platform ->
                    Text("• ${platform.name}")
                }
                
                if (template.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tags: ${template.tags.joinToString(", ")}")
                }
            }
        },
        confirmButton = {
            Button(onClick = onUseTemplate) {
                Text("Use Template")
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
fun CustomWorkflowTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🚧 Custom Workflow Builder",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Custom workflow creation is coming soon!\n\nFor now, you can use our pre-built templates to create powerful automation workflows.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "💡 Templates cover 90% of common use cases",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Create workflow from template
 */
private fun createWorkflowFromTemplate(
    template: WorkflowTemplate,
    userManager: com.localllm.myapplication.service.UserManager,
    workflowRepository: WorkflowRepository,
    onComplete: () -> Unit
) {
    // For now, create a basic workflow - in a real implementation,
    // you'd want to show a user selection dialog for multi-user templates
    val currentUserId = userManager.getCurrentUserId()
    if (currentUserId == null) {
        // User not signed in
        onComplete()
        return
    }
    
    // Create workflow from template
    val workflow = WorkflowTemplates.createFromTemplate(
        templateId = template.id,
        creatorUserId = currentUserId,
        targetUserIds = emptyList() // For simplicity, using empty list for now
    )
    
    if (workflow != null) {
        // Save workflow
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val result = workflowRepository.saveWorkflow(workflow)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete()
            }
        }
    } else {
        onComplete()
    }
}