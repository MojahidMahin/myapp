package com.localllm.myapplication.service

import android.content.Context
import android.util.Log
import com.localllm.myapplication.data.*

/**
 * Validates workflow configurations and provides error recovery
 */
class WorkflowValidator(private val context: Context) {
    
    companion object {
        private const val TAG = "WorkflowValidator"
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<ValidationError> = emptyList(),
        val warnings: List<ValidationWarning> = emptyList()
    )
    
    data class ValidationError(
        val code: String,
        val message: String,
        val severity: Severity = Severity.ERROR,
        val suggestedFix: String? = null
    )
    
    data class ValidationWarning(
        val code: String,
        val message: String,
        val suggestion: String? = null
    )
    
    enum class Severity {
        ERROR, WARNING, INFO
    }
    
    /**
     * Validate a complete workflow
     */
    suspend fun validateWorkflow(workflow: MultiUserWorkflow, userManager: UserManager, triggerUserId: String? = null): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()
        
        Log.d(TAG, "Validating workflow: ${workflow.name}")
        
        try {
            // Basic validation
            validateBasicWorkflow(workflow, errors, warnings)
            
            // Validate triggers
            validateTriggers(workflow.triggers, errors, warnings)
            
            // Validate actions
            validateActions(workflow.actions, errors, warnings, userManager, triggerUserId)
            
            // Validate permissions
            validatePermissions(workflow, errors, warnings)
            
            // Validate variables
            validateVariables(workflow, errors, warnings)
            
            // Cross-reference validation
            validateCrossReferences(workflow, errors, warnings)
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception during workflow validation", e)
            errors.add(
                ValidationError(
                    "VALIDATION_EXCEPTION",
                    "Validation failed with exception: ${e.message}",
                    Severity.ERROR
                )
            )
        }
        
        val isValid = errors.none { it.severity == Severity.ERROR }
        Log.i(TAG, "Workflow validation completed - Valid: $isValid, Errors: ${errors.size}, Warnings: ${warnings.size}")
        
        return ValidationResult(isValid, errors, warnings)
    }
    
    /**
     * Validate basic workflow properties
     */
    private fun validateBasicWorkflow(
        workflow: MultiUserWorkflow,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        // Name validation
        if (workflow.name.isBlank()) {
            errors.add(
                ValidationError(
                    "EMPTY_NAME",
                    "Workflow name cannot be empty",
                    suggestedFix = "Provide a descriptive name for the workflow"
                )
            )
        } else if (workflow.name.length > 100) {
            warnings.add(
                ValidationWarning(
                    "LONG_NAME",
                    "Workflow name is very long (${workflow.name.length} characters)",
                    "Consider using a shorter, more concise name"
                )
            )
        }
        
        // Description validation
        if (workflow.description.isBlank()) {
            warnings.add(
                ValidationWarning(
                    "EMPTY_DESCRIPTION",
                    "Workflow has no description",
                    "Add a description to help others understand the workflow purpose"
                )
            )
        }
        
        // CreatedBy validation
        if (workflow.createdBy.isBlank()) {
            errors.add(
                ValidationError(
                    "INVALID_CREATOR",
                    "Workflow must have a valid creator ID",
                    suggestedFix = "Set the createdBy field to a valid user ID"
                )
            )
        }
        
        // Actions validation
        if (workflow.actions.isEmpty()) {
            errors.add(
                ValidationError(
                    "NO_ACTIONS",
                    "Workflow must have at least one action",
                    suggestedFix = "Add at least one action to the workflow"
                )
            )
        }
        
        // Triggers validation (optional but recommended)
        if (workflow.triggers.isEmpty()) {
            warnings.add(
                ValidationWarning(
                    "NO_TRIGGERS",
                    "Workflow has no triggers - it can only be executed manually",
                    "Add triggers for automated workflow execution"
                )
            )
        }
    }
    
    /**
     * Validate workflow triggers
     */
    private fun validateTriggers(
        triggers: List<MultiUserTrigger>,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        triggers.forEachIndexed { index, trigger ->
            when (trigger) {
                is MultiUserTrigger.UserGmailNewEmail -> {
                    if (trigger.userId.isBlank()) {
                        errors.add(
                            ValidationError(
                                "INVALID_GMAIL_USER",
                                "Gmail trigger at index $index has invalid user ID",
                                suggestedFix = "Specify a valid user ID for the Gmail trigger"
                            )
                        )
                    }
                }
                is MultiUserTrigger.UserGmailEmailReceived -> {
                    if (trigger.userId.isBlank()) {
                        errors.add(
                            ValidationError(
                                "INVALID_GMAIL_USER",
                                "Gmail email received trigger at index $index has invalid user ID",
                                suggestedFix = "Specify a valid user ID for the Gmail trigger"
                            )
                        )
                    }
                }
                is MultiUserTrigger.UserTelegramMessage -> {
                    if (trigger.userId.isBlank()) {
                        errors.add(
                            ValidationError(
                                "INVALID_TELEGRAM_USER",
                                "Telegram trigger at index $index has invalid user ID",
                                suggestedFix = "Specify a valid user ID for the Telegram trigger"
                            )
                        )
                    }
                }
                is MultiUserTrigger.UserTelegramCommand -> {
                    if (trigger.userId.isBlank()) {
                        errors.add(
                            ValidationError(
                                "INVALID_TELEGRAM_USER",
                                "Telegram command trigger at index $index has invalid user ID",
                                suggestedFix = "Specify a valid user ID for the Telegram trigger"
                            )
                        )
                    }
                    if (trigger.command.isBlank()) {
                        errors.add(
                            ValidationError(
                                "INVALID_TELEGRAM_COMMAND",
                                "Telegram command trigger at index $index has empty command",
                                suggestedFix = "Provide a command (e.g., '/start', '/help')"
                            )
                        )
                    }
                }
                is MultiUserTrigger.AnyUserGmailTrigger -> {
                    if (trigger.userIds.isEmpty()) {
                        errors.add(
                            ValidationError(
                                "EMPTY_USER_LIST",
                                "Any user Gmail trigger at index $index has no user IDs",
                                suggestedFix = "Specify at least one user ID"
                            )
                        )
                    }
                }
                is MultiUserTrigger.AnyUserTelegramTrigger -> {
                    if (trigger.userIds.isEmpty()) {
                        errors.add(
                            ValidationError(
                                "EMPTY_USER_LIST",
                                "Any user Telegram trigger at index $index has no user IDs",
                                suggestedFix = "Specify at least one user ID"
                            )
                        )
                    }
                }
                is MultiUserTrigger.ScheduledTrigger -> {
                    // Basic cron validation
                    if (trigger.cronExpression.isBlank()) {
                        errors.add(
                            ValidationError(
                                "INVALID_CRON",
                                "Scheduled trigger at index $index has empty cron expression",
                                suggestedFix = "Provide a valid cron expression (e.g., '0 9 * * *' for daily at 9 AM)"
                            )
                        )
                    }
                }
                is MultiUserTrigger.ManualTrigger -> {
                    if (trigger.name.isBlank()) {
                        warnings.add(
                            ValidationWarning(
                                "UNNAMED_MANUAL_TRIGGER",
                                "Manual trigger at index $index has no name",
                                "Add a descriptive name for better identification"
                            )
                        )
                    }
                }
                is MultiUserTrigger.GeofenceEnterTrigger -> {
                    if (trigger.userId.isBlank()) {
                        errors.add(
                            ValidationError(
                                "INVALID_GEOFENCE_USER",
                                "Geofence enter trigger at index $index has invalid user ID",
                                suggestedFix = "Specify a valid user ID for the geofence trigger"
                            )
                        )
                    }
                    if (trigger.locationName.isBlank()) {
                        warnings.add(
                            ValidationWarning(
                                "UNNAMED_LOCATION",
                                "Geofence enter trigger at index $index has no location name",
                                "Add a descriptive location name"
                            )
                        )
                    }
                    if (trigger.radiusMeters <= 0) {
                        errors.add(
                            ValidationError(
                                "INVALID_GEOFENCE_RADIUS",
                                "Geofence enter trigger at index $index has invalid radius: ${trigger.radiusMeters}",
                                suggestedFix = "Set a positive radius value (recommended: 50-500 meters)"
                            )
                        )
                    }
                }
                is MultiUserTrigger.GeofenceExitTrigger -> {
                    if (trigger.userId.isBlank()) {
                        errors.add(
                            ValidationError(
                                "INVALID_GEOFENCE_USER",
                                "Geofence exit trigger at index $index has invalid user ID",
                                suggestedFix = "Specify a valid user ID for the geofence trigger"
                            )
                        )
                    }
                    if (trigger.locationName.isBlank()) {
                        warnings.add(
                            ValidationWarning(
                                "UNNAMED_LOCATION",
                                "Geofence exit trigger at index $index has no location name",
                                "Add a descriptive location name"
                            )
                        )
                    }
                    if (trigger.radiusMeters <= 0) {
                        errors.add(
                            ValidationError(
                                "INVALID_GEOFENCE_RADIUS",
                                "Geofence exit trigger at index $index has invalid radius: ${trigger.radiusMeters}",
                                suggestedFix = "Set a positive radius value (recommended: 50-500 meters)"
                            )
                        )
                    }
                }
                is MultiUserTrigger.GeofenceDwellTrigger -> {
                    if (trigger.userId.isBlank()) {
                        errors.add(
                            ValidationError(
                                "INVALID_GEOFENCE_USER",
                                "Geofence dwell trigger at index $index has invalid user ID",
                                suggestedFix = "Specify a valid user ID for the geofence trigger"
                            )
                        )
                    }
                    if (trigger.locationName.isBlank()) {
                        warnings.add(
                            ValidationWarning(
                                "UNNAMED_LOCATION",
                                "Geofence dwell trigger at index $index has no location name",
                                "Add a descriptive location name"
                            )
                        )
                    }
                    if (trigger.radiusMeters <= 0) {
                        errors.add(
                            ValidationError(
                                "INVALID_GEOFENCE_RADIUS",
                                "Geofence dwell trigger at index $index has invalid radius: ${trigger.radiusMeters}",
                                suggestedFix = "Set a positive radius value (recommended: 50-500 meters)"
                            )
                        )
                    }
                    if (trigger.dwellTimeMillis <= 0) {
                        errors.add(
                            ValidationError(
                                "INVALID_DWELL_TIME",
                                "Geofence dwell trigger at index $index has invalid dwell time: ${trigger.dwellTimeMillis}ms",
                                suggestedFix = "Set a positive dwell time (recommended: 60000-1800000ms / 1-30 minutes)"
                            )
                        )
                    }
                }
                else -> {
                    warnings.add(
                        ValidationWarning(
                            "UNKNOWN_TRIGGER_TYPE",
                            "Unknown trigger type at index $index: ${trigger::class.simpleName}",
                            "This trigger type may not be supported"
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Validate workflow actions
     */
    private suspend fun validateActions(
        actions: List<MultiUserAction>,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>,
        userManager: UserManager,
        triggerUserId: String? = null
    ) {
        actions.forEachIndexed { index, action ->
            when (action) {
                is MultiUserAction.SendToUserGmail -> {
                    // Validate user existence, use triggerUserId as fallback
                    val effectiveTargetUserId = if (action.targetUserId.isEmpty() && triggerUserId != null) {
                        triggerUserId
                    } else {
                        action.targetUserId
                    }
                    
                    val user = userManager.getUserById(effectiveTargetUserId).getOrNull()
                    if (user == null) {
                        errors.add(
                            ValidationError(
                                "INVALID_GMAIL_TARGET",
                                "Gmail action at index $index targets non-existent user: $effectiveTargetUserId",
                                suggestedFix = "Verify the target user ID exists"
                            )
                        )
                    } else if (!user.gmailConnected) {
                        warnings.add(
                            ValidationWarning(
                                "GMAIL_NOT_CONNECTED",
                                "Target user for Gmail action at index $index doesn't have Gmail connected",
                                "Ensure the target user has connected their Gmail account"
                            )
                        )
                    }
                    
                    if (action.subject.isBlank()) {
                        warnings.add(
                            ValidationWarning(
                                "EMPTY_EMAIL_SUBJECT",
                                "Gmail action at index $index has empty subject",
                                "Consider adding a descriptive subject line"
                            )
                        )
                    }
                }
                
                is MultiUserAction.SendToUserTelegram -> {
                    // Validate user existence
                    val user = userManager.getUserById(action.targetUserId).getOrNull()
                    if (user == null) {
                        errors.add(
                            ValidationError(
                                "INVALID_TELEGRAM_TARGET",
                                "Telegram action at index $index targets non-existent user: ${action.targetUserId}",
                                suggestedFix = "Verify the target user ID exists"
                            )
                        )
                    } else if (!user.telegramConnected) {
                        warnings.add(
                            ValidationWarning(
                                "TELEGRAM_NOT_CONNECTED",
                                "Target user for Telegram action at index $index doesn't have Telegram connected",
                                "Ensure the target user has connected their Telegram account"
                            )
                        )
                    }
                    
                    if (action.text.isBlank()) {
                        errors.add(
                            ValidationError(
                                "EMPTY_TELEGRAM_TEXT",
                                "Telegram action at index $index has empty text",
                                suggestedFix = "Provide message text for the Telegram action"
                            )
                        )
                    }
                }
                
                is MultiUserAction.ForwardGmailToTelegram -> {
                    // Validate user existence
                    val user = userManager.getUserById(action.targetUserId).getOrNull()
                    if (user == null) {
                        errors.add(
                            ValidationError(
                                "INVALID_FORWARD_TARGET",
                                "Forward Gmail to Telegram action at index $index targets non-existent user: ${action.targetUserId}",
                                suggestedFix = "Verify the target user ID exists"
                            )
                        )
                    } else if (!user.telegramConnected) {
                        warnings.add(
                            ValidationWarning(
                                "TELEGRAM_NOT_CONNECTED_FORWARD",
                                "Target user for Forward Gmail to Telegram action at index $index doesn't have Telegram connected",
                                "Ensure the target user has connected their Telegram account"
                            )
                        )
                    }
                    
                    // Warn if no content will be included
                    if (!action.includeSubject && !action.includeFrom && !action.includeBody) {
                        warnings.add(
                            ValidationWarning(
                                "NO_CONTENT_INCLUDED",
                                "Forward Gmail to Telegram action at index $index has all content options disabled",
                                "Enable at least one of: includeSubject, includeFrom, or includeBody"
                            )
                        )
                    }
                }
                
                is MultiUserAction.AIAnalyzeText -> {
                    if (action.inputText.isBlank()) {
                        errors.add(
                            ValidationError(
                                "EMPTY_AI_INPUT",
                                "AI analysis action at index $index has empty input text",
                                suggestedFix = "Provide input text for AI analysis"
                            )
                        )
                    }
                    
                    if (action.analysisPrompt.isBlank()) {
                        warnings.add(
                            ValidationWarning(
                                "EMPTY_AI_PROMPT",
                                "AI analysis action at index $index has empty analysis prompt",
                                "Add a specific prompt to guide the AI analysis"
                            )
                        )
                    }
                }
                
                is MultiUserAction.DelayAction -> {
                    if (action.delayMinutes <= 0) {
                        errors.add(
                            ValidationError(
                                "INVALID_DELAY",
                                "Delay action at index $index has invalid delay: ${action.delayMinutes}",
                                suggestedFix = "Set delay to a positive number of minutes"
                            )
                        )
                    } else if (action.delayMinutes > 1440) { // More than 24 hours
                        warnings.add(
                            ValidationWarning(
                                "LONG_DELAY",
                                "Delay action at index $index has very long delay: ${action.delayMinutes} minutes",
                                "Consider if such a long delay is intentional"
                            )
                        )
                    }
                }
                
                is MultiUserAction.ConditionalAction -> {
                    if (action.condition.isBlank()) {
                        errors.add(
                            ValidationError(
                                "EMPTY_CONDITION",
                                "Conditional action at index $index has empty condition",
                                suggestedFix = "Provide a condition expression (e.g., 'ai_sentiment == positive')"
                            )
                        )
                    }
                }
                
                is MultiUserAction.ReplyToUserGmail -> {
                    // Validate user existence for reply action, use triggerUserId as fallback
                    val effectiveTargetUserId = if (action.targetUserId.isEmpty() && triggerUserId != null) {
                        triggerUserId
                    } else {
                        action.targetUserId
                    }
                    
                    val user = userManager.getUserById(effectiveTargetUserId).getOrNull()
                    if (user == null) {
                        errors.add(
                            ValidationError(
                                "INVALID_GMAIL_REPLY_TARGET",
                                "Gmail reply action at index $index targets non-existent user: $effectiveTargetUserId",
                                suggestedFix = "Verify the target user ID exists"
                            )
                        )
                    } else if (!user.gmailConnected) {
                        warnings.add(
                            ValidationWarning(
                                "GMAIL_NOT_CONNECTED",
                                "Gmail reply action at index $index targets user without Gmail connection: $effectiveTargetUserId",
                                suggestion = "Connect Gmail account for the target user"
                            )
                        )
                    }
                }
                
                // Add missing action types
                is MultiUserAction.ForwardUserGmail,
                is MultiUserAction.ReplyToUserTelegram,
                is MultiUserAction.ForwardUserTelegram,
                is MultiUserAction.SendToMultipleUsers,
                is MultiUserAction.BroadcastMessage,
                is MultiUserAction.AIGenerateResponse,
                is MultiUserAction.AISummarizeContent,
                is MultiUserAction.AITranslateText,
                is MultiUserAction.AIExtractKeywords,
                is MultiUserAction.AISentimentAnalysis,
                is MultiUserAction.AISmartReply,
                is MultiUserAction.AISmartSummarizeAndForward,
                is MultiUserAction.AIAutoEmailSummarizer,
                is MultiUserAction.RequireApproval,
                is MultiUserAction.LogAction,
                is MultiUserAction.NotificationAction -> {
                    // Generic validation for other action types
                    Log.d(TAG, "Validated action: ${action::class.simpleName}")
                }
            }
        }
    }
    
    /**
     * Validate workflow permissions
     */
    private fun validatePermissions(
        workflow: MultiUserWorkflow,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        if (workflow.isPublic && workflow.sharedWith.isNotEmpty()) {
            warnings.add(
                ValidationWarning(
                    "PUBLIC_WITH_SHARED",
                    "Public workflow also has specific shared users",
                    "Public workflows are accessible to all users - shared users list may be redundant"
                )
            )
        }
        
        if (workflow.permissions.requiresApproval && workflow.permissions.approvers.isEmpty()) {
            errors.add(
                ValidationError(
                    "APPROVAL_WITHOUT_APPROVERS",
                    "Workflow requires approval but has no approvers defined",
                    suggestedFix = "Add at least one user ID to the approvers list"
                )
            )
        }
    }
    
    /**
     * Validate workflow variables and references
     */
    private fun validateVariables(
        workflow: MultiUserWorkflow,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        val definedVariables = workflow.variables.keys.toMutableSet()
        val usedVariables = mutableSetOf<String>()
        
        // Extract variables used in actions
        workflow.actions.forEach { action ->
            when (action) {
                is MultiUserAction.SendToUserGmail -> {
                    extractVariables(action.subject, usedVariables)
                    extractVariables(action.body, usedVariables)
                }
                is MultiUserAction.SendToUserTelegram -> {
                    extractVariables(action.text, usedVariables)
                }
                is MultiUserAction.ForwardGmailToTelegram -> {
                    action.messageTemplate?.let { extractVariables(it, usedVariables) }
                }
                is MultiUserAction.AIAnalyzeText -> {
                    extractVariables(action.inputText, usedVariables)
                    extractVariables(action.analysisPrompt, usedVariables)
                    definedVariables.add(action.outputVariable)
                }
                is MultiUserAction.AIGenerateResponse -> {
                    extractVariables(action.context, usedVariables)
                    extractVariables(action.prompt, usedVariables)
                    definedVariables.add(action.outputVariable)
                }
                is MultiUserAction.LogAction -> {
                    extractVariables(action.message, usedVariables)
                }
                is MultiUserAction.AISummarizeContent -> {
                    extractVariables(action.content, usedVariables)
                    definedVariables.add(action.outputVariable)
                }
                is MultiUserAction.AITranslateText -> {
                    extractVariables(action.text, usedVariables)
                    definedVariables.add(action.outputVariable)
                }
                is MultiUserAction.AIExtractKeywords -> {
                    extractVariables(action.text, usedVariables)
                    definedVariables.add(action.outputVariable)
                }
                is MultiUserAction.AISentimentAnalysis -> {
                    extractVariables(action.text, usedVariables)
                    definedVariables.add(action.outputVariable)
                }
                is MultiUserAction.AISmartReply -> {
                    extractVariables(action.originalMessage, usedVariables)
                    action.context?.let { extractVariables(it, usedVariables) }
                    definedVariables.add(action.outputVariable)
                }
                is MultiUserAction.AISmartSummarizeAndForward -> {
                    extractVariables(action.triggerContent, usedVariables)
                    definedVariables.add(action.summaryOutputVariable)
                    definedVariables.add(action.keywordsOutputVariable)
                    definedVariables.add(action.forwardingDecisionVariable)
                }
                is MultiUserAction.AIAutoEmailSummarizer -> {
                    // Uses email data from trigger context, no variables to extract from action
                    definedVariables.add(action.summaryOutputVariable)
                }
                // Other action types that don't have text to extract
                is MultiUserAction.ReplyToUserGmail,
                is MultiUserAction.ForwardUserGmail,
                is MultiUserAction.ReplyToUserTelegram,
                is MultiUserAction.ForwardUserTelegram,
                is MultiUserAction.SendToMultipleUsers,
                is MultiUserAction.BroadcastMessage,
                is MultiUserAction.RequireApproval,
                is MultiUserAction.ConditionalAction,
                is MultiUserAction.DelayAction,
                is MultiUserAction.NotificationAction -> {
                    // These actions don't have extractable variables or are handled elsewhere
                }
            }
        }
        
        // Check for undefined variables
        usedVariables.forEach { variable ->
            if (!definedVariables.contains(variable)) {
                errors.add(
                    ValidationError(
                        "UNDEFINED_VARIABLE",
                        "Variable '$variable' is used but not defined",
                        suggestedFix = "Define the variable in workflow variables or ensure it's set by a previous action"
                    )
                )
            }
        }
        
        // Check for unused defined variables
        workflow.variables.keys.forEach { variable ->
            if (!usedVariables.contains(variable)) {
                warnings.add(
                    ValidationWarning(
                        "UNUSED_VARIABLE",
                        "Variable '$variable' is defined but never used",
                        "Remove unused variables to keep the workflow clean"
                    )
                )
            }
        }
    }
    
    /**
     * Extract variable references from text ({{variable_name}} format)
     */
    private fun extractVariables(text: String, variables: MutableSet<String>) {
        val regex = """\{\{([^}]+)\}\}""".toRegex()
        regex.findAll(text).forEach { match ->
            variables.add(match.groupValues[1].trim())
        }
    }
    
    /**
     * Validate cross-references and dependencies
     */
    private fun validateCrossReferences(
        workflow: MultiUserWorkflow,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>
    ) {
        val aiOutputVariables = mutableSetOf<String>()
        val conditionalActions = mutableListOf<MultiUserAction.ConditionalAction>()
        
        workflow.actions.forEach { action ->
            when (action) {
                is MultiUserAction.AIAnalyzeText -> aiOutputVariables.add(action.outputVariable)
                is MultiUserAction.AIGenerateResponse -> aiOutputVariables.add(action.outputVariable)
                is MultiUserAction.AISummarizeContent -> aiOutputVariables.add(action.outputVariable)
                is MultiUserAction.AITranslateText -> aiOutputVariables.add(action.outputVariable)
                is MultiUserAction.AIExtractKeywords -> aiOutputVariables.add(action.outputVariable)
                is MultiUserAction.AISentimentAnalysis -> aiOutputVariables.add(action.outputVariable)
                is MultiUserAction.AISmartReply -> aiOutputVariables.add(action.outputVariable)
                is MultiUserAction.AISmartSummarizeAndForward -> {
                    aiOutputVariables.add(action.summaryOutputVariable)
                    aiOutputVariables.add(action.keywordsOutputVariable)
                    aiOutputVariables.add(action.forwardingDecisionVariable)
                }
                is MultiUserAction.AIAutoEmailSummarizer -> {
                    aiOutputVariables.add(action.summaryOutputVariable)
                }
                is MultiUserAction.ConditionalAction -> conditionalActions.add(action)
                // Non-AI actions don't produce output variables
                is MultiUserAction.SendToUserGmail,
                is MultiUserAction.ReplyToUserGmail,
                is MultiUserAction.ForwardUserGmail,
                is MultiUserAction.SendToUserTelegram,
                is MultiUserAction.ForwardGmailToTelegram,
                is MultiUserAction.ReplyToUserTelegram,
                is MultiUserAction.ForwardUserTelegram,
                is MultiUserAction.SendToMultipleUsers,
                is MultiUserAction.BroadcastMessage,
                is MultiUserAction.RequireApproval,
                is MultiUserAction.DelayAction,
                is MultiUserAction.LogAction,
                is MultiUserAction.NotificationAction -> {
                    // These actions don't produce output variables
                }
            }
        }
        
        // Validate conditional action references
        conditionalActions.forEach { conditional ->
            val condition = conditional.condition
            // Simple check for AI output variable references
            aiOutputVariables.forEach { variable ->
                if (condition.contains(variable) && !condition.contains("$variable ==") && !condition.contains("$variable !=")) {
                    warnings.add(
                        ValidationWarning(
                            "UNCLEAR_CONDITION",
                            "Condition references AI variable '$variable' but comparison is unclear",
                            "Use explicit comparisons like '$variable == positive' or '$variable != empty'"
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Suggest fixes for common validation errors
     */
    fun suggestFixes(errors: List<ValidationError>): List<String> {
        return errors.mapNotNull { error ->
            error.suggestedFix?.let { "${error.code}: $it" }
        }
    }
    
    /**
     * Get validation summary
     */
    fun getValidationSummary(result: ValidationResult): String {
        val summary = buildString {
            if (result.isValid) {
                append("✅ Workflow is valid")
            } else {
                append("❌ Workflow has validation errors")
            }
            
            if (result.errors.isNotEmpty()) {
                append("\n\nErrors (${result.errors.size}):")
                result.errors.forEach { error ->
                    append("\n• ${error.message}")
                    error.suggestedFix?.let { append(" (Fix: $it)") }
                }
            }
            
            if (result.warnings.isNotEmpty()) {
                append("\n\nWarnings (${result.warnings.size}):")
                result.warnings.forEach { warning ->
                    append("\n• ${warning.message}")
                    warning.suggestion?.let { append(" (Suggestion: $it)") }
                }
            }
        }
        
        return summary
    }
}