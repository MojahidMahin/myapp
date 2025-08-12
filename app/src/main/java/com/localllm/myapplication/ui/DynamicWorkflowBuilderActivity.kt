package com.localllm.myapplication.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localllm.myapplication.data.*
import com.localllm.myapplication.di.AppContainer
import com.localllm.myapplication.service.integration.GmailIntegrationService
import com.localllm.myapplication.service.integration.TelegramBotService
import com.localllm.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.util.*

/**
 * Fully dynamic workflow builder that allows users to create any workflow combination
 */
class DynamicWorkflowBuilderActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MyApplicationTheme {
                DynamicWorkflowBuilderScreen(
                    onBackPressed = { finish() },
                    onWorkflowCreated = { 
                        // Show success message and go back
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicWorkflowBuilderScreen(
    onBackPressed: () -> Unit,
    onWorkflowCreated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userManager = remember { AppContainer.provideUserManager(context) }
    val workflowRepository = remember { AppContainer.provideWorkflowRepository() }
    val workflowEngine = remember { AppContainer.provideWorkflowEngine(context) }
    
    // Workflow building state
    var workflowName by remember { mutableStateOf("") }
    var workflowDescription by remember { mutableStateOf("") }
    var selectedTrigger by remember { mutableStateOf<TriggerConfig?>(null) }
    var selectedActions by remember { mutableStateOf<List<ActionConfig>>(emptyList()) }
    var selectedUsers by remember { mutableStateOf<List<WorkflowUser>>(emptyList()) }
    var showUserPicker by remember { mutableStateOf(false) }
    var showTriggerPicker by remember { mutableStateOf(false) }
    var showActionPicker by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var currentStep by remember { mutableStateOf(BuildStep.TRIGGER) }
    
    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            // Top App Bar
            TopAppBar(
                title = { Text("Create Dynamic Workflow") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Save as Template button
                    if (canCreateWorkflow(workflowName, selectedTrigger, selectedActions)) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    saveAsTemplate(
                                        name = workflowName,
                                        description = workflowDescription,
                                        trigger = selectedTrigger!!,
                                        actions = selectedActions,
                                        users = selectedUsers
                                    )
                                }
                            }
                        ) {
                            Text("SAVE AS TEMPLATE")
                        }
                    }
                    
                    // Create workflow button
                    TextButton(
                        onClick = {
                            if (canCreateWorkflow(workflowName, selectedTrigger, selectedActions)) {
                                scope.launch {
                                    isCreating = true
                                    createDynamicWorkflow(
                                        name = workflowName,
                                        description = workflowDescription,
                                        trigger = selectedTrigger!!,
                                        actions = selectedActions,
                                        users = selectedUsers,
                                        userManager = userManager,
                                        workflowRepository = workflowRepository,
                                        onSuccess = {
                                            isCreating = false
                                            onWorkflowCreated()
                                        },
                                        onError = { 
                                            isCreating = false
                                        }
                                    )
                                }
                            }
                        },
                        enabled = canCreateWorkflow(workflowName, selectedTrigger, selectedActions) && !isCreating
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("CREATE")
                        }
                    }
                }
            )
            
            // Step indicator
            StepIndicator(
                currentStep = currentStep,
                completedSteps = getCompletedSteps(workflowName, selectedTrigger, selectedActions)
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Workflow Info
                item {
                    WorkflowInfoCard(
                        name = workflowName,
                        description = workflowDescription,
                        onNameChange = { workflowName = it },
                        onDescriptionChange = { workflowDescription = it },
                        onStepFocus = { currentStep = BuildStep.INFO }
                    )
                }
                
                // Trigger Selection
                item {
                    TriggerSelectionCard(
                        selectedTrigger = selectedTrigger,
                        onSelectTrigger = { showTriggerPicker = true },
                        onStepFocus = { currentStep = BuildStep.TRIGGER }
                    )
                }
                
                // Actions List
                item {
                    ActionsListCard(
                        actions = selectedActions,
                        onAddAction = { showActionPicker = true },
                        onRemoveAction = { action ->
                            selectedActions = selectedActions - action
                        },
                        onMoveUp = { index ->
                            if (index > 0) {
                                selectedActions = selectedActions.toMutableList().apply {
                                    val item = removeAt(index)
                                    add(index - 1, item)
                                }
                            }
                        },
                        onMoveDown = { index ->
                            if (index < selectedActions.size - 1) {
                                selectedActions = selectedActions.toMutableList().apply {
                                    val item = removeAt(index)
                                    add(index + 1, item)
                                }
                            }
                        },
                        onStepFocus = { currentStep = BuildStep.ACTIONS }
                    )
                }
                
                // Users Selection (for multi-user workflows)
                item {
                    UsersSelectionCard(
                        selectedUsers = selectedUsers,
                        onSelectUsers = { showUserPicker = true },
                        onStepFocus = { currentStep = BuildStep.USERS }
                    )
                }
                
                // Workflow Preview
                item {
                    WorkflowPreviewCard(
                        name = workflowName,
                        trigger = selectedTrigger,
                        actions = selectedActions,
                        users = selectedUsers
                    )
                }
                
                // Test Workflow
                if (canCreateWorkflow(workflowName, selectedTrigger, selectedActions)) {
                    item {
                        TestWorkflowCard(
                            trigger = selectedTrigger,
                            actions = selectedActions,
                            users = selectedUsers,
                            userManager = userManager,
                            workflowEngine = workflowEngine
                        )
                    }
                }
            }
        }
    }
    
    // Dialogs
    if (showTriggerPicker) {
        TriggerPickerDialog(
            onDismiss = { showTriggerPicker = false },
            onTriggerSelected = { trigger ->
                selectedTrigger = trigger
                showTriggerPicker = false
                currentStep = BuildStep.ACTIONS
            }
        )
    }
    
    if (showActionPicker) {
        ActionPickerDialog(
            onDismiss = { showActionPicker = false },
            onActionSelected = { action ->
                selectedActions = selectedActions + action
                showActionPicker = false
            }
        )
    }
    
    if (showUserPicker) {
        UserPickerDialog(
            selectedUsers = selectedUsers,
            onDismiss = { showUserPicker = false },
            onUsersSelected = { users ->
                selectedUsers = users
                showUserPicker = false
            }
        )
    }
}

enum class BuildStep {
    INFO, TRIGGER, ACTIONS, USERS, PREVIEW
}

@Composable
fun StepIndicator(
    currentStep: BuildStep,
    completedSteps: Set<BuildStep>
) {
    val steps = listOf(
        BuildStep.INFO to "Info",
        BuildStep.TRIGGER to "Trigger", 
        BuildStep.ACTIONS to "Actions",
        BuildStep.USERS to "Users",
        BuildStep.PREVIEW to "Preview"
    )
    
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(steps) { (step, label) ->
            val isActive = step == currentStep
            val isCompleted = completedSteps.contains(step)
            
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = when {
                    isActive -> MaterialTheme.colorScheme.primary
                    isCompleted -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = when {
                        isActive -> MaterialTheme.colorScheme.onPrimary
                        isCompleted -> MaterialTheme.colorScheme.onSecondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun WorkflowInfoCard(
    name: String,
    description: String,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onStepFocus: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onStepFocus() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "1. Workflow Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Workflow Name") },
                placeholder = { Text("e.g., Email to Telegram Alerts") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text("Description (Optional)") },
                placeholder = { Text("Describe what this workflow does...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }
    }
}

@Composable
fun TriggerSelectionCard(
    selectedTrigger: TriggerConfig?,
    onSelectTrigger: () -> Unit,
    onStepFocus: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onStepFocus() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "2. Choose Trigger (When to run)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            if (selectedTrigger != null) {
                TriggerPreview(trigger = selectedTrigger)
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    OutlinedButton(
                        onClick = onSelectTrigger,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Change Trigger")
                    }
                }
            } else {
                Button(
                    onClick = onSelectTrigger,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select When This Workflow Should Run")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 Examples: New Gmail email, Telegram message, scheduled time, manual trigger",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ActionsListCard(
    actions: List<ActionConfig>,
    onAddAction: () -> Unit,
    onRemoveAction: (ActionConfig) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onStepFocus: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onStepFocus() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "3. Actions (What to do)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onAddAction) {
                    Icon(Icons.Default.Add, contentDescription = "Add Action")
                }
            }
            
            if (actions.isEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onAddAction,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add First Action")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 Examples: Send email, send Telegram message, AI analysis, translate text",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                actions.forEachIndexed { index, action ->
                    ActionPreview(
                        action = action,
                        index = index,
                        onRemove = { onRemoveAction(action) },
                        onMoveUp = { onMoveUp(index) },
                        onMoveDown = { onMoveDown(index) },
                        canMoveUp = index > 0,
                        canMoveDown = index < actions.size - 1
                    )
                    if (index < actions.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onAddAction,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Another Action")
                }
            }
        }
    }
}

@Composable
fun UsersSelectionCard(
    selectedUsers: List<WorkflowUser>,
    onSelectUsers: () -> Unit,
    onStepFocus: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onStepFocus() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "4. Users (Who's involved)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            if (selectedUsers.isEmpty()) {
                Button(
                    onClick = onSelectUsers,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Team Members (Optional)")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 Add other users to create collaborative workflows",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                selectedUsers.forEach { user ->
                    UserChip(user = user)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSelectUsers,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Users")
                }
            }
        }
    }
}

@Composable
fun WorkflowPreviewCard(
    name: String,
    trigger: TriggerConfig?,
    actions: List<ActionConfig>,
    users: List<WorkflowUser>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "5. Workflow Preview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            if (name.isNotBlank() && trigger != null && actions.isNotEmpty()) {
                Text(
                    text = "\"$name\"",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "WHEN: ${trigger.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                Text("THEN:", style = MaterialTheme.typography.bodyMedium)
                actions.forEachIndexed { index, action ->
                    Text(
                        text = "${index + 1}. ${action.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                
                if (users.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "USERS: ${users.map { it.displayName }.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Complete the steps above to see your workflow preview",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Data classes for dynamic workflow building
data class TriggerConfig(
    val type: TriggerType,
    val displayName: String,
    val config: Map<String, String> = emptyMap()
)

data class ActionConfig(
    val type: ActionType,
    val displayName: String,
    val config: Map<String, String> = emptyMap()
)

enum class TriggerType {
    GMAIL_NEW_EMAIL,
    GMAIL_EMAIL_FROM,
    GMAIL_EMAIL_SUBJECT,
    TELEGRAM_NEW_MESSAGE,
    TELEGRAM_COMMAND,
    TELEGRAM_FROM_USER,
    SCHEDULED_TIME,
    MANUAL_TRIGGER
}

enum class ActionType {
    SEND_GMAIL,
    REPLY_GMAIL,
    SEND_TELEGRAM,
    REPLY_TELEGRAM,
    AI_ANALYZE,
    AI_SUMMARIZE,
    AI_TRANSLATE,
    AI_GENERATE_REPLY,
    DELAY,
    CONDITIONAL
}

// Utility functions
fun canCreateWorkflow(name: String, trigger: TriggerConfig?, actions: List<ActionConfig>): Boolean {
    return name.isNotBlank() && trigger != null && actions.isNotEmpty()
}

fun getCompletedSteps(name: String, trigger: TriggerConfig?, actions: List<ActionConfig>): Set<BuildStep> {
    val completed = mutableSetOf<BuildStep>()
    if (name.isNotBlank()) completed.add(BuildStep.INFO)
    if (trigger != null) completed.add(BuildStep.TRIGGER)
    if (actions.isNotEmpty()) completed.add(BuildStep.ACTIONS)
    completed.add(BuildStep.USERS) // Optional step is always "completed"
    if (canCreateWorkflow(name, trigger, actions)) completed.add(BuildStep.PREVIEW)
    return completed
}

suspend fun createDynamicWorkflow(
    name: String,
    description: String,
    trigger: TriggerConfig,
    actions: List<ActionConfig>,
    users: List<WorkflowUser>,
    userManager: com.localllm.myapplication.service.UserManager,
    workflowRepository: WorkflowRepository,
    onSuccess: () -> Unit,
    onError: (Exception) -> Unit
) {
    try {
        val currentUserId = userManager.getCurrentUserId() ?: throw Exception("User not signed in")
        
        // Convert configs to actual workflow objects
        val workflowTrigger = convertTriggerConfig(trigger, currentUserId)
        val workflowActions = actions.map { convertActionConfig(it, users) }
        
        val workflow = MultiUserWorkflow(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            createdBy = currentUserId,
            workflowType = if (users.isEmpty()) WorkflowType.PERSONAL else WorkflowType.CROSS_USER,
            sharedWith = users.map { it.id },
            triggers = listOf(workflowTrigger),
            actions = workflowActions
        )
        
        val result = workflowRepository.saveWorkflow(workflow)
        result.fold(
            onSuccess = { onSuccess() },
            onFailure = { onError(it as Exception) }
        )
    } catch (e: Exception) {
        onError(e)
    }
}

fun convertTriggerConfig(config: TriggerConfig, userId: String): MultiUserTrigger {
    return when (config.type) {
        TriggerType.GMAIL_NEW_EMAIL -> MultiUserTrigger.UserGmailNewEmail(
            userId = userId,
            condition = GmailIntegrationService.EmailCondition(
                isUnreadOnly = true
            )
        )
        TriggerType.GMAIL_EMAIL_FROM -> MultiUserTrigger.UserGmailEmailReceived(
            userId = userId,
            fromFilter = config.config["fromFilter"] ?: ""
        )
        TriggerType.GMAIL_EMAIL_SUBJECT -> MultiUserTrigger.UserGmailEmailReceived(
            userId = userId,
            subjectFilter = config.config["subjectFilter"] ?: ""
        )
        TriggerType.TELEGRAM_NEW_MESSAGE -> MultiUserTrigger.UserTelegramMessage(
            userId = userId,
            condition = TelegramBotService.TelegramCondition()
        )
        TriggerType.TELEGRAM_COMMAND -> MultiUserTrigger.UserTelegramCommand(
            userId = userId,
            command = config.config["command"] ?: "/start"
        )
        TriggerType.SCHEDULED_TIME -> MultiUserTrigger.ScheduledTrigger(
            cronExpression = config.config["cron"] ?: "0 9 * * *",
            triggerUserId = userId
        )
        TriggerType.MANUAL_TRIGGER -> MultiUserTrigger.ManualTrigger(
            name = config.config["name"] ?: "Manual Trigger"
        )
        else -> MultiUserTrigger.ManualTrigger(name = "Default")
    }
}

fun convertActionConfig(config: ActionConfig, users: List<WorkflowUser>): MultiUserAction {
    return when (config.type) {
        ActionType.SEND_GMAIL -> MultiUserAction.SendToUserGmail(
            targetUserId = config.config["targetUserId"] ?: users.firstOrNull()?.id ?: "",
            subject = config.config["subject"] ?: "Workflow Notification",
            body = config.config["body"] ?: "This is an automated message from your workflow."
        )
        ActionType.SEND_TELEGRAM -> MultiUserAction.SendToUserTelegram(
            targetUserId = config.config["targetUserId"] ?: users.firstOrNull()?.id ?: "",
            text = config.config["text"] ?: "Workflow notification"
        )
        ActionType.AI_ANALYZE -> MultiUserAction.AIAnalyzeText(
            inputText = config.config["inputText"] ?: "{{trigger_content}}",
            analysisPrompt = config.config["prompt"] ?: "Analyze this content",
            outputVariable = config.config["outputVar"] ?: "ai_analysis"
        )
        ActionType.AI_SUMMARIZE -> MultiUserAction.AISummarizeContent(
            content = config.config["content"] ?: "{{trigger_content}}",
            maxLength = config.config["maxLength"]?.toIntOrNull() ?: 100,
            outputVariable = config.config["outputVar"] ?: "ai_summary"
        )
        ActionType.AI_TRANSLATE -> MultiUserAction.AITranslateText(
            text = config.config["text"] ?: "{{trigger_content}}",
            targetLanguage = config.config["language"] ?: "English",
            outputVariable = config.config["outputVar"] ?: "ai_translation"
        )
        ActionType.AI_GENERATE_REPLY -> MultiUserAction.AISmartReply(
            originalMessage = config.config["originalMessage"] ?: "{{trigger_content}}",
            tone = config.config["tone"] ?: "professional",
            outputVariable = config.config["outputVar"] ?: "ai_reply"
        )
        ActionType.DELAY -> MultiUserAction.DelayAction(
            delayMinutes = config.config["delayMinutes"]?.toIntOrNull() ?: 5
        )
        else -> MultiUserAction.LogAction("Dynamic action executed")
    }
}

// Missing dialog components for dynamic workflow building

@Composable
fun TriggerPickerDialog(
    onDismiss: () -> Unit,
    onTriggerSelected: (TriggerConfig) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Gmail") }
    var triggerConfig by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    
    val categories = listOf("Gmail", "Telegram", "Schedule", "Manual")
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Choose Trigger",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Category tabs
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        FilterChip(
                            onClick = { 
                                selectedCategory = category
                                triggerConfig = emptyMap()
                            },
                            label = { Text(category) },
                            selected = selectedCategory == category
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Trigger options based on category
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (selectedCategory) {
                        "Gmail" -> {
                            item {
                                TriggerOptionCard(
                                    title = "New Email Received",
                                    description = "Triggers when any new email arrives",
                                    icon = "📧",
                                    onClick = {
                                        onTriggerSelected(TriggerConfig(
                                            type = TriggerType.GMAIL_NEW_EMAIL,
                                            displayName = "New Gmail Email",
                                            config = triggerConfig
                                        ))
                                    }
                                )
                            }
                            item {
                                TriggerOptionCard(
                                    title = "Email from Specific Sender",
                                    description = "Triggers when email arrives from specific sender",
                                    icon = "👤",
                                    onClick = {
                                        onTriggerSelected(TriggerConfig(
                                            type = TriggerType.GMAIL_EMAIL_FROM,
                                            displayName = "Gmail Email from ${triggerConfig["fromFilter"] ?: "specific sender"}",
                                            config = triggerConfig
                                        ))
                                    },
                                    configurable = true,
                                    currentConfig = triggerConfig,
                                    onConfigChange = { key, value ->
                                        triggerConfig = triggerConfig + (key to value)
                                    },
                                    configFields = listOf("fromFilter" to "Sender email address")
                                )
                            }
                            item {
                                TriggerOptionCard(
                                    title = "Email with Subject Keywords",
                                    description = "Triggers when email subject contains specific keywords",
                                    icon = "🔍",
                                    onClick = {
                                        onTriggerSelected(TriggerConfig(
                                            type = TriggerType.GMAIL_EMAIL_SUBJECT,
                                            displayName = "Gmail Email with subject: ${triggerConfig["subjectFilter"] ?: "keywords"}",
                                            config = triggerConfig
                                        ))
                                    },
                                    configurable = true,
                                    currentConfig = triggerConfig,
                                    onConfigChange = { key, value ->
                                        triggerConfig = triggerConfig + (key to value)
                                    },
                                    configFields = listOf("subjectFilter" to "Subject keywords")
                                )
                            }
                        }
                        "Telegram" -> {
                            item {
                                TriggerOptionCard(
                                    title = "New Message Received",
                                    description = "Triggers when any new Telegram message arrives",
                                    icon = "✈️",
                                    onClick = {
                                        onTriggerSelected(TriggerConfig(
                                            type = TriggerType.TELEGRAM_NEW_MESSAGE,
                                            displayName = "New Telegram Message",
                                            config = triggerConfig
                                        ))
                                    }
                                )
                            }
                            item {
                                TriggerOptionCard(
                                    title = "Specific Command",
                                    description = "Triggers when specific bot command is received",
                                    icon = "🤖",
                                    onClick = {
                                        onTriggerSelected(TriggerConfig(
                                            type = TriggerType.TELEGRAM_COMMAND,
                                            displayName = "Telegram Command: ${triggerConfig["command"] ?: "/start"}",
                                            config = triggerConfig
                                        ))
                                    },
                                    configurable = true,
                                    currentConfig = triggerConfig,
                                    onConfigChange = { key, value ->
                                        triggerConfig = triggerConfig + (key to value)
                                    },
                                    configFields = listOf("command" to "Bot command (e.g., /start)")
                                )
                            }
                            item {
                                TriggerOptionCard(
                                    title = "Message from Specific User",
                                    description = "Triggers when message arrives from specific user",
                                    icon = "👤",
                                    onClick = {
                                        onTriggerSelected(TriggerConfig(
                                            type = TriggerType.TELEGRAM_FROM_USER,
                                            displayName = "Telegram Message from ${triggerConfig["fromUser"] ?: "specific user"}",
                                            config = triggerConfig
                                        ))
                                    },
                                    configurable = true,
                                    currentConfig = triggerConfig,
                                    onConfigChange = { key, value ->
                                        triggerConfig = triggerConfig + (key to value)
                                    },
                                    configFields = listOf("fromUser" to "Username or user ID")
                                )
                            }
                        }
                        "Schedule" -> {
                            item {
                                TriggerOptionCard(
                                    title = "Daily at Specific Time",
                                    description = "Triggers every day at specified time",
                                    icon = "⏰",
                                    onClick = {
                                        val hour = triggerConfig["hour"] ?: "9"
                                        val minute = triggerConfig["minute"] ?: "0"
                                        onTriggerSelected(TriggerConfig(
                                            type = TriggerType.SCHEDULED_TIME,
                                            displayName = "Daily at $hour:$minute",
                                            config = triggerConfig + ("cron" to "0 $minute $hour * * *")
                                        ))
                                    },
                                    configurable = true,
                                    currentConfig = triggerConfig,
                                    onConfigChange = { key, value ->
                                        triggerConfig = triggerConfig + (key to value)
                                    },
                                    configFields = listOf(
                                        "hour" to "Hour (0-23)",
                                        "minute" to "Minute (0-59)"
                                    )
                                )
                            }
                            item {
                                TriggerOptionCard(
                                    title = "Weekly Schedule",
                                    description = "Triggers on specific days of the week",
                                    icon = "📅",
                                    onClick = {
                                        val hour = triggerConfig["hour"] ?: "9"
                                        val minute = triggerConfig["minute"] ?: "0"
                                        val dayOfWeek = triggerConfig["dayOfWeek"] ?: "1"
                                        onTriggerSelected(TriggerConfig(
                                            type = TriggerType.SCHEDULED_TIME,
                                            displayName = "Weekly on day $dayOfWeek at $hour:$minute",
                                            config = triggerConfig + ("cron" to "0 $minute $hour * * $dayOfWeek")
                                        ))
                                    },
                                    configurable = true,
                                    currentConfig = triggerConfig,
                                    onConfigChange = { key, value ->
                                        triggerConfig = triggerConfig + (key to value)
                                    },
                                    configFields = listOf(
                                        "hour" to "Hour (0-23)",
                                        "minute" to "Minute (0-59)",
                                        "dayOfWeek" to "Day (1=Monday, 7=Sunday)"
                                    )
                                )
                            }
                        }
                        "Manual" -> {
                            item {
                                TriggerOptionCard(
                                    title = "Manual Trigger",
                                    description = "Run workflow manually with a button press",
                                    icon = "🎯",
                                    onClick = {
                                        onTriggerSelected(TriggerConfig(
                                            type = TriggerType.MANUAL_TRIGGER,
                                            displayName = "Manual: ${triggerConfig["name"] ?: "Quick Action"}",
                                            config = triggerConfig
                                        ))
                                    },
                                    configurable = true,
                                    currentConfig = triggerConfig,
                                    onConfigChange = { key, value ->
                                        triggerConfig = triggerConfig + (key to value)
                                    },
                                    configFields = listOf("name" to "Button name")
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Cancel button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
fun TriggerOptionCard(
    title: String,
    description: String,
    icon: String,
    onClick: () -> Unit,
    configurable: Boolean = false,
    currentConfig: Map<String, String> = emptyMap(),
    onConfigChange: ((String, String) -> Unit)? = null,
    configFields: List<Pair<String, String>> = emptyList()
) {
    var showConfig by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!configurable) onClick() else showConfig = !showConfig }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (configurable) {
                    Icon(
                        if (showConfig) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                } else {
                    Icon(Icons.Default.Add, contentDescription = "Select")
                }
            }
            
            if (configurable && showConfig) {
                Spacer(modifier = Modifier.height(12.dp))
                configFields.forEach { (key, label) ->
                    OutlinedTextField(
                        value = currentConfig[key] ?: "",
                        onValueChange = { onConfigChange?.invoke(key, it) },
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use This Trigger")
                }
            }
        }
    }
}

@Composable
fun ActionPickerDialog(
    onDismiss: () -> Unit,
    onActionSelected: (ActionConfig) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Communication") }
    var actionConfig by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    
    val categories = listOf("Communication", "AI Processing", "Utility")
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Choose Action",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Category tabs
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        FilterChip(
                            onClick = { 
                                selectedCategory = category
                                actionConfig = emptyMap()
                            },
                            label = { Text(category) },
                            selected = selectedCategory == category
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action options based on category
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (selectedCategory) {
                        "Communication" -> {
                            item {
                                ActionOptionCard(
                                    title = "Send Gmail Email",
                                    description = "Send an email via Gmail to specified recipient",
                                    icon = "📧",
                                    onClick = {
                                        onActionSelected(ActionConfig(
                                            type = ActionType.SEND_GMAIL,
                                            displayName = "Send Gmail to ${actionConfig["subject"] ?: "recipient"}",
                                            config = actionConfig
                                        ))
                                    },
                                    configurable = true,
                                    currentConfig = actionConfig,
                                    onConfigChange = { key, value ->
                                        actionConfig = actionConfig + (key to value)
                                    },
                                    configFields = listOf(
                                        "subject" to "Email subject",
                                        "body" to "Email body"
                                    )
                                )
                            }
                            item {
                                ActionOptionCard(
                                    title = "Reply to Gmail",
                                    description = "Reply to the original Gmail message",
                                    icon = "↩️",
                                    onClick = {
                                        onActionSelected(ActionConfig(
                                            type = ActionType.REPLY_GMAIL,
                                            displayName = "Reply to Gmail",
                                            config = actionConfig
                                        ))
                                    },
                                    configurable = true,
                                    currentConfig = actionConfig,
                                    onConfigChange = { key, value ->
                                        actionConfig = actionConfig + (key to value)
                                    },
                                    configFields = listOf("body" to "Reply message")
                                )
                            }
                            item {
                                ActionOptionCard(
                                    title = "Send Telegram Message",
                                    description = "Send a message via Telegram bot",
                                    icon = "✈️",
                                    onClick = {
                                        onActionSelected(ActionConfig(
                                            type = ActionType.SEND_TELEGRAM,
                                            displayName = "Send Telegram: ${actionConfig["text"]?.take(20) ?: "message"}",
                                            config = actionConfig
                                        ))
                                    },
                                    configurable = true,
                                    currentConfig = actionConfig,
                                    onConfigChange = { key, value ->
                                        actionConfig = actionConfig + (key to value)
                                    },
                                    configFields = listOf("text" to "Message text")
                                )
                            }
                            item {
                                ActionOptionCard(
                                    title = "Reply to Telegram",
                                    description = "Reply to the original Telegram message",
                                    icon = "💬",
                                    onClick = {
                                        onActionSelected(ActionConfig(
                                            type = ActionType.REPLY_TELEGRAM,
                                            displayName = "Reply to Telegram",
                                            config = actionConfig
                                        ))
                                    },
                                    configurable = true,
                                    currentConfig = actionConfig,
                                    onConfigChange = { key, value ->
                                        actionConfig = actionConfig + (key to value)
                                    },
                                    configFields = listOf("text" to "Reply text")
                                )
                            }
                        }
                        "AI Processing" -> {
                            item {
                                ActionOptionCard(
                                    title = "AI Text Analysis",
                                    description = "Analyze text content using local AI",
                                    icon = "🧠",
                                    onClick = {
                                        onActionSelected(ActionConfig(
                                            type = ActionType.AI_ANALYZE,
                                            displayName = "AI Analysis: ${actionConfig["prompt"]?.take(20) ?: "custom prompt"}",
                                            config = actionConfig
                                        ))
                                    },
                                    configurable = true,
                                    currentConfig = actionConfig,
                                    onConfigChange = { key, value ->
                                        actionConfig = actionConfig + (key to value)
                                    },
                                    configFields = listOf(
                                        "prompt" to "Analysis prompt",
                                        "outputVar" to "Result variable name"
                                    )
                                )
                            }
                            item {
                                ActionOptionCard(
                                    title = "AI Text Summarization",
                                    description = "Summarize text content using local AI",
                                    icon = "📝",
                                    onClick = {
                                        val maxLength = actionConfig["maxLength"] ?: "100"
                                        onActionSelected(ActionConfig(
                                            type = ActionType.AI_SUMMARIZE,
                                            displayName = "AI Summary (max $maxLength words)",
                                            config = actionConfig
                                        ))
                                    },
                                    configurable = true,
                                    currentConfig = actionConfig,
                                    onConfigChange = { key, value ->
                                        actionConfig = actionConfig + (key to value)
                                    },
                                    configFields = listOf(
                                        "maxLength" to "Maximum words",
                                        "outputVar" to "Result variable name"
                                    )
                                )
                            }
                            item {
                                ActionOptionCard(
                                    title = "AI Text Translation",
                                    description = "Translate text to specified language using local AI",
                                    icon = "🌐",
                                    onClick = {
                                        val language = actionConfig["language"] ?: "English"
                                        onActionSelected(ActionConfig(
                                            type = ActionType.AI_TRANSLATE,
                                            displayName = "AI Translate to $language",
                                            config = actionConfig
                                        ))
                                    },
                                    configurable = true,
                                    currentConfig = actionConfig,
                                    onConfigChange = { key, value ->
                                        actionConfig = actionConfig + (key to value)
                                    },
                                    configFields = listOf(
                                        "language" to "Target language",
                                        "outputVar" to "Result variable name"
                                    )
                                )
                            }
                            item {
                                ActionOptionCard(
                                    title = "AI Smart Reply",
                                    description = "Generate intelligent reply using local AI",
                                    icon = "🤖",
                                    onClick = {
                                        val tone = actionConfig["tone"] ?: "professional"
                                        onActionSelected(ActionConfig(
                                            type = ActionType.AI_GENERATE_REPLY,
                                            displayName = "AI Smart Reply ($tone tone)",
                                            config = actionConfig
                                        ))
                                    },
                                    configurable = true,
                                    currentConfig = actionConfig,
                                    onConfigChange = { key, value ->
                                        actionConfig = actionConfig + (key to value)
                                    },
                                    configFields = listOf(
                                        "tone" to "Reply tone (professional, casual, friendly)",
                                        "outputVar" to "Result variable name"
                                    )
                                )
                            }
                        }
                        "Utility" -> {
                            item {
                                ActionOptionCard(
                                    title = "Add Delay",
                                    description = "Wait for specified time before next action",
                                    icon = "⏱️",
                                    onClick = {
                                        val minutes = actionConfig["delayMinutes"] ?: "5"
                                        onActionSelected(ActionConfig(
                                            type = ActionType.DELAY,
                                            displayName = "Wait $minutes minutes",
                                            config = actionConfig
                                        ))
                                    },
                                    configurable = true,
                                    currentConfig = actionConfig,
                                    onConfigChange = { key, value ->
                                        actionConfig = actionConfig + (key to value)
                                    },
                                    configFields = listOf("delayMinutes" to "Delay in minutes")
                                )
                            }
                            item {
                                ActionOptionCard(
                                    title = "Conditional Action",
                                    description = "Execute action only if condition is met",
                                    icon = "🔀",
                                    onClick = {
                                        onActionSelected(ActionConfig(
                                            type = ActionType.CONDITIONAL,
                                            displayName = "If ${actionConfig["condition"] ?: "condition"} then...",
                                            config = actionConfig
                                        ))
                                    },
                                    configurable = true,
                                    currentConfig = actionConfig,
                                    onConfigChange = { key, value ->
                                        actionConfig = actionConfig + (key to value)
                                    },
                                    configFields = listOf(
                                        "condition" to "Condition to check",
                                        "ifAction" to "Action if true",
                                        "elseAction" to "Action if false (optional)"
                                    )
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Cancel button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
fun ActionOptionCard(
    title: String,
    description: String,
    icon: String,
    onClick: () -> Unit,
    configurable: Boolean = false,
    currentConfig: Map<String, String> = emptyMap(),
    onConfigChange: ((String, String) -> Unit)? = null,
    configFields: List<Pair<String, String>> = emptyList()
) {
    var showConfig by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!configurable) onClick() else showConfig = !showConfig }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (configurable) {
                    Icon(
                        if (showConfig) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                } else {
                    Icon(Icons.Default.Add, contentDescription = "Select")
                }
            }
            
            if (configurable && showConfig) {
                Spacer(modifier = Modifier.height(12.dp))
                configFields.forEach { (key, label) ->
                    if (key == "body" || key == "text") {
                        OutlinedTextField(
                            value = currentConfig[key] ?: "",
                            onValueChange = { onConfigChange?.invoke(key, it) },
                            label = { Text(label) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5
                        )
                    } else {
                        OutlinedTextField(
                            value = currentConfig[key] ?: "",
                            onValueChange = { onConfigChange?.invoke(key, it) },
                            label = { Text(label) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add This Action")
                }
            }
        }
    }
}

@Composable
fun UserPickerDialog(
    selectedUsers: List<WorkflowUser>,
    onDismiss: () -> Unit,
    onUsersSelected: (List<WorkflowUser>) -> Unit
) {
    val context = LocalContext.current
    val userManager = remember { AppContainer.provideUserManager(context) }
    var currentUsers by remember { mutableStateOf(selectedUsers) }
    var availableUsers by remember { mutableStateOf<List<WorkflowUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Load available users
    LaunchedEffect(Unit) {
        isLoading = true
        userManager.getAllUsers().fold(
            onSuccess = { users ->
                availableUsers = users.filter { user ->
                    // Exclude current user from selection
                    user.id != userManager.getCurrentUserId()
                }
                isLoading = false
            },
            onFailure = {
                isLoading = false
            }
        )
    }
    
    val filteredUsers = if (searchQuery.isBlank()) {
        availableUsers
    } else {
        availableUsers.filter { user ->
            user.displayName.contains(searchQuery, ignoreCase = true) ||
            user.email?.contains(searchQuery, ignoreCase = true) == true
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Select Team Members",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search users") },
                    placeholder = { Text("Enter name or email...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Selected users
                if (currentUsers.isNotEmpty()) {
                    Text(
                        text = "Selected (${currentUsers.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentUsers) { user ->
                            UserChip(
                                user = user,
                                onRemove = {
                                    currentUsers = currentUsers - user
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Available users
                Text(
                    text = "Available Users",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (filteredUsers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No users available" else "No users match your search",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredUsers) { user ->
                            val isSelected = currentUsers.contains(user)
                            UserSelectionCard(
                                user = user,
                                isSelected = isSelected,
                                onToggle = {
                                    currentUsers = if (isSelected) {
                                        currentUsers - user
                                    } else {
                                        currentUsers + user
                                    }
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onUsersSelected(currentUsers) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Done (${currentUsers.size})")
                    }
                }
            }
        }
    }
}

@Composable
fun UserSelectionCard(
    user: WorkflowUser,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .padding(end = 12.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (user.email != null) {
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (user.telegramUsername != null) {
                    Text(
                        text = "@${user.telegramUsername}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
fun UserChip(
    user: WorkflowUser,
    onRemove: (() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.labelMedium
            )
            if (onRemove != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TriggerPreview(trigger: TriggerConfig) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (trigger.type) {
                TriggerType.GMAIL_NEW_EMAIL, TriggerType.GMAIL_EMAIL_FROM, TriggerType.GMAIL_EMAIL_SUBJECT -> "📧"
                TriggerType.TELEGRAM_NEW_MESSAGE, TriggerType.TELEGRAM_COMMAND, TriggerType.TELEGRAM_FROM_USER -> "✈️"
                TriggerType.SCHEDULED_TIME -> "⏰"
                TriggerType.MANUAL_TRIGGER -> "🎯"
            }
            
            Text(
                text = icon,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 12.dp)
            )
            
            Column {
                Text(
                    text = trigger.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (trigger.type) {
                        TriggerType.GMAIL_NEW_EMAIL -> "Watches for new Gmail emails"
                        TriggerType.GMAIL_EMAIL_FROM -> "Watches for emails from specific sender"
                        TriggerType.GMAIL_EMAIL_SUBJECT -> "Watches for emails with specific subject"
                        TriggerType.TELEGRAM_NEW_MESSAGE -> "Watches for new Telegram messages"
                        TriggerType.TELEGRAM_COMMAND -> "Watches for specific bot command"
                        TriggerType.TELEGRAM_FROM_USER -> "Watches for messages from specific user"
                        TriggerType.SCHEDULED_TIME -> "Runs on schedule"
                        TriggerType.MANUAL_TRIGGER -> "Run manually when needed"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ActionPreview(
    action: ActionConfig,
    index: Int,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (action.type) {
                ActionType.SEND_GMAIL, ActionType.REPLY_GMAIL -> "📧"
                ActionType.SEND_TELEGRAM, ActionType.REPLY_TELEGRAM -> "✈️"
                ActionType.AI_ANALYZE -> "🧠"
                ActionType.AI_SUMMARIZE -> "📝"
                ActionType.AI_TRANSLATE -> "🌐"
                ActionType.AI_GENERATE_REPLY -> "🤖"
                ActionType.DELAY -> "⏱️"
                ActionType.CONDITIONAL -> "🔀"
            }
            
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            )
            
            Text(
                text = icon,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 12.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (action.type) {
                        ActionType.SEND_GMAIL -> "Send email via Gmail"
                        ActionType.REPLY_GMAIL -> "Reply to Gmail email"
                        ActionType.SEND_TELEGRAM -> "Send Telegram message"
                        ActionType.REPLY_TELEGRAM -> "Reply to Telegram message"
                        ActionType.AI_ANALYZE -> "Analyze content with AI"
                        ActionType.AI_SUMMARIZE -> "Summarize content with AI"
                        ActionType.AI_TRANSLATE -> "Translate text with AI"
                        ActionType.AI_GENERATE_REPLY -> "Generate smart reply with AI"
                        ActionType.DELAY -> "Wait before next action"
                        ActionType.CONDITIONAL -> "Execute conditionally"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Action controls
            Column {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move up",
                        tint = if (canMoveUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move down",
                        tint = if (canMoveDown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun TestWorkflowCard(
    trigger: TriggerConfig?,
    actions: List<ActionConfig>,
    users: List<WorkflowUser>,
    userManager: com.localllm.myapplication.service.UserManager,
    workflowEngine: com.localllm.myapplication.service.MultiUserWorkflowEngine
) {
    val scope = rememberCoroutineScope()
    var isRunningTest by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<List<WorkflowTestResult>>(emptyList()) }
    var showTestResults by remember { mutableStateOf(false) }
    var testError by remember { mutableStateOf<String?>(null) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🧪 Test Your Workflow",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Test your workflow with sample data to ensure it works as expected before saving.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (testError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = testError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            if (testResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showTestResults = !showTestResults },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (showTestResults) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${if (showTestResults) "Hide" else "Show"} Test Results (${testResults.size})")
                }
                
                if (showTestResults) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(testResults) { result ->
                            TestResultItem(result)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (trigger != null && actions.isNotEmpty()) {
                        scope.launch {
                            isRunningTest = true
                            testError = null
                            try {
                                val results = runWorkflowTest(
                                    trigger = trigger,
                                    actions = actions,
                                    users = users,
                                    userManager = userManager,
                                    workflowEngine = workflowEngine
                                )
                                testResults = results
                                showTestResults = true
                            } catch (e: Exception) {
                                testError = "Test failed: ${e.message}"
                            } finally {
                                isRunningTest = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunningTest && trigger != null && actions.isNotEmpty()
            ) {
                if (isRunningTest) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isRunningTest) "Running Test..." else "Run Test")
            }
        }
    }
}

@Composable
fun TestResultItem(result: WorkflowTestResult) {
    Surface(
        color = when (result.status) {
            WorkflowTestStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
            WorkflowTestStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
            WorkflowTestStatus.WARNING -> MaterialTheme.colorScheme.secondaryContainer
        },
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (result.status) {
                WorkflowTestStatus.SUCCESS -> Icons.Default.CheckCircle
                WorkflowTestStatus.FAILED -> Icons.Default.Warning
                WorkflowTestStatus.WARNING -> Icons.Default.Info
            }
            
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = when (result.status) {
                    WorkflowTestStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                    WorkflowTestStatus.FAILED -> MaterialTheme.colorScheme.error
                    WorkflowTestStatus.WARNING -> MaterialTheme.colorScheme.secondary
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.stepName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (result.executionTime > 0) {
                    Text(
                        text = "${result.executionTime}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Data classes for workflow testing
data class WorkflowTestResult(
    val stepName: String,
    val message: String,
    val status: WorkflowTestStatus,
    val executionTime: Long = 0L,
    val details: Map<String, Any> = emptyMap()
)

enum class WorkflowTestStatus {
    SUCCESS, FAILED, WARNING
}

// Test execution function
suspend fun runWorkflowTest(
    trigger: TriggerConfig,
    actions: List<ActionConfig>,
    users: List<WorkflowUser>,
    userManager: com.localllm.myapplication.service.UserManager,
    workflowEngine: com.localllm.myapplication.service.MultiUserWorkflowEngine
): List<WorkflowTestResult> {
    val results = mutableListOf<WorkflowTestResult>()
    val currentUserId = userManager.getCurrentUserId() ?: "test_user"
    
    // Test trigger validation
    val triggerStartTime = System.currentTimeMillis()
    try {
        val triggerResult = validateTrigger(trigger, currentUserId)
        results.add(WorkflowTestResult(
            stepName = "Trigger: ${trigger.displayName}",
            message = triggerResult.message,
            status = triggerResult.status,
            executionTime = System.currentTimeMillis() - triggerStartTime
        ))
    } catch (e: Exception) {
        results.add(WorkflowTestResult(
            stepName = "Trigger: ${trigger.displayName}",
            message = "Trigger validation failed: ${e.message}",
            status = WorkflowTestStatus.FAILED,
            executionTime = System.currentTimeMillis() - triggerStartTime
        ))
        return results // Stop testing if trigger fails
    }
    
    // Test each action
    var previousActionOutput = generateSampleTriggerData(trigger)
    actions.forEachIndexed { index, action ->
        val actionStartTime = System.currentTimeMillis()
        try {
            val actionResult = validateAction(action, previousActionOutput, users)
            results.add(WorkflowTestResult(
                stepName = "Action ${index + 1}: ${action.displayName}",
                message = actionResult.message,
                status = actionResult.status,
                executionTime = System.currentTimeMillis() - actionStartTime,
                details = actionResult.details
            ))
            
            // Update output for next action
            if (actionResult.output.isNotEmpty()) {
                previousActionOutput = actionResult.output
            }
        } catch (e: Exception) {
            results.add(WorkflowTestResult(
                stepName = "Action ${index + 1}: ${action.displayName}",
                message = "Action validation failed: ${e.message}",
                status = WorkflowTestStatus.FAILED,
                executionTime = System.currentTimeMillis() - actionStartTime
            ))
        }
    }
    
    // Test overall workflow connectivity
    val connectivityResult = validateWorkflowConnectivity(trigger, actions, users)
    results.add(connectivityResult)
    
    return results
}

fun validateTrigger(trigger: TriggerConfig, userId: String): WorkflowTestResult {
    return when (trigger.type) {
        TriggerType.GMAIL_NEW_EMAIL -> {
            WorkflowTestResult(
                stepName = "Gmail Trigger",
                message = "✓ Gmail trigger configured correctly. Will monitor for new emails.",
                status = WorkflowTestStatus.SUCCESS
            )
        }
        TriggerType.GMAIL_EMAIL_FROM -> {
            val fromFilter = trigger.config["fromFilter"]
            if (fromFilter.isNullOrBlank()) {
                WorkflowTestResult(
                    stepName = "Gmail Trigger", 
                    message = "⚠️ No sender filter specified. Will match all emails.",
                    status = WorkflowTestStatus.WARNING
                )
            } else {
                WorkflowTestResult(
                    stepName = "Gmail Trigger",
                    message = "✓ Will monitor emails from: $fromFilter",
                    status = WorkflowTestStatus.SUCCESS
                )
            }
        }
        TriggerType.TELEGRAM_NEW_MESSAGE -> {
            WorkflowTestResult(
                stepName = "Telegram Trigger",
                message = "✓ Telegram trigger configured correctly. Will monitor for new messages.",
                status = WorkflowTestStatus.SUCCESS
            )
        }
        TriggerType.SCHEDULED_TIME -> {
            val cron = trigger.config["cron"]
            WorkflowTestResult(
                stepName = "Schedule Trigger",
                message = "✓ Schedule configured: ${trigger.displayName}",
                status = WorkflowTestStatus.SUCCESS,
                details = mapOf("cron" to (cron ?: ""))
            )
        }
        TriggerType.MANUAL_TRIGGER -> {
            WorkflowTestResult(
                stepName = "Manual Trigger",
                message = "✓ Manual trigger ready. Can be run on-demand.",
                status = WorkflowTestStatus.SUCCESS
            )
        }
        else -> {
            WorkflowTestResult(
                stepName = "Unknown Trigger",
                message = "⚠️ Unknown trigger type: ${trigger.type}",
                status = WorkflowTestStatus.WARNING
            )
        }
    }
}

data class ActionValidationResult(
    val message: String,
    val status: WorkflowTestStatus,
    val output: Map<String, String> = emptyMap(),
    val details: Map<String, Any> = emptyMap()
)

fun validateAction(action: ActionConfig, inputData: Map<String, String>, users: List<WorkflowUser>): ActionValidationResult {
    return when (action.type) {
        ActionType.SEND_GMAIL -> {
            val subject = action.config["subject"]
            val body = action.config["body"]
            if (subject.isNullOrBlank()) {
                ActionValidationResult(
                    message = "⚠️ No email subject specified",
                    status = WorkflowTestStatus.WARNING
                )
            } else {
                ActionValidationResult(
                    message = "✓ Will send email: \"$subject\"",
                    status = WorkflowTestStatus.SUCCESS,
                    details = mapOf("subject" to subject, "body" to (body ?: ""))
                )
            }
        }
        ActionType.SEND_TELEGRAM -> {
            val text = action.config["text"]
            if (text.isNullOrBlank()) {
                ActionValidationResult(
                    message = "⚠️ No message text specified",
                    status = WorkflowTestStatus.WARNING
                )
            } else {
                ActionValidationResult(
                    message = "✓ Will send Telegram message: \"${text.take(50)}${if (text.length > 50) "..." else ""}\"",
                    status = WorkflowTestStatus.SUCCESS
                )
            }
        }
        ActionType.AI_ANALYZE -> {
            val prompt = action.config["prompt"]
            val outputVar = action.config["outputVar"] ?: "ai_analysis"
            if (prompt.isNullOrBlank()) {
                ActionValidationResult(
                    message = "⚠️ No analysis prompt specified",
                    status = WorkflowTestStatus.WARNING
                )
            } else {
                ActionValidationResult(
                    message = "✓ Will analyze with AI: \"$prompt\"",
                    status = WorkflowTestStatus.SUCCESS,
                    output = mapOf(outputVar to "Sample AI analysis result"),
                    details = mapOf("prompt" to prompt, "outputVariable" to outputVar)
                )
            }
        }
        ActionType.AI_SUMMARIZE -> {
            val maxLength = action.config["maxLength"]?.toIntOrNull() ?: 100
            val outputVar = action.config["outputVar"] ?: "ai_summary"
            ActionValidationResult(
                message = "✓ Will summarize content (max $maxLength words)",
                status = WorkflowTestStatus.SUCCESS,
                output = mapOf(outputVar to "Sample summary of the content"),
                details = mapOf("maxLength" to maxLength, "outputVariable" to outputVar)
            )
        }
        ActionType.AI_TRANSLATE -> {
            val language = action.config["language"] ?: "English"
            val outputVar = action.config["outputVar"] ?: "ai_translation"
            ActionValidationResult(
                message = "✓ Will translate to $language",
                status = WorkflowTestStatus.SUCCESS,
                output = mapOf(outputVar to "Sample translated text"),
                details = mapOf("targetLanguage" to language, "outputVariable" to outputVar)
            )
        }
        ActionType.DELAY -> {
            val minutes = action.config["delayMinutes"]?.toIntOrNull() ?: 5
            ActionValidationResult(
                message = "✓ Will wait $minutes minutes before next action",
                status = WorkflowTestStatus.SUCCESS,
                details = mapOf("delayMinutes" to minutes)
            )
        }
        else -> {
            ActionValidationResult(
                message = "⚠️ Unknown action type: ${action.type}",
                status = WorkflowTestStatus.WARNING
            )
        }
    }
}

fun generateSampleTriggerData(trigger: TriggerConfig): Map<String, String> {
    return when (trigger.type) {
        TriggerType.GMAIL_NEW_EMAIL, TriggerType.GMAIL_EMAIL_FROM, TriggerType.GMAIL_EMAIL_SUBJECT -> {
            mapOf(
                "trigger_content" to "Sample email content for testing",
                "email_subject" to "Test Email Subject",
                "email_from" to "test@example.com",
                "email_body" to "This is a sample email body for workflow testing."
            )
        }
        TriggerType.TELEGRAM_NEW_MESSAGE, TriggerType.TELEGRAM_COMMAND -> {
            mapOf(
                "trigger_content" to "Sample telegram message",
                "telegram_text" to "Hello, this is a test message",
                "telegram_user" to "test_user"
            )
        }
        TriggerType.SCHEDULED_TIME -> {
            mapOf(
                "trigger_content" to "Scheduled trigger activated",
                "schedule_time" to System.currentTimeMillis().toString()
            )
        }
        TriggerType.MANUAL_TRIGGER -> {
            mapOf(
                "trigger_content" to "Manual trigger activated",
                "trigger_name" to (trigger.config["name"] ?: "Manual Trigger")
            )
        }
        else -> {
            mapOf("trigger_content" to "Unknown trigger data")
        }
    }
}

fun validateWorkflowConnectivity(
    trigger: TriggerConfig,
    actions: List<ActionConfig>,
    users: List<WorkflowUser>
): WorkflowTestResult {
    val issues = mutableListOf<String>()
    
    // Check if there are cross-user actions but no users selected
    val hasUserTargetedActions = actions.any { action ->
        action.type == ActionType.SEND_GMAIL || action.type == ActionType.SEND_TELEGRAM
    }
    
    if (hasUserTargetedActions && users.isEmpty()) {
        issues.add("Some actions target other users, but no team members are selected")
    }
    
    // Check if AI actions have proper output variables
    val aiActions = actions.filter { action ->
        action.type in listOf(ActionType.AI_ANALYZE, ActionType.AI_SUMMARIZE, ActionType.AI_TRANSLATE, ActionType.AI_GENERATE_REPLY)
    }
    
    val outputVariables = aiActions.mapNotNull { it.config["outputVar"] }
    val duplicateVars = outputVariables.groupBy { it }.filter { it.value.size > 1 }.keys
    if (duplicateVars.isNotEmpty()) {
        issues.add("Duplicate output variables: ${duplicateVars.joinToString(", ")}")
    }
    
    return if (issues.isEmpty()) {
        WorkflowTestResult(
            stepName = "Workflow Connectivity",
            message = "✓ All workflow components are properly connected",
            status = WorkflowTestStatus.SUCCESS
        )
    } else {
        WorkflowTestResult(
            stepName = "Workflow Connectivity",
            message = "⚠️ ${issues.joinToString("; ")}",
            status = WorkflowTestStatus.WARNING,
            details = mapOf("issues" to issues)
        )
    }
}

// Save workflow as template function
suspend fun saveAsTemplate(
    name: String,
    description: String,
    trigger: TriggerConfig,
    actions: List<ActionConfig>,
    users: List<WorkflowUser>
) {
    try {
        val template = WorkflowTemplate(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = "$name (Custom Template)",
            description = description,
            category = "Custom",
            platforms = determinePlatforms(trigger, actions),
            requiredUsers = if (users.isNotEmpty()) 1 else 0,
            optionalUsers = users.size,
            estimatedSetupTime = "2-5 minutes",
            tags = generateTemplateTags(trigger, actions),
            isCustom = true,
            triggerConfig = trigger,
            actionConfigs = actions
        )
        
        // Save to custom templates (this would integrate with your template storage system)
        WorkflowTemplates.addCustomTemplate(template)
        
        android.util.Log.d("TemplateCreation", "Created custom template: ${template.name}")
        
    } catch (e: Exception) {
        android.util.Log.e("TemplateCreation", "Failed to save template", e)
    }
}

fun determinePlatforms(trigger: TriggerConfig, actions: List<ActionConfig>): List<WorkflowPlatform> {
    val platforms = mutableSetOf<WorkflowPlatform>()
    
    // Add platform based on trigger
    when (trigger.type) {
        TriggerType.GMAIL_NEW_EMAIL, TriggerType.GMAIL_EMAIL_FROM, TriggerType.GMAIL_EMAIL_SUBJECT -> {
            platforms.add(WorkflowPlatform.GMAIL)
        }
        TriggerType.TELEGRAM_NEW_MESSAGE, TriggerType.TELEGRAM_COMMAND, TriggerType.TELEGRAM_FROM_USER -> {
            platforms.add(WorkflowPlatform.TELEGRAM)
        }
        else -> {}
    }
    
    // Add platforms based on actions
    actions.forEach { action ->
        when (action.type) {
            ActionType.SEND_GMAIL, ActionType.REPLY_GMAIL -> platforms.add(WorkflowPlatform.GMAIL)
            ActionType.SEND_TELEGRAM, ActionType.REPLY_TELEGRAM -> platforms.add(WorkflowPlatform.TELEGRAM)
            ActionType.AI_ANALYZE, ActionType.AI_SUMMARIZE, ActionType.AI_TRANSLATE, ActionType.AI_GENERATE_REPLY -> {
                platforms.add(WorkflowPlatform.AI)
            }
            else -> {}
        }
    }
    
    return platforms.toList()
}

fun generateTemplateTags(trigger: TriggerConfig, actions: List<ActionConfig>): List<String> {
    val tags = mutableSetOf<String>()
    
    // Add trigger-based tags
    when (trigger.type) {
        TriggerType.GMAIL_NEW_EMAIL, TriggerType.GMAIL_EMAIL_FROM, TriggerType.GMAIL_EMAIL_SUBJECT -> {
            tags.add("Email")
        }
        TriggerType.TELEGRAM_NEW_MESSAGE, TriggerType.TELEGRAM_COMMAND, TriggerType.TELEGRAM_FROM_USER -> {
            tags.add("Messaging")
        }
        TriggerType.SCHEDULED_TIME -> tags.add("Scheduled")
        TriggerType.MANUAL_TRIGGER -> tags.add("Manual")
    }
    
    // Add action-based tags
    val hasAI = actions.any { it.type in listOf(ActionType.AI_ANALYZE, ActionType.AI_SUMMARIZE, ActionType.AI_TRANSLATE, ActionType.AI_GENERATE_REPLY) }
    if (hasAI) tags.add("AI")
    
    val hasCommunication = actions.any { it.type in listOf(ActionType.SEND_GMAIL, ActionType.SEND_TELEGRAM, ActionType.REPLY_GMAIL, ActionType.REPLY_TELEGRAM) }
    if (hasCommunication) tags.add("Communication")
    
    val hasDelay = actions.any { it.type == ActionType.DELAY }
    if (hasDelay) tags.add("Scheduled")
    
    tags.add("Custom")
    
    return tags.toList()
}

// Extension to WorkflowTemplate data class
data class WorkflowTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val platforms: List<WorkflowPlatform>,
    val requiredUsers: Int,
    val optionalUsers: Int,
    val estimatedSetupTime: String,
    val tags: List<String>,
    val isCustom: Boolean = false,
    val triggerConfig: TriggerConfig? = null,
    val actionConfigs: List<ActionConfig> = emptyList()
)

enum class WorkflowPlatform(val displayName: String) {
    GMAIL("Gmail"),
    TELEGRAM("Telegram"), 
    AI("AI Processing"),
    SCHEDULER("Scheduler")
}

// Mock template storage (replace with actual implementation)
object WorkflowTemplates {
    private val customTemplates = mutableListOf<WorkflowTemplate>()
    
    fun addCustomTemplate(template: WorkflowTemplate) {
        customTemplates.add(template)
    }
    
    fun getCustomTemplates(): List<WorkflowTemplate> = customTemplates.toList()
    
    fun getAllTemplates(): List<WorkflowTemplate> {
        // Return built-in templates + custom templates
        return getBuiltInTemplates() + customTemplates
    }
    
    private fun getBuiltInTemplates(): List<WorkflowTemplate> {
        return listOf(
            WorkflowTemplate(
                id = "email_to_telegram",
                name = "Email to Telegram Alert",
                description = "Forward important emails to Telegram",
                category = "Communication",
                platforms = listOf(WorkflowPlatform.GMAIL, WorkflowPlatform.TELEGRAM),
                requiredUsers = 1,
                optionalUsers = 2,
                estimatedSetupTime = "3 minutes",
                tags = listOf("Email", "Messaging", "Alerts")
            ),
            WorkflowTemplate(
                id = "ai_email_summary",
                name = "AI Email Summary",
                description = "Get AI-powered summaries of important emails",
                category = "AI Processing",
                platforms = listOf(WorkflowPlatform.GMAIL, WorkflowPlatform.AI),
                requiredUsers = 1,
                optionalUsers = 0,
                estimatedSetupTime = "2 minutes",
                tags = listOf("Email", "AI", "Summary")
            ),
            WorkflowTemplate(
                id = "scheduled_reports",
                name = "Daily Reports",
                description = "Generate and send daily activity reports",
                category = "Reporting",
                platforms = listOf(WorkflowPlatform.SCHEDULER, WorkflowPlatform.TELEGRAM),
                requiredUsers = 1,
                optionalUsers = 3,
                estimatedSetupTime = "5 minutes",
                tags = listOf("Scheduled", "Reports", "Daily")
            ),
            WorkflowTemplate(
                id = "multilingual_support",
                name = "Multilingual Customer Support",
                description = "Auto-translate and respond to customer messages",
                category = "Customer Service",
                platforms = listOf(WorkflowPlatform.TELEGRAM, WorkflowPlatform.AI),
                requiredUsers = 1,
                optionalUsers = 1,
                estimatedSetupTime = "4 minutes",
                tags = listOf("Translation", "AI", "Customer Service")
            ),
            WorkflowTemplate(
                id = "smart_notifications",
                name = "Smart Notification Filter",
                description = "AI-powered filtering of important notifications",
                category = "Productivity",
                platforms = listOf(WorkflowPlatform.GMAIL, WorkflowPlatform.AI, WorkflowPlatform.TELEGRAM),
                requiredUsers = 1,
                optionalUsers = 0,
                estimatedSetupTime = "3 minutes",
                tags = listOf("AI", "Filtering", "Notifications")
            )
        )
    }
    
    fun createFromTemplate(templateId: String, creatorUserId: String, targetUserIds: List<String>): MultiUserWorkflow? {
        // This would create a workflow from template
        // For now, return null as placeholder
        return null
    }
}