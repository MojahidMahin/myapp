# LocalLLM Workflow System - API Reference

**Last Updated**: August 17, 2025  
**Version**: 1.0.0  
**API Level**: 1

## 📋 Overview

This document provides a comprehensive API reference for the LocalLLM Workflow System, including all workflow actions, triggers, services, and integration points.

## 🏗️ Core Architecture

### Workflow Engine

#### MultiUserWorkflowEngine

The central orchestrator for workflow execution.

```kotlin
class MultiUserWorkflowEngine(
    private val context: Context,
    private val userManager: UserManager,
    private val workflowRepository: WorkflowRepository,
    val executionRepository: WorkflowExecutionRepository,
    private val aiProcessor: AIWorkflowProcessor
)
```

**Key Methods**:

```kotlin
suspend fun executeWorkflow(
    workflowId: String,
    triggerUserId: String,
    triggerData: Any
): Result<WorkflowExecutionResult>

suspend fun executeAction(
    action: MultiUserAction,
    context: WorkflowExecutionContext
): Result<String>
```

### Workflow Data Models

#### MultiUserWorkflow

```kotlin
data class MultiUserWorkflow(
    override val id: String,
    override val name: String,
    override val description: String,
    override val createdBy: String,
    val sharedWith: List<String> = emptyList(),
    val triggers: List<MultiUserTrigger>,
    val actions: List<MultiUserAction>,
    override val isEnabled: Boolean = true,
    override val workflowType: WorkflowType = WorkflowType.PERSONAL,
    override val createdAt: Long = System.currentTimeMillis(),
    override val updatedAt: Long = System.currentTimeMillis()
) : Workflow
```

#### WorkflowExecutionContext

```kotlin
data class WorkflowExecutionContext(
    val workflowId: String,
    val triggerUserId: String,
    val triggerData: Any,
    val executionId: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val variables: MutableMap<String, String> = mutableMapOf()
)
```

## 🎯 Workflow Actions

### Base Action Interface

```kotlin
sealed class MultiUserAction
```

All workflow actions inherit from this sealed class.

### Communication Actions

#### SendToUserGmail

Send email via Gmail to specified recipient.

```kotlin
data class SendToUserGmail(
    val targetUserId: String,
    val to: String? = null,  // If null, uses target user's email
    val subject: String,
    val body: String,
    val isHtml: Boolean = false
) : MultiUserAction()
```

**Usage Example**:
```kotlin
val emailAction = MultiUserAction.SendToUserGmail(
    targetUserId = "user123",
    to = "recipient@example.com",
    subject = "Workflow Notification",
    body = "This is an automated message from your workflow.",
    isHtml = false
)
```

**Variables Supported**:
- `{{email_subject}}` - Original email subject
- `{{email_body}}` - Original email body
- `{{email_from}}` - Original sender
- `{{trigger_content}}` - Content from trigger

#### ReplyToUserGmail

Reply to the original Gmail message that triggered the workflow.

```kotlin
data class ReplyToUserGmail(
    val targetUserId: String,
    val originalMessageId: String,
    val replyBody: String,
    val isHtml: Boolean = false
) : MultiUserAction()
```

**Usage Example**:
```kotlin
val replyAction = MultiUserAction.ReplyToUserGmail(
    targetUserId = "user123",
    originalMessageId = "{{trigger_email_id}}",
    replyBody = "Thank you for your email. This is an automated reply.",
    isHtml = false
)
```

#### SendToUserTelegram

Send message via Telegram to specified user.

```kotlin
data class SendToUserTelegram(
    val targetUserId: String,
    val chatId: Long? = null,  // If null, uses user's private chat
    val text: String,
    val parseMode: String? = null
) : MultiUserAction()
```

### AI Processing Actions

#### AIAnalyzeText

Analyze text content using local AI.

```kotlin
data class AIAnalyzeText(
    val inputText: String,
    val analysisPrompt: String,
    val outputVariable: String = "ai_analysis"
) : MultiUserAction()
```

**Usage Example**:
```kotlin
val analyzeAction = MultiUserAction.AIAnalyzeText(
    inputText = "{{trigger_content}}",
    analysisPrompt = "Analyze the sentiment and extract key topics from this text",
    outputVariable = "analysis_result"
)
```

#### AISummarizeContent

Summarize text content using local AI.

```kotlin
data class AISummarizeContent(
    val content: String,
    val maxLength: Int = 100,
    val outputVariable: String = "ai_summary"
) : MultiUserAction()
```

#### AIAutoEmailSummarizer

**New Feature**: Automatically summarize triggered emails and forward to specified addresses.

```kotlin
data class AIAutoEmailSummarizer(
    val forwardToEmails: List<String>,
    val summaryStyle: String = "concise", // "concise", "detailed", "structured"
    val maxSummaryLength: Int = 200,
    val includeOriginalSubject: Boolean = true,
    val includeOriginalSender: Boolean = true,
    val customSubjectPrefix: String = "[Summary]",
    val emailTemplate: String = """
        {{summary_subject}}
        
        Summary:
        {{ai_summary}}
        
        {{original_info}}
    """.trimIndent(),
    val summaryOutputVariable: String = "email_summary"
) : MultiUserAction()
```

**Usage Example**:
```kotlin
val autoSummarizer = MultiUserAction.AIAutoEmailSummarizer(
    forwardToEmails = listOf("manager@company.com", "team@company.com"),
    summaryStyle = "structured",
    maxSummaryLength = 250,
    customSubjectPrefix = "[AI Summary]",
    summaryOutputVariable = "email_summary"
)
```

**Template Variables**:
- `{{ai_summary}}` - Generated summary
- `{{summary_subject}}` - Formatted subject with prefix
- `{{original_info}}` - Original sender and subject info
- `{{email_from}}` - Original sender email
- `{{email_subject}}` - Original email subject
- `{{email_body}}` - Original email content
- `{{timestamp}}` - Current timestamp

#### AISmartSummarizeAndForward

Advanced Zapier-like functionality with keyword-based forwarding.

```kotlin
data class AISmartSummarizeAndForward(
    val triggerContent: String = "{{trigger_content}}",
    val summarizationStyle: String = "concise",
    val maxSummaryLength: Int = 150,
    val keywordRules: List<KeywordForwardingRule> = emptyList(),
    val defaultForwardTo: ForwardingDestination? = null,
    val includeOriginalContent: Boolean = false,
    val summaryOutputVariable: String = "ai_summary",
    val keywordsOutputVariable: String = "extracted_keywords",
    val forwardingDecisionVariable: String = "forwarding_decision"
) : MultiUserAction()
```

### Control Flow Actions

#### DelayAction

Add delay before next action.

```kotlin
data class DelayAction(
    val delayMinutes: Int
) : MultiUserAction()
```

#### ConditionalAction

Execute action based on condition.

```kotlin
data class ConditionalAction(
    val condition: String,
    val trueAction: MultiUserAction,
    val falseAction: MultiUserAction? = null
) : MultiUserAction()
```

## 🎪 Workflow Triggers

### Base Trigger Interface

```kotlin
sealed class MultiUserTrigger
```

### Gmail Triggers

#### UserGmailNewEmail

Trigger on new email received.

```kotlin
data class UserGmailNewEmail(
    val userId: String,
    val condition: GmailIntegrationService.EmailCondition
) : MultiUserTrigger()
```

#### UserGmailEmailReceived

Trigger on email received with specific filters.

```kotlin
data class UserGmailEmailReceived(
    val userId: String,
    val fromFilter: String? = null,
    val subjectFilter: String? = null,
    val bodyFilter: String? = null
) : MultiUserTrigger()
```

### Telegram Triggers

#### UserTelegramMessage

Trigger on Telegram message received.

```kotlin
data class UserTelegramMessage(
    val userId: String,
    val condition: TelegramBotService.TelegramCondition
) : MultiUserTrigger()
```

### Manual Triggers

#### ManualTrigger

Manually triggered workflow.

```kotlin
data class ManualTrigger(
    val triggerUserId: String? = null
) : MultiUserTrigger()
```

## 🔧 Services

### AI Services

#### SummarizationService

Interface for text summarization.

```kotlin
interface SummarizationService {
    suspend fun summarizeText(
        text: String,
        maxLength: Int = 100,
        style: SummarizationStyle = SummarizationStyle.CONCISE
    ): Result<String>
    
    suspend fun summarizeEmail(
        subject: String,
        body: String,
        sender: String
    ): Result<EmailSummary>
}
```

#### LocalLLMSummarizationService

Concrete implementation using local LLM.

```kotlin
class LocalLLMSummarizationService(
    private val modelManager: ModelManager,
    private val context: Context
) : SummarizationService
```

**Summarization Styles**:
```kotlin
enum class SummarizationStyle {
    CONCISE,        // Very brief summary
    DETAILED,       // Comprehensive summary
    STRUCTURED,     // Bullet points and organized
    KEYWORDS_FOCUSED // Focus on keywords and actions
}
```

#### WorkflowKeywordService

Service for keyword extraction and matching.

```kotlin
class WorkflowKeywordService {
    suspend fun extractKeywords(text: String, maxKeywords: Int = 10): Result<List<String>>
    
    suspend fun findKeywordMatches(
        text: String,
        targetKeywords: List<String>,
        fuzzyMatch: Boolean = true
    ): Result<List<String>>
    
    suspend fun extractEmailActions(subject: String, body: String): Result<List<String>>
}
```

### Integration Services

#### GmailIntegrationService

Service for Gmail integration.

```kotlin
class GmailIntegrationService(private val context: Context) {
    suspend fun sendEmail(
        to: String,
        subject: String,
        body: String,
        isHtml: Boolean = false
    ): Result<String>
    
    suspend fun replyToEmail(
        messageId: String,
        replyBody: String,
        isHtml: Boolean = false
    ): Result<String>
    
    suspend fun checkForNewEmails(
        condition: EmailCondition,
        limit: Int = 10
    ): Result<List<EmailData>>
}
```

**EmailCondition**:
```kotlin
data class EmailCondition(
    val isUnreadOnly: Boolean = true,
    val fromFilter: String? = null,
    val subjectFilter: String? = null,
    val bodyFilter: String? = null,
    val maxAgeHours: Int? = null,
    val newerThan: Long? = null
)
```

#### TelegramBotService

Service for Telegram bot integration.

```kotlin
class TelegramBotService(
    private val context: Context,
    private val botToken: String
) {
    suspend fun sendMessage(
        chatId: Long,
        text: String,
        parseMode: String? = null
    ): Result<Int>
    
    suspend fun replyToMessage(
        chatId: Long,
        replyToMessageId: Long,
        text: String,
        parseMode: String? = null
    ): Result<Int>
}
```

### Repository Services

#### WorkflowRepository

Interface for workflow data persistence.

```kotlin
interface WorkflowRepository {
    suspend fun saveWorkflow(workflow: Workflow): Result<Unit>
    suspend fun getWorkflowById(id: String): Result<Workflow?>
    suspend fun getAllWorkflows(): Result<List<Workflow>>
    suspend fun deleteWorkflow(id: String): Result<Unit>
    suspend fun getWorkflowsByUser(userId: String): Result<List<Workflow>>
    suspend fun hasPermission(userId: String, workflowId: String, permission: Permission): Result<Boolean>
}
```

#### WorkflowExecutionRepository

Repository for workflow execution history.

```kotlin
interface WorkflowExecutionRepository {
    suspend fun saveExecution(execution: WorkflowExecutionResult): Result<Unit>
    suspend fun getExecutionHistory(workflowId: String, limit: Int = 50): Result<List<WorkflowExecutionResult>>
    suspend fun getExecutionById(executionId: String): Result<WorkflowExecutionResult?>
}
```

## 🎛️ Configuration

### ActionConfig

Configuration structure for UI workflow builder.

```kotlin
data class ActionConfig(
    val type: ActionType,
    val displayName: String,
    val config: Map<String, String> = emptyMap()
)
```

### TriggerConfig

Configuration structure for workflow triggers.

```kotlin
data class TriggerConfig(
    val type: TriggerType,
    val displayName: String,
    val config: Map<String, String> = emptyMap()
)
```

### ActionType Enum

```kotlin
enum class ActionType {
    SEND_GMAIL,
    REPLY_GMAIL,
    SEND_TELEGRAM,
    REPLY_TELEGRAM,
    AI_ANALYZE,
    AI_SUMMARIZE,
    AI_TRANSLATE,
    AI_GENERATE_REPLY,
    AI_SMART_SUMMARIZE_AND_FORWARD,
    AI_AUTO_EMAIL_SUMMARIZER,
    DELAY,
    CONDITIONAL
}
```

### TriggerType Enum

```kotlin
enum class TriggerType {
    MANUAL,
    SCHEDULED,
    GMAIL_NEW_EMAIL,
    GMAIL_EMAIL_FROM,
    GMAIL_EMAIL_SUBJECT,
    TELEGRAM_MESSAGE,
    GEOFENCE_ENTER,
    GEOFENCE_EXIT,
    GEOFENCE_DWELL
}
```

## 📊 Validation

### WorkflowValidator

Service for validating workflow configurations.

```kotlin
class WorkflowValidator(private val context: Context) {
    fun validateWorkflow(
        workflow: MultiUserWorkflow,
        userManager: UserManager,
        triggerUserId: String? = null
    ): ValidationResult
    
    fun getValidationSummary(result: ValidationResult): String
}
```

### ValidationResult

```kotlin
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError>,
    val warnings: List<ValidationError>
)

data class ValidationError(
    val message: String,
    val severity: ErrorSeverity
)

enum class ErrorSeverity {
    ERROR, WARNING, INFO
}
```

## 🔄 Execution Flow

### Workflow Execution Process

1. **Trigger Detection** → WorkflowTriggerManager monitors for trigger conditions
2. **Workflow Retrieval** → Get workflow from repository
3. **Validation** → Validate workflow and permissions
4. **Context Creation** → Create execution context with trigger data
5. **Action Execution** → Execute actions sequentially
6. **Result Storage** → Save execution results
7. **Cleanup** → Clean up resources

### Error Handling

All API methods return `Result<T>` for consistent error handling:

```kotlin
when (val result = workflowEngine.executeWorkflow(workflowId, userId, data)) {
    is Result.Success -> {
        // Handle success
        val executionResult = result.getOrThrow()
    }
    is Result.Failure -> {
        // Handle error
        val error = result.exceptionOrNull()
        Log.e(TAG, "Workflow execution failed", error)
    }
}
```

## 🔗 Variable System

### Template Variables

Variables can be used in action parameters using `{{variable_name}}` syntax:

#### Common Variables

- `{{trigger_content}}` - Content from trigger (email body, telegram message, etc.)
- `{{email_subject}}` - Email subject from Gmail triggers
- `{{email_body}}` - Email body from Gmail triggers
- `{{email_from}}` - Sender email from Gmail triggers
- `{{trigger_email_id}}` - Email ID for reply actions
- `{{trigger_user_id}}` - ID of user who triggered workflow
- `{{workflow_id}}` - Current workflow ID
- `{{execution_id}}` - Current execution ID

#### AI Output Variables

- `{{ai_summary}}` - Output from summarization actions
- `{{ai_analysis}}` - Output from analysis actions
- `{{ai_translation}}` - Output from translation actions
- `{{extracted_keywords}}` - Keywords from keyword extraction

### Variable Scope

Variables are available within the execution context and can be:

1. **Set by triggers** - Email data, Telegram message data
2. **Set by actions** - AI processing outputs, custom variables
3. **Used by subsequent actions** - Template replacement in parameters

## 📱 UI Integration

### DynamicWorkflowBuilderActivity

Main activity for building workflows through UI.

```kotlin
class DynamicWorkflowBuilderActivity : ComponentActivity() {
    // Key methods for extending UI
    fun convertActionConfig(config: ActionConfig, users: List<WorkflowUser>): MultiUserAction
    fun validateAction(action: ActionConfig, variables: Map<String, String>): ActionValidationResult
}
```

### ActionOptionCard

Composable component for action configuration.

```kotlin
@Composable
fun ActionOptionCard(
    title: String,
    description: String,
    icon: String,
    onClick: () -> Unit,
    configurable: Boolean = false,
    currentConfig: Map<String, String> = emptyMap(),
    onConfigChange: (String, String) -> Unit = { _, _ -> },
    configFields: List<Pair<String, String>> = emptyList()
)
```

## 🔧 Extension Points

### Custom Actions

To add custom actions:

1. Extend `MultiUserAction` sealed class
2. Add to `ActionType` enum
3. Implement execution logic in `MultiUserWorkflowEngine`
4. Add UI configuration in `DynamicWorkflowBuilderActivity`
5. Add validation in `WorkflowValidator`

### Custom AI Services

Implement AI service interfaces:

```kotlin
interface CustomAIService {
    suspend fun processContent(input: String): Result<ProcessedContent>
}

class CustomAIServiceImpl(
    private val modelManager: ModelManager
) : CustomAIService {
    override suspend fun processContent(input: String): Result<ProcessedContent> {
        // Custom AI processing logic
    }
}
```

### Custom Triggers

Extend trigger system:

```kotlin
data class CustomTrigger(
    val userId: String,
    val customParameter: String
) : MultiUserTrigger()
```

## 📚 Example Implementations

### Complete Workflow Example

```kotlin
// Create workflow with email trigger and auto summarizer
val workflow = MultiUserWorkflow(
    id = "auto-summary-workflow",
    name = "Auto Email Summary",
    description = "Automatically summarize and forward important emails",
    createdBy = "user123",
    triggers = listOf(
        MultiUserTrigger.UserGmailNewEmail(
            userId = "user123",
            condition = GmailIntegrationService.EmailCondition(
                isUnreadOnly = true,
                fromFilter = "important-sender@company.com"
            )
        )
    ),
    actions = listOf(
        MultiUserAction.AIAutoEmailSummarizer(
            forwardToEmails = listOf("manager@company.com"),
            summaryStyle = "structured",
            maxSummaryLength = 200,
            customSubjectPrefix = "[Important Email Summary]"
        )
    )
)

// Execute workflow
val result = workflowEngine.executeWorkflow(
    workflowId = workflow.id,
    triggerUserId = "user123",
    triggerData = mapOf(
        "email_subject" to "Quarterly Report",
        "email_body" to "Please find attached the Q3 report...",
        "email_from" to "finance@company.com"
    )
)
```

### Custom Action Implementation

```kotlin
// 1. Define action
data class CustomNotificationAction(
    val message: String,
    val priority: String = "normal",
    val outputVariable: String = "notification_result"
) : MultiUserAction()

// 2. Add to ActionType
enum class ActionType {
    // ... existing types
    CUSTOM_NOTIFICATION
}

// 3. Implement execution
private suspend fun executeCustomNotification(
    action: MultiUserAction.CustomNotificationAction,
    context: WorkflowExecutionContext
): Result<String> {
    return try {
        // Custom notification logic
        val processedMessage = replaceVariables(action.message, context.variables)
        
        // Send notification (custom implementation)
        notificationService.sendNotification(processedMessage, action.priority)
        
        // Store result
        context.variables[action.outputVariable] = "notification_sent"
        
        Result.success("Custom notification sent: $processedMessage")
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

---

**For more examples and detailed implementation guides, see**:
- [Adding New Actions Guide](./guides/ADDING_NEW_ACTIONS.md)
- [Development Guidelines](./DEVELOPMENT_GUIDELINES.md)
- [Auto Email Summarizer Implementation](./AUTO_EMAIL_SUMMARIZER.md)