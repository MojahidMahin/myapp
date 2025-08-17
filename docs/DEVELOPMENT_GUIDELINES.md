# Development Guidelines

**Last Updated**: August 17, 2025  
**Version**: 1.0.0

## 📋 Overview

This document provides comprehensive guidelines for developing new features and maintaining the LocalLLM Workflow System. Follow these practices to ensure consistency, quality, and maintainability.

## 🏗️ Architecture Principles

### SOLID Principles

Our workflow system is built following SOLID design principles:

- **Single Responsibility**: Each class has one reason to change
- **Open/Closed**: Open for extension, closed for modification
- **Liskov Substitution**: Subtypes must be substitutable for base types
- **Interface Segregation**: Many client-specific interfaces over one general-purpose interface
- **Dependency Inversion**: Depend on abstractions, not concretions

### Design Patterns Used

1. **Command Pattern**: All workflow actions implement command pattern
2. **Strategy Pattern**: Different summarization and forwarding strategies
3. **Repository Pattern**: Data access abstraction
4. **Observer Pattern**: Workflow execution monitoring
5. **Builder Pattern**: Complex workflow construction

## 🔧 Adding New Features

### 1. Feature Planning Phase

Before implementing any new feature:

1. **Document Requirements**
   - Create feature specification document
   - Define user stories and acceptance criteria
   - Identify integration points with existing system

2. **Design Review**
   - Follow existing architectural patterns
   - Ensure SOLID principles compliance
   - Plan for testing and validation

3. **Impact Assessment**
   - Identify affected components
   - Plan backward compatibility
   - Consider performance implications

### 2. Implementation Structure

#### For New Workflow Actions

```kotlin
// 1. Define data model in Workflow.kt
data class YourNewAction(
    val parameter1: String,
    val parameter2: Int = defaultValue,
    val outputVariable: String = "default_output"
) : MultiUserAction()

// 2. Add to ActionType enum in DynamicWorkflowBuilderActivity.kt
enum class ActionType {
    // ... existing types
    YOUR_NEW_ACTION,
    // ... other types
}

// 3. Implement execution logic in MultiUserWorkflowEngine.kt
private suspend fun executeYourNewAction(
    action: MultiUserAction.YourNewAction,
    context: WorkflowExecutionContext
): Result<String> {
    // Implementation here
}

// 4. Add to execution switch
is MultiUserAction.YourNewAction -> executeYourNewAction(action, context)
```

#### For New AI Services

```kotlin
// 1. Create service interface
interface YourAIService {
    suspend fun processContent(input: String): Result<ProcessedContent>
}

// 2. Implement service
class YourAIServiceImpl(
    private val modelManager: ModelManager,
    private val context: Context
) : YourAIService {
    override suspend fun processContent(input: String): Result<ProcessedContent> {
        // Implementation using local LLM
    }
}

// 3. Register in dependency injection
// Add to AppContainer.kt
```

### 3. Validation and Testing

#### Workflow Validator Integration

Always add validation for new actions:

```kotlin
// In WorkflowValidator.kt
when (action) {
    // Add to all three validation locations:
    // 1. General validation (around line 550)
    // 2. Variable extraction (around line 650)  
    // 3. AI output tracking (around line 730)
    is MultiUserAction.YourNewAction -> {
        // Validation logic
    }
}
```

#### UI Integration

Add UI configuration for new actions:

```kotlin
// In DynamicWorkflowBuilderActivity.kt ActionPickerDialog
item {
    ActionOptionCard(
        title = "Your New Action",
        description = "Description of what it does",
        icon = "🔧",
        onClick = {
            onActionSelected(ActionConfig(
                type = ActionType.YOUR_NEW_ACTION,
                displayName = "Display Name",
                config = actionConfig
            ))
        },
        configurable = true,
        currentConfig = actionConfig,
        onConfigChange = { key, value ->
            actionConfig = actionConfig + (key to value)
        },
        configFields = listOf(
            "parameter1" to "Parameter 1 description",
            "parameter2" to "Parameter 2 description"
        )
    )
}
```

## 🎯 Code Quality Standards

### Kotlin Coding Standards

```kotlin
// ✅ Good: Clear, descriptive names
class EmailSummarizationService(
    private val modelManager: ModelManager,
    private val configurationProvider: ConfigurationProvider
) {
    suspend fun summarizeEmail(email: EmailData): Result<EmailSummary> {
        return try {
            val summary = generateSummary(email.content)
            Result.success(summary)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to summarize email", e)
            Result.failure(e)
        }
    }
}

// ❌ Bad: Unclear names, poor error handling
class ESS(private val mm: ModelManager) {
    fun se(e: EmailData): EmailSummary? {
        return try {
            // No logging, poor error handling
            generateSummary(e.content)
        } catch (ex: Exception) {
            null
        }
    }
}
```

### Error Handling

```kotlin
// ✅ Comprehensive error handling
suspend fun processWorkflowAction(action: MultiUserAction): Result<String> {
    return try {
        when (action) {
            is MultiUserAction.EmailAction -> {
                validateEmailAction(action).getOrThrow()
                executeEmailAction(action)
            }
            else -> Result.failure(Exception("Unsupported action type"))
        }
    } catch (e: ValidationException) {
        Log.w(TAG, "Validation failed: ${e.message}")
        Result.failure(e)
    } catch (e: NetworkException) {
        Log.e(TAG, "Network error: ${e.message}")
        Result.failure(e)
    } catch (e: Exception) {
        Log.e(TAG, "Unexpected error in action processing", e)
        Result.failure(e)
    }
}
```

### Logging Standards

```kotlin
class WorkflowExecutionEngine {
    companion object {
        private const val TAG = "WorkflowExecutionEngine"
    }
    
    suspend fun executeWorkflow(workflow: Workflow): Result<ExecutionResult> {
        Log.i(TAG, "Starting workflow execution: ${workflow.id}")
        
        try {
            // Debug level for detailed execution flow
            Log.d(TAG, "Executing ${workflow.actions.size} actions")
            
            workflow.actions.forEachIndexed { index, action ->
                Log.d(TAG, "Executing action $index: ${action::class.simpleName}")
                // ... execution logic
            }
            
            Log.i(TAG, "Workflow execution completed successfully")
            return Result.success(executionResult)
            
        } catch (e: Exception) {
            Log.e(TAG, "Workflow execution failed", e)
            return Result.failure(e)
        }
    }
}
```

## 📊 Testing Guidelines

### Unit Testing

```kotlin
class EmailSummarizationServiceTest {
    
    @Mock
    private lateinit var mockModelManager: ModelManager
    
    @Mock
    private lateinit var mockContext: Context
    
    private lateinit var emailSummarizationService: EmailSummarizationService
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        emailSummarizationService = EmailSummarizationService(mockModelManager, mockContext)
    }
    
    @Test
    fun `summarizeEmail should return success when model responds correctly`() = runTest {
        // Given
        val emailData = EmailData("subject", "body", "sender@test.com")
        whenever(mockModelManager.generateResponse(any())).thenReturn(
            Result.success("Generated summary")
        )
        
        // When
        val result = emailSummarizationService.summarizeEmail(emailData)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals("Generated summary", result.getOrNull())
    }
}
```

### Integration Testing

```kotlin
@Test
fun `workflow execution should complete end-to-end successfully`() = runTest {
    // Given: Complete workflow with email trigger and summarization action
    val workflow = createTestWorkflow()
    val triggerData = createTestEmailData()
    
    // When: Execute workflow
    val result = workflowEngine.executeWorkflow(workflow.id, "user1", triggerData)
    
    // Then: Verify complete execution
    assertTrue(result.isSuccess)
    verify(mockGmailService).sendEmail(any(), any(), any())
    verify(mockSummarizationService).summarizeText(any())
}
```

## 🔒 Security Guidelines

### Input Validation

```kotlin
// ✅ Proper input validation
private fun validateEmailAddress(email: String): Result<String> {
    return when {
        email.isBlank() -> Result.failure(ValidationException("Email cannot be blank"))
        !email.contains("@") -> Result.failure(ValidationException("Invalid email format"))
        email.length > 254 -> Result.failure(ValidationException("Email too long"))
        else -> Result.success(email.trim().lowercase())
    }
}

// ✅ SQL injection prevention (using Room ORM)
@Query("SELECT * FROM workflows WHERE user_id = :userId AND name LIKE :searchTerm")
suspend fun searchWorkflows(userId: String, searchTerm: String): List<WorkflowEntity>
```

### Data Privacy

```kotlin
// ✅ Secure logging - avoid sensitive data
Log.d(TAG, "Processing email from user: ${userId.take(8)}...")

// ❌ Bad: Logging sensitive information
Log.d(TAG, "Processing email: $emailContent with password: $userPassword")
```

### Local Processing

```kotlin
// ✅ Keep AI processing local
class LocalLLMSummarizationService {
    suspend fun summarizeText(text: String): Result<String> {
        // All processing happens locally
        return modelManager.generateResponse(buildPrompt(text))
    }
}

// ❌ Bad: Sending data to external services
suspend fun summarizeText(text: String): Result<String> {
    return httpClient.post("https://external-ai-service.com/summarize") {
        setBody(text) // Violates local processing principle
    }
}
```

## 📱 UI/UX Guidelines

### Compose Best Practices

```kotlin
@Composable
fun WorkflowActionCard(
    action: ActionConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = action.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = action.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

### Accessibility

```kotlin
// ✅ Proper content descriptions
IconButton(
    onClick = onDelete,
    modifier = Modifier.semantics { contentDescription = "Delete workflow action" }
) {
    Icon(Icons.Default.Delete, contentDescription = null)
}

// ✅ Semantic properties for screen readers
Text(
    text = validationMessage,
    modifier = Modifier.semantics { 
        role = Role.Status
        liveRegion = LiveRegionMode.Polite
    }
)
```

## 🔄 Performance Guidelines

### Background Processing

```kotlin
// ✅ Proper coroutine scope management
class WorkflowTriggerManager(
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun start() {
        scope.launch {
            while (isActive) {
                checkTriggers()
                delay(TRIGGER_CHECK_INTERVAL)
            }
        }
    }
    
    fun stop() {
        scope.cancel()
    }
}
```

### Memory Management

```kotlin
// ✅ Efficient resource management
class ModelManager {
    private var currentModel: LLMModel? = null
    
    suspend fun loadModel(modelPath: String): Result<Unit> {
        return try {
            // Clean up previous model
            currentModel?.close()
            
            currentModel = LLMModel.load(modelPath)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun cleanup() {
        currentModel?.close()
        currentModel = null
    }
}
```

## 📝 Documentation Standards

### Code Documentation

```kotlin
/**
 * Service responsible for summarizing email content using local LLM models.
 * 
 * This service provides various summarization styles and integrates with the 
 * workflow system to automatically process triggered emails.
 *
 * @param modelManager The LLM model manager for text generation
 * @param context Android context for resource access
 */
class EmailSummarizationService(
    private val modelManager: ModelManager,
    private val context: Context
) {
    /**
     * Summarizes the given email content using the specified style.
     *
     * @param emailData The email data containing subject, body, and sender
     * @param style The summarization style to use (concise, detailed, structured)
     * @param maxLength Maximum length of the summary in words
     * @return Result containing the generated summary or error information
     */
    suspend fun summarizeEmail(
        emailData: EmailData,
        style: SummarizationStyle = SummarizationStyle.CONCISE,
        maxLength: Int = 150
    ): Result<String>
}
```

### Feature Documentation

When implementing new features, create documentation following this template:

```markdown
# Feature Name

## Overview
Brief description of what the feature does

## Implementation Details
Technical implementation specifics

## Usage Guide
How users interact with the feature

## API Reference
Code examples and API documentation

## Testing
Testing approach and test cases

## Security Considerations
Security implications and mitigations
```

## 🚀 Deployment Guidelines

### Version Control

```bash
# Feature branch naming
git checkout -b feature/auto-email-summarizer
git checkout -b bugfix/workflow-validation-error
git checkout -b improvement/ui-performance

# Commit message format
feat: add auto email summarizer action
fix: resolve workflow validation for new actions
docs: update implementation guidelines
refactor: improve workflow engine performance
```

### Code Review Checklist

- [ ] Follows SOLID principles
- [ ] Includes comprehensive error handling
- [ ] Has appropriate logging
- [ ] Includes unit tests
- [ ] Updates relevant documentation
- [ ] Maintains backward compatibility
- [ ] Follows security guidelines
- [ ] Performance impact assessed

### Release Process

1. **Feature Complete**: All code implemented and tested
2. **Documentation Updated**: All relevant docs updated
3. **Testing Passed**: Unit, integration, and manual tests pass
4. **Code Review Approved**: Peer review completed
5. **Performance Validated**: No significant performance degradation
6. **Security Review**: Security implications assessed

## 🎯 Best Practices Summary

### Do's ✅

- Follow established architectural patterns
- Write comprehensive error handling
- Include detailed logging with appropriate levels
- Create thorough documentation
- Write meaningful unit tests
- Validate all inputs
- Use dependency injection
- Keep AI processing local
- Follow SOLID principles
- Consider backward compatibility

### Don'ts ❌

- Break existing functionality
- Skip error handling
- Log sensitive information
- Send data to external services
- Hardcode configuration values
- Ignore performance implications
- Skip documentation updates
- Write tightly coupled code
- Implement without planning
- Ignore security considerations

---

**Next Steps**: After reading this guide, proceed to the specific implementation guides in the `guides/` directory for detailed instructions on adding new actions, triggers, or AI services.

**Questions?** Refer to the [API Reference](./API_REFERENCE.md) or [Architecture Overview](./technical/ARCHITECTURE.md) for more details.