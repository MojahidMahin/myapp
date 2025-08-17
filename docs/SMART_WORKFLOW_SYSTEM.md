# Smart Zapier-like Workflow System Implementation

## Overview
This document describes the implementation of an intelligent AI-powered workflow automation system that works like Zapier, integrated into your existing workflow manager. The system uses your local LLM for content summarization and intelligent keyword-based routing.

## Architecture Overview

### SOLID Principles Implementation

**Single Responsibility Principle (SRP)**
- `SummarizationService`: Only handles text summarization using local LLM
- `SmartKeywordExtractor`: Only extracts keywords and finds patterns  
- `SmartForwardingService`: Only handles forwarding logic
- `AISmartSummarizeAndForward`: Orchestrates the smart action

**Strategy Pattern**
- Different keyword matching strategies: exact, fuzzy, semantic, pattern
- Multiple forwarding destination types: email, telegram, user accounts
- Customizable summarization styles: concise, detailed, structured, keyword-focused

**Command Pattern**
- `SummarizationCommand`, `ExtractKeywordsCommand`, `ForwardContentCommand`
- Enables queuing, logging, and background processing

## Core Components

### 1. AISmartSummarizeAndForward Action
**File:** `app/src/main/java/com/localllm/myapplication/data/Workflow.kt`

```kotlin
data class AISmartSummarizeAndForward(
    val triggerContent: String = "{{trigger_content}}", // Content from trigger
    val summarizationStyle: String = "concise", // Style of summarization
    val maxSummaryLength: Int = 150, // Maximum summary length
    val keywordRules: List<KeywordForwardingRule> = emptyList(), // Forwarding rules
    val defaultForwardTo: ForwardingDestination? = null, // Default destination
    val includeOriginalContent: Boolean = false, // Include original with summary
    val summaryOutputVariable: String = "ai_summary", // Output variable name
    val keywordsOutputVariable: String = "extracted_keywords", // Keywords variable
    val forwardingDecisionVariable: String = "forwarding_decision" // Decision variable
) : MultiUserAction()
```

### 2. SummarizationService
**File:** `app/src/main/java/com/localllm/myapplication/service/ai/SummarizationService.kt`

Handles AI-powered text summarization using your local LLM model:
- Different summarization styles (concise, detailed, structured, keywords-focused)
- Email-specific summarization with metadata extraction
- Urgency level assessment
- Integration with your existing ModelManager

### 3. SmartKeywordExtractor  
**File:** `app/src/main/java/com/localllm/myapplication/service/ai/KeywordExtractor.kt`

Intelligent keyword extraction and matching:
- Multiple matching strategies (exact, fuzzy, semantic, pattern)
- Email action detection (urgent, reply, forward, etc.)
- Context-aware keyword scoring
- String similarity algorithms

### 4. SmartForwardingService
**File:** `app/src/main/java/com/localllm/myapplication/service/ai/SmartForwardingService.kt`

Rule-based intelligent forwarding:
- Keyword rule processing
- Multiple destination types
- Template variable substitution
- Fallback destination handling

## How It Works

### Workflow Execution Flow

1. **Trigger Fires** 
   - New Gmail email, Telegram message, etc.
   - Content extracted from trigger data

2. **AI Summarization**
   - Your local LLM creates intelligent summary
   - Configurable style and length
   - Urgency and sentiment analysis

3. **Keyword Analysis**
   - Extracts important keywords from content + summary
   - Multiple matching strategies available
   - Context-aware relevance scoring

4. **Rule Matching**
   - Evaluates keyword rules by priority
   - Finds best matching destination
   - Supports minimum match thresholds

5. **Smart Forwarding**
   - Generates appropriate actions (email, telegram)
   - Substitutes template variables
   - Executes forwarding actions

## Configuration Examples

### Basic Email Routing
```kotlin
val workflow = MultiUserWorkflow(
    name = "Smart Email Router",
    triggers = listOf(
        MultiUserTrigger.UserGmailNewEmail(userId = "user123")
    ),
    actions = listOf(
        MultiUserAction.AISmartSummarizeAndForward(
            triggerContent = "{{email_body}}",
            summarizationStyle = "structured",
            keywordRules = listOf(
                // Route urgent emails to support
                KeywordForwardingRule(
                    keywords = listOf("urgent", "emergency", "critical"),
                    destination = ForwardingDestination.EmailDestination("support@company.com"),
                    priority = 1
                ),
                // Route technical issues to engineering
                KeywordForwardingRule(
                    keywords = listOf("bug", "error", "crash", "technical"),
                    destination = ForwardingDestination.EmailDestination("engineering@company.com"),
                    priority = 2
                )
            ),
            defaultForwardTo = ForwardingDestination.EmailDestination("general@company.com")
        )
    )
)
```

### Simple Configuration Format
For UI configuration, you can use simple string format:
```
urgent,asap,critical->support@company.com|bug,error->tech@company.com
```

## Integration Points

### 1. WorkflowEngine Integration
**File:** `app/src/main/java/com/localllm/myapplication/service/MultiUserWorkflowEngine.kt`

The `executeSmartSummarizeAndForward` method handles:
- Service initialization
- Content processing
- Result variable storage
- Forwarding action execution

### 2. UI Integration  
**File:** `app/src/main/java/com/localllm/myapplication/ui/DynamicWorkflowBuilderActivity.kt`

- Added `AI_SMART_SUMMARIZE_AND_FORWARD` to ActionType enum
- Configuration parsing in `convertActionConfig`
- Helper functions for rule parsing

### 3. Data Models
**File:** `app/src/main/java/com/localllm/myapplication/data/Workflow.kt`

Supporting data classes:
- `KeywordForwardingRule`: Defines keyword matching rules
- `ForwardingDestination`: Sealed class for different destination types
- `EmailSummary`: Structured email summary with metadata

## Template Variables

The system supports extensive template variable substitution:

### Standard Variables
- `{{trigger_content}}` - Content from trigger
- `{{ai_summary}}` - Generated summary
- `{{extracted_keywords}}` - Comma-separated keywords
- `{{original_content}}` - Original trigger content
- `{{timestamp}}` - Current timestamp

### Email-Specific Variables
- `{{email_subject}}` - Email subject
- `{{email_body}}` - Email body
- `{{email_from}}` - Sender address
- `{{original_subject}}` - For reply subjects

### Telegram-Specific Variables
- `{{telegram_message}}` - Message content
- `{{telegram_user}}` - Username
- `{{telegram_chat_id}}` - Chat ID

## Background Processing

The system runs entirely in the background as part of your existing workflow engine:
- No UI blocking operations
- Automatic error handling and retries
- Full logging for debugging
- Integration with existing permission system

## Benefits

✅ **Uses Your Local LLM** - All processing on-device  
✅ **Intelligent Analysis** - Smart keyword extraction and matching  
✅ **Rule-Based Logic** - Complex conditional routing  
✅ **Background Processing** - Seamless automation  
✅ **Multiple Platforms** - Email, Telegram, user accounts  
✅ **Extensible Design** - Easy to add new destinations/rules  
✅ **Template System** - Flexible content formatting  

## Files Created/Modified

### New Files
- `app/src/main/java/com/localllm/myapplication/service/ai/SummarizationService.kt`
- `app/src/main/java/com/localllm/myapplication/service/ai/KeywordExtractor.kt`  
- `app/src/main/java/com/localllm/myapplication/service/ai/SmartForwardingService.kt`

### Modified Files
- `app/src/main/java/com/localllm/myapplication/data/Workflow.kt` - Added smart action and data models
- `app/src/main/java/com/localllm/myapplication/service/MultiUserWorkflowEngine.kt` - Added execution logic
- `app/src/main/java/com/localllm/myapplication/ui/DynamicWorkflowBuilderActivity.kt` - Added UI support

## Next Steps

To complete the implementation:
1. Fix remaining compilation errors in WorkflowValidator.kt
2. Resolve interface signature mismatches  
3. Add UI configuration dialogs
4. Test end-to-end workflow execution

The core Zapier-like automation system is architecturally complete and ready for use once compilation issues are resolved.