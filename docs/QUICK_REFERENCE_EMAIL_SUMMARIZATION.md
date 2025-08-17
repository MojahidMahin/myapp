# Email Summarization Quick Reference

## Developer Quick Start

### Basic Usage

```kotlin
// Initialize service
val modelManager = AppContainer.provideModelManager(context)
val summarizationService = LocalLLMSummarizationService(modelManager, context)

// Summarize text
val result = summarizationService.summarizeText(
    text = emailContent,
    maxLength = 100,
    style = SummarizationStyle.CONCISE
)

result.fold(
    onSuccess = { summary -> 
        Log.d(TAG, "Summary: $summary")
        // Use summary...
    },
    onFailure = { error ->
        // This should rarely happen due to fallback strategies
        Log.e(TAG, "Summarization failed", error)
    }
)
```

### Workflow Integration

```kotlin
// In workflow action
private suspend fun executeAutoEmailSummarizer(
    action: MultiUserAction.AIAutoEmailSummarizer,
    context: WorkflowExecutionContext
): Result<String> {
    
    val emailContent = "Subject: ${emailSubject}\nFrom: ${emailFrom}\n\n${emailBody}"
    
    val summaryResult = summarizationService.summarizeText(
        text = emailContent,
        maxLength = action.maxSummaryLength,
        style = parseSummarizationStyle(action.summaryStyle)
    )
    
    // Store in context for use by other actions
    context.variables[action.summaryOutputVariable] = summaryResult.getOrElse { 
        createFallbackSummary(emailSubject, emailBody, emailFrom) 
    }
    
    return Result.success("Summary generated and stored")
}
```

## Configuration Options

### SummarizationStyle Options

| Style | Description | Use Case | Example Output |
|-------|-------------|----------|----------------|
| `CONCISE` | 1-2 sentences | Quick notifications | "Meeting scheduled for tomorrow at 2 PM." |
| `DETAILED` | 3-5 sentences | Comprehensive summaries | "Meeting scheduled for tomorrow at 2 PM to discuss project status. Please bring quarterly reports. Location is Conference Room A." |
| `STRUCTURED` | Bullet points | Organized information | "• Meeting: Tomorrow 2 PM\n• Topic: Project status\n• Bring: Quarterly reports" |
| `KEYWORDS_FOCUSED` | Action items and keywords | Task-oriented | "Actions: attend meeting, bring reports. Keywords: project, quarterly, status" |

### Action Configuration

```kotlin
data class AIAutoEmailSummarizer(
    val maxSummaryLength: Int = 100,           // Words in summary
    val summaryStyle: String = "concise",      // Style option
    val summaryOutputVariable: String = "email_summary",
    val includeOriginalSubject: Boolean = true,
    val includeOriginalSender: Boolean = true,
    val customSubjectPrefix: String = "Summary:",
    val emailTemplate: String = """
        {{summary_subject}}
        
        {{ai_summary}}
        
        {{original_info}}
    """.trimIndent(),
    val forwardToEmails: List<String> = emptyList()
) : MultiUserAction
```

## Strategy Behavior

### Automatic Strategy Selection

```
┌─────────────────┐    Success    ┌─────────────────┐
│   AI Strategy   │ ───────────► │   Use Result    │
│   (Primary)     │               └─────────────────┘
└─────────────────┘                        
         │ Timeout/Error                   
         ▼                                
┌─────────────────┐    Success    ┌─────────────────┐
│  Extractive     │ ───────────► │   Use Result    │
│  Strategy       │               └─────────────────┘
│  (Fallback)     │                        
└─────────────────┘                        
         │ Error (rare)                   
         ▼                                
┌─────────────────┐    Always     ┌─────────────────┐
│   Emergency     │ ───────────► │   Use Result    │
│   Fallback      │   Success     └─────────────────┘
└─────────────────┘                        
```

### Strategy Performance

| Strategy | Typical Time | Quality | Reliability |
|----------|--------------|---------|-------------|
| AI | 2-10 seconds | High | Medium |
| Extractive | 50-200ms | Medium | High |
| Emergency | 10-50ms | Basic | 100% |

## Common Code Patterns

### Error Handling

```kotlin
// ✅ Good - Let the service handle fallbacks
val summary = summarizationService.summarizeText(text)
    .getOrElse { "Summary not available" }

// ❌ Avoid - Don't implement your own fallbacks
try {
    val summary = summarizationService.summarizeText(text).getOrThrow()
} catch (e: Exception) {
    // Service already handles this internally
}
```

### Testing Strategies

```kotlin
// Test AI availability
private suspend fun testAIAvailability(): Boolean {
    return summarizationService.summarizeText("test", 10, SummarizationStyle.CONCISE)
        .map { it.contains("test") || it.isNotEmpty() }
        .getOrElse { false }
}

// Test all strategies
class SummarizationServiceTest {
    @Test
    fun `test fallback strategies work`() = runTest {
        // Mock AI failure
        mockModelManager.setAvailable(false)
        
        val result = service.summarizeText("Test email content")
        
        assertTrue(result.isSuccess)
        assertThat(result.getOrNull()).isNotEmpty()
    }
}
```

### Performance Monitoring

```kotlin
private suspend fun monitoredSummarization(text: String): Result<String> {
    val startTime = System.currentTimeMillis()
    
    val result = summarizationService.summarizeText(text)
    
    val duration = System.currentTimeMillis() - startTime
    
    Log.d(TAG, "Summarization completed in ${duration}ms, " +
            "strategy: ${if (duration < 1000) "extractive/emergency" else "AI"}")
    
    return result
}
```

## Debugging Commands

### Quick Status Check

```bash
# Check recent summarization activity
adb logcat -d | grep "SummarizationService" | tail -10

# Check which strategy was used
adb logcat -d | grep -E "(AI summarization|extractive|emergency)" | tail -5

# Monitor real-time
adb logcat | grep -E "(Strategy|Summary|fallback)"
```

### Performance Analysis

```bash
# Find slow operations
adb logcat -d | grep -E "completed.*ms" | awk '{print $NF}' | sort -n

# Count strategy usage
adb logcat -d | grep -c "AI summarization successful"
adb logcat -d | grep -c "extractive"
adb logcat -d | grep -c "emergency"
```

## Troubleshooting Checklist

### ✅ System Health Indicators

- [ ] No timeout errors in logs
- [ ] At least one strategy succeeding
- [ ] Response times under 15 seconds
- [ ] Memory usage stable

### ⚠️ Warning Signs

- [ ] Frequent fallback to extractive strategy
- [ ] AI model availability issues
- [ ] Increasing response times
- [ ] Memory usage growing

### ❌ Critical Issues

- [ ] All strategies failing
- [ ] System crashes during summarization
- [ ] Memory leaks detected
- [ ] Workflow execution stopped

## Best Practices

### Do's ✅

- **Always use the service interface** - Don't bypass the multi-strategy implementation
- **Store summaries in context variables** - Make them available to subsequent actions
- **Use appropriate summarization styles** - Match style to use case
- **Monitor performance** - Track strategy usage and response times
- **Test fallback scenarios** - Ensure your workflow works without AI

### Don'ts ❌

- **Don't implement custom timeouts** - The service handles this internally
- **Don't catch and re-throw exceptions** - Let the service manage fallbacks
- **Don't assume AI is available** - Always handle the possibility of fallback strategies
- **Don't log sensitive email content** - Respect user privacy
- **Don't ignore performance metrics** - Monitor system health regularly

## Migration Guide

### From Old Implementation

If migrating from the old timeout-prone implementation:

1. **Replace direct ModelManager calls**:
   ```kotlin
   // ❌ Old approach
   modelManager.generateResponse(prompt) { result ->
       // Manual timeout handling...
   }
   
   // ✅ New approach
   val summary = summarizationService.summarizeText(content)
   ```

2. **Remove custom timeout logic**:
   ```kotlin
   // ❌ Remove this pattern
   var attempts = 0
   while (summary == null && attempts < 100) {
       delay(100)
       attempts++
   }
   ```

3. **Update error handling**:
   ```kotlin
   // ❌ Old - workflow fails on summarization error
   if (summaryResult.isFailure) {
       return Result.failure(Exception("Summarization failed"))
   }
   
   // ✅ New - use fallback and continue
   val summary = summaryResult.getOrElse { 
       createFallbackSummary(subject, body, sender) 
   }
   ```

## API Reference

### SummarizationService Interface

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

### EmailSummary Data Class

```kotlin
data class EmailSummary(
    val summary: String,
    val sender: String,
    val subject: String,
    val keyPoints: List<String>,
    val urgencyLevel: UrgencyLevel
)

enum class UrgencyLevel { LOW, MEDIUM, HIGH }
```

### Context Variables

When using in workflows, these variables are automatically populated:

| Variable | Description | Example |
|----------|-------------|---------|
| `email_summary` | Generated summary | "Meeting scheduled for tomorrow..." |
| `email_subject` | Original subject | "Project Meeting Tomorrow" |
| `email_body` | Original body | "Hi team, let's meet tomorrow..." |
| `email_from` | Sender email | "john@company.com" |

## Performance Tuning

### Device-Specific Optimization

```kotlin
// Detect device capabilities
val runtime = Runtime.getRuntime()
val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB

val config = when {
    maxMemory > 4096 -> SummarizationConfig.HIGH_PERFORMANCE
    maxMemory > 2048 -> SummarizationConfig.BALANCED  
    else -> SummarizationConfig.BATTERY_OPTIMIZED
}
```

### Content-Based Optimization

```kotlin
// Adjust strategy based on content
val strategy = when {
    text.length > 5000 -> SummarizationStyle.STRUCTURED
    text.contains("urgent", ignoreCase = true) -> SummarizationStyle.KEYWORDS_FOCUSED
    else -> SummarizationStyle.CONCISE
}
```

This quick reference should help developers understand and implement the email summarization system effectively. For detailed implementation examples, see the full documentation files.