# Adding New Workflow Actions - Step-by-Step Guide

**Last Updated**: August 17, 2025  
**Difficulty**: Intermediate  
**Estimated Time**: 2-4 hours

## 📋 Overview

This guide provides a comprehensive step-by-step process for adding new workflow actions to the LocalLLM Workflow System. Follow these steps to ensure proper integration with existing infrastructure.

## 🎯 Prerequisites

- Understanding of Kotlin and Android development
- Familiarity with the workflow system architecture
- Knowledge of SOLID design principles
- Local development environment set up

## 🔧 Step-by-Step Implementation

### Step 1: Define the Data Model

**File**: `/app/src/main/java/com/localllm/myapplication/data/Workflow.kt`

Add your new action to the `MultiUserAction` sealed class:

```kotlin
/**
 * Your new action description
 * Explain what this action does and when to use it
 */
data class YourNewAction(
    val parameter1: String,                    // Required parameter
    val parameter2: String = "default_value", // Optional parameter with default
    val parameter3: Int = 100,                 // Numeric parameter
    val enableFeature: Boolean = true,         // Boolean parameter
    val outputVariable: String = "action_output" // Output variable name
) : MultiUserAction()
```

**Example** (Based on Auto Email Summarizer):
```kotlin
data class AIAutoEmailSummarizer(
    val forwardToEmails: List<String>,
    val summaryStyle: String = "concise",
    val maxSummaryLength: Int = 200,
    val includeOriginalSubject: Boolean = true,
    val includeOriginalSender: Boolean = true,
    val customSubjectPrefix: String = "[Summary]",
    val summaryOutputVariable: String = "email_summary"
) : MultiUserAction()
```

### Step 2: Add Action Type

**File**: `/app/src/main/java/com/localllm/myapplication/ui/DynamicWorkflowBuilderActivity.kt`

Add to the `ActionType` enum:

```kotlin
enum class ActionType {
    SEND_GMAIL,
    REPLY_GMAIL,
    // ... existing types
    YOUR_NEW_ACTION,  // Add your action here
    DELAY,
    CONDITIONAL
}
```

### Step 3: Implement Execution Logic

**File**: `/app/src/main/java/com/localllm/myapplication/service/MultiUserWorkflowEngine.kt`

#### 3a. Add to execution switch

Find the `executeAction` method and add your case:

```kotlin
private suspend fun executeAction(action: MultiUserAction, context: WorkflowExecutionContext): Result<String> {
    return try {
        val result = when (action) {
            // ... existing cases
            is MultiUserAction.YourNewAction -> executeYourNewAction(action, context)
            // ... other cases
        }
        result
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

#### 3b. Implement execution method

Add the execution method to the same file:

```kotlin
/**
 * Execute your new action
 * Describe what this method does and any important details
 */
private suspend fun executeYourNewAction(
    action: MultiUserAction.YourNewAction,
    context: WorkflowExecutionContext
): Result<String> {
    return try {
        Log.d(TAG, "Executing YourNewAction")
        
        // 1. Extract and validate parameters
        val param1 = action.parameter1
        if (param1.isBlank()) {
            return Result.failure(Exception("Parameter1 cannot be blank"))
        }
        
        // 2. Get any required services
        val someService = getSomeRequiredService()
        if (someService == null) {
            return Result.failure(Exception("Required service not available"))
        }
        
        // 3. Perform the main action logic
        val result = performMainLogic(param1, action.parameter2, someService)
        
        // 4. Store output in context if needed
        if (result.isSuccess) {
            context.variables[action.outputVariable] = result.getOrThrow()
        }
        
        // 5. Return success with descriptive message
        Log.i(TAG, "YourNewAction completed successfully")
        Result.success("Action completed successfully: ${result.getOrThrow()}")
        
    } catch (e: Exception) {
        Log.e(TAG, "Error in executeYourNewAction", e)
        Result.failure(Exception("YourNewAction failed: ${e.message}"))
    }
}

/**
 * Helper method for main logic
 */
private suspend fun performMainLogic(
    param1: String, 
    param2: String, 
    service: SomeService
): Result<String> {
    // Implement your main logic here
    return try {
        val processedResult = service.processData(param1, param2)
        Result.success(processedResult)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### Step 4: Add Workflow Validator Support

**File**: `/app/src/main/java/com/localllm/myapplication/service/WorkflowValidator.kt`

Add validation in **three** locations:

#### 4a. General action validation (around line 550)

```kotlin
when (action) {
    // ... existing cases
    is MultiUserAction.YourNewAction -> {
        // Validate parameters
        if (action.parameter1.isBlank()) {
            errors.add(ValidationError(
                "Action at position $index: parameter1 cannot be blank",
                ErrorSeverity.ERROR
            ))
        }
        
        // Add any other validation logic
        Log.d(TAG, "Validated action: ${action::class.simpleName}")
    }
    // ... other cases
}
```

#### 4b. Variable extraction (around line 650)

```kotlin
when (action) {
    // ... existing cases
    is MultiUserAction.YourNewAction -> {
        // Extract variables from parameters that support templates
        extractVariables(action.parameter1, usedVariables)
        extractVariables(action.parameter2, usedVariables)
        
        // Add output variable
        definedVariables.add(action.outputVariable)
    }
    // ... other cases
}
```

#### 4c. AI output tracking (if AI-related, around line 730)

```kotlin
when (action) {
    // ... existing cases
    is MultiUserAction.YourNewAction -> {
        // Only add if this action produces AI-related output
        aiOutputVariables.add(action.outputVariable)
    }
    // ... other cases
}
```

### Step 5: Add UI Configuration

**File**: `/app/src/main/java/com/localllm/myapplication/ui/DynamicWorkflowBuilderActivity.kt`

#### 5a. Add to convertActionConfig function (around line 961)

```kotlin
fun convertActionConfig(config: ActionConfig, users: List<WorkflowUser>): MultiUserAction {
    return when (config.type) {
        // ... existing cases
        ActionType.YOUR_NEW_ACTION -> {
            MultiUserAction.YourNewAction(
                parameter1 = config.config["parameter1"] ?: "",
                parameter2 = config.config["parameter2"] ?: "default_value",
                parameter3 = config.config["parameter3"]?.toIntOrNull() ?: 100,
                enableFeature = config.config["enableFeature"]?.toBooleanStrictOrNull() ?: true,
                outputVariable = config.config["outputVar"] ?: "action_output"
            )
        }
        // ... other cases
    }
}
```

#### 5b. Add to action picker dialog (around line 1640)

Find the appropriate category ("Communication", "AI Processing", or "Utility") and add:

```kotlin
when (selectedCategory) {
    "AI Processing" -> { // or appropriate category
        // ... existing items
        item {
            ActionOptionCard(
                title = "Your New Action",
                description = "Brief description of what your action does",
                icon = "🔧", // Choose appropriate emoji
                onClick = {
                    val param1 = actionConfig["parameter1"] ?: ""
                    onActionSelected(ActionConfig(
                        type = ActionType.YOUR_NEW_ACTION,
                        displayName = "Your Action: $param1",
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
                    "parameter2" to "Parameter 2 description (optional)",
                    "parameter3" to "Parameter 3 (number)",
                    "enableFeature" to "Enable feature (true/false)",
                    "outputVar" to "Output variable name"
                )
            )
        }
    }
}
```

#### 5c. Add to UI display helpers

Add to icon mapping (around line 2220):
```kotlin
val icon = when (action.type) {
    // ... existing cases
    ActionType.YOUR_NEW_ACTION -> "🔧"
    // ... other cases
}
```

Add to description mapping (around line 2250):
```kotlin
text = when (action.type) {
    // ... existing cases
    ActionType.YOUR_NEW_ACTION -> "Your action description"
    // ... other cases
}
```

### Step 6: Add Action Validation

**File**: `/app/src/main/java/com/localllm/myapplication/ui/DynamicWorkflowBuilderActivity.kt`

Add validation case (around line 2800):

```kotlin
fun validateAction(action: ActionConfig, variables: Map<String, String>): ActionValidationResult {
    return when (action.type) {
        // ... existing cases
        ActionType.YOUR_NEW_ACTION -> {
            val param1 = action.config["parameter1"]
            val param3 = action.config["parameter3"]?.toIntOrNull() ?: 100
            val outputVar = action.config["outputVar"] ?: "action_output"
            
            when {
                param1.isNullOrBlank() -> ActionValidationResult(
                    message = "⚠️ Parameter 1 is required",
                    status = WorkflowTestStatus.WARNING
                )
                param3 <= 0 -> ActionValidationResult(
                    message = "⚠️ Parameter 3 must be positive",
                    status = WorkflowTestStatus.WARNING
                )
                else -> ActionValidationResult(
                    message = "✓ Will execute your action with parameter: $param1",
                    status = WorkflowTestStatus.SUCCESS,
                    output = mapOf(outputVar to "Sample output value"),
                    details = mapOf(
                        "parameter1" to param1,
                        "parameter3" to param3,
                        "outputVariable" to outputVar
                    )
                )
            }
        }
        // ... other cases
    }
}
```

### Step 7: Update Platform Detection (if applicable)

**File**: `/app/src/main/java/com/localllm/myapplication/ui/DynamicWorkflowBuilderActivity.kt`

If your action uses specific platforms, add to platform detection (around line 3000):

```kotlin
actions.forEach { action ->
    when (action.type) {
        ActionType.SEND_GMAIL, ActionType.REPLY_GMAIL -> platforms.add(WorkflowPlatform.GMAIL)
        ActionType.SEND_TELEGRAM, ActionType.REPLY_TELEGRAM -> platforms.add(WorkflowPlatform.TELEGRAM)
        ActionType.AI_ANALYZE, ActionType.AI_SUMMARIZE, ActionType.YOUR_NEW_ACTION -> {
            platforms.add(WorkflowPlatform.AI) // if AI-related
        }
        else -> {}
    }
}
```

## 🧪 Testing Your Implementation

### Step 8: Manual Testing

1. **Build the project**:
   ```bash
   ./gradlew compileDebugKotlin
   ```

2. **Test UI configuration**:
   - Open Workflow Manager
   - Create new workflow
   - Add your action
   - Verify configuration fields appear
   - Test validation

3. **Test execution**:
   - Create complete workflow with trigger
   - Execute workflow manually
   - Check logs for execution flow
   - Verify output variables

### Step 9: Unit Testing

Create test file: `/app/src/test/java/com/localllm/myapplication/YourNewActionTest.kt`

```kotlin
class YourNewActionTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockWorkflowEngine: MultiUserWorkflowEngine
    
    @Test
    fun `executeYourNewAction should succeed with valid parameters`() = runTest {
        // Given
        val action = MultiUserAction.YourNewAction(
            parameter1 = "test_value",
            parameter2 = "custom_value",
            parameter3 = 150
        )
        val context = WorkflowExecutionContext(
            workflowId = "test_workflow",
            triggerUserId = "user1",
            triggerData = "test_data"
        )
        
        // When
        val result = mockWorkflowEngine.executeYourNewAction(action, context)
        
        // Then
        assertTrue(result.isSuccess)
        assertNotNull(context.variables[action.outputVariable])
    }
    
    @Test
    fun `executeYourNewAction should fail with blank parameter1`() = runTest {
        // Given
        val action = MultiUserAction.YourNewAction(parameter1 = "")
        val context = WorkflowExecutionContext("workflow", "user", "data")
        
        // When
        val result = mockWorkflowEngine.executeYourNewAction(action, context)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Parameter1 cannot be blank") == true)
    }
}
```

## 📋 Checklist

Before considering your implementation complete, verify:

- [ ] Data model added to `Workflow.kt`
- [ ] Action type added to enum
- [ ] Execution logic implemented in `MultiUserWorkflowEngine.kt`
- [ ] All three validation cases added to `WorkflowValidator.kt`
- [ ] UI configuration added to action picker dialog
- [ ] Action conversion logic implemented
- [ ] UI display helpers updated (icon, description)
- [ ] Action validation implemented
- [ ] Platform detection updated (if applicable)
- [ ] Project builds successfully
- [ ] Manual testing completed
- [ ] Unit tests written and passing
- [ ] Documentation updated

## 🎯 Common Patterns

### Parameter Validation Pattern

```kotlin
private fun validateParameters(action: MultiUserAction.YourNewAction): Result<Unit> {
    return when {
        action.parameter1.isBlank() -> 
            Result.failure(Exception("Parameter1 is required"))
        action.parameter3 <= 0 -> 
            Result.failure(Exception("Parameter3 must be positive"))
        else -> Result.success(Unit)
    }
}
```

### Service Integration Pattern

```kotlin
private suspend fun executeWithService(
    action: MultiUserAction.YourNewAction,
    context: WorkflowExecutionContext
): Result<String> {
    // Get service from dependency injection
    val service = getRequiredService() 
        ?: return Result.failure(Exception("Service not available"))
    
    // Use service to perform action
    return service.performOperation(action.parameter1)
        .map { result -> "Operation completed: $result" }
}
```

### Template Variable Pattern

```kotlin
// For parameters that support template variables like {{email_subject}}
private fun processTemplateParameter(
    template: String, 
    context: WorkflowExecutionContext
): String {
    var result = template
    for ((key, value) in context.variables) {
        result = result.replace("{{$key}}", value)
    }
    return result
}
```

## 🚀 Next Steps

After implementing your action:

1. **Test thoroughly** with different configurations
2. **Document your action** in the main documentation
3. **Add to workflow templates** if applicable
4. **Consider performance implications** for large-scale usage
5. **Plan for future enhancements** and extensibility

## 📚 References

- [Development Guidelines](../DEVELOPMENT_GUIDELINES.md)
- [Architecture Overview](../technical/ARCHITECTURE.md)
- [Auto Email Summarizer Example](../AUTO_EMAIL_SUMMARIZER.md)
- [API Reference](../API_REFERENCE.md)

---

**Need Help?** Refer to the existing action implementations in the codebase for real-world examples, or check the architecture documentation for system overview.