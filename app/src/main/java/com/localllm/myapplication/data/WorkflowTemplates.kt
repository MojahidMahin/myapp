package com.localllm.myapplication.data

import com.localllm.myapplication.service.integration.GmailIntegrationService
import com.localllm.myapplication.service.integration.TelegramBotService
import java.util.UUID

/**
 * Pre-built workflow templates for common automation scenarios
 * Users can select these templates to quickly create workflows
 */
object WorkflowTemplates {
    
    /**
     * Template: Forward urgent emails to Telegram
     */
    fun createUrgentEmailToTelegramTemplate(creatorUserId: String, targetTelegramUserId: String): MultiUserWorkflow {
        return MultiUserWorkflow(
            id = UUID.randomUUID().toString(),
            name = "Urgent Email → Telegram Alert",
            description = "Automatically send Telegram notifications when urgent emails are received",
            createdBy = creatorUserId,
            workflowType = WorkflowType.PERSONAL,
            triggers = listOf(
                MultiUserTrigger.UserGmailNewEmail(
                    userId = creatorUserId,
                    condition = GmailIntegrationService.EmailCondition(
                        subjectFilter = "urgent",
                        isUnreadOnly = true
                    )
                )
            ),
            actions = listOf(
                MultiUserAction.AIAnalyzeText(
                    inputText = "{{email_subject}} - {{email_body}}",
                    analysisPrompt = "Summarize this urgent email in 100 words or less, focusing on what action is needed",
                    outputVariable = "email_summary"
                ),
                MultiUserAction.SendToUserTelegram(
                    targetUserId = targetTelegramUserId,
                    text = "🚨 URGENT EMAIL ALERT\n\nFrom: {{email_from}}\nSubject: {{email_subject}}\n\nAI Summary:\n{{email_summary}}\n\n#urgent #email"
                )
            )
        )
    }
    
    /**
     * Template: Telegram command to send emails
     */
    fun createTelegramToEmailTemplate(creatorUserId: String, targetEmailUserId: String): MultiUserWorkflow {
        return MultiUserWorkflow(
            id = UUID.randomUUID().toString(),
            name = "Telegram Command → Send Email",
            description = "Send emails via Telegram commands",
            createdBy = creatorUserId,
            workflowType = WorkflowType.CROSS_USER,
            sharedWith = listOf(targetEmailUserId),
            triggers = listOf(
                MultiUserTrigger.UserTelegramCommand(
                    userId = creatorUserId,
                    command = "/sendemail"
                )
            ),
            actions = listOf(
                MultiUserAction.AIGenerateResponse(
                    context = "{{telegram_message}}",
                    prompt = "Convert this Telegram message into a professional email format. Extract recipient, subject, and body from the message.",
                    outputVariable = "email_content"
                ),
                MultiUserAction.SendToUserGmail(
                    targetUserId = targetEmailUserId,
                    subject = "Message from Telegram",
                    body = "{{email_content}}"
                ),
                MultiUserAction.SendToUserTelegram(
                    targetUserId = creatorUserId,
                    text = "✅ Email sent successfully via workflow automation!"
                )
            )
        )
    }
    
    /**
     * Template: Auto-reply to emails using AI
     */
    fun createAIAutoReplyTemplate(creatorUserId: String): MultiUserWorkflow {
        return MultiUserWorkflow(
            id = UUID.randomUUID().toString(),
            name = "AI Email Auto-Reply",
            description = "Automatically generate and send AI-powered email replies",
            createdBy = creatorUserId,
            workflowType = WorkflowType.PERSONAL,
            triggers = listOf(
                MultiUserTrigger.UserGmailNewEmail(
                    userId = creatorUserId,
                    condition = GmailIntegrationService.EmailCondition(
                        subjectFilter = "support",
                        isUnreadOnly = true
                    )
                )
            ),
            actions = listOf(
                MultiUserAction.AISentimentAnalysis(
                    text = "{{email_body}}",
                    outputVariable = "email_sentiment"
                ),
                MultiUserAction.ConditionalAction(
                    condition = "email_sentiment == negative",
                    trueAction = MultiUserAction.AISmartReply(
                        originalMessage = "{{email_body}}",
                        context = "This is a customer support email. Be empathetic and helpful.",
                        tone = "empathetic",
                        outputVariable = "ai_reply"
                    ),
                    falseAction = MultiUserAction.AISmartReply(
                        originalMessage = "{{email_body}}",
                        context = "This is a customer support email. Be professional and helpful.",
                        tone = "professional",
                        outputVariable = "ai_reply"
                    )
                ),
                MultiUserAction.ReplyToUserGmail(
                    targetUserId = creatorUserId,
                    originalMessageId = "{{trigger_email_id}}",
                    replyBody = "{{ai_reply}}\n\n---\nThis is an automated AI-generated response. A human will follow up if needed."
                )
            )
        )
    }
    
    /**
     * Template: Team notification system
     */
    fun createTeamNotificationTemplate(creatorUserId: String, teamUserIds: List<String>): MultiUserWorkflow {
        return MultiUserWorkflow(
            id = UUID.randomUUID().toString(),
            name = "Team Notification System",
            description = "Broadcast important emails to team via multiple channels",
            createdBy = creatorUserId,
            workflowType = WorkflowType.TEAM,
            sharedWith = teamUserIds,
            triggers = listOf(
                MultiUserTrigger.UserGmailNewEmail(
                    userId = creatorUserId,
                    condition = GmailIntegrationService.EmailCondition(
                        fromFilter = "manager",
                        isUnreadOnly = true
                    )
                )
            ),
            actions = listOf(
                MultiUserAction.AIExtractKeywords(
                    text = "{{email_subject}} {{email_body}}",
                    count = 5,
                    outputVariable = "email_keywords"
                ),
                MultiUserAction.AISummarizeContent(
                    content = "{{email_body}}",
                    maxLength = 150,
                    outputVariable = "email_summary"
                ),
                MultiUserAction.BroadcastMessage(
                    targetUserIds = teamUserIds,
                    platforms = listOf(Platform.TELEGRAM, Platform.GMAIL),
                    content = "📢 TEAM NOTIFICATION\n\nFrom: {{email_from}}\nSubject: {{email_subject}}\n\nSummary: {{email_summary}}\n\nKeywords: {{email_keywords}}",
                    subject = "Team Alert: {{email_subject}}"
                )
            )
        )
    }
    
    /**
     * Template: Customer support escalation
     */
    fun createSupportEscalationTemplate(supportUserId: String, managerUserId: String): MultiUserWorkflow {
        return MultiUserWorkflow(
            id = UUID.randomUUID().toString(),
            name = "Support Escalation Workflow",
            description = "Escalate high-priority support emails to manager",
            createdBy = supportUserId,
            workflowType = WorkflowType.CROSS_USER,
            sharedWith = listOf(managerUserId),
            triggers = listOf(
                MultiUserTrigger.UserGmailNewEmail(
                    userId = supportUserId,
                    condition = GmailIntegrationService.EmailCondition(
                        subjectFilter = "priority|escalate|urgent",
                        isUnreadOnly = true
                    )
                )
            ),
            actions = listOf(
                MultiUserAction.AIAnalyzeText(
                    inputText = "{{email_body}}",
                    analysisPrompt = "Analyze this support email and determine the urgency level (low, medium, high, critical) and the main issue category",
                    outputVariable = "urgency_analysis"
                ),
                MultiUserAction.ConditionalAction(
                    condition = "urgency_analysis contains critical",
                    trueAction = MultiUserAction.SendToUserTelegram(
                        targetUserId = managerUserId,
                        text = "🚨 CRITICAL SUPPORT ISSUE\n\nFrom: {{email_from}}\nSubject: {{email_subject}}\n\nAnalysis: {{urgency_analysis}}\n\nImmediate attention required!"
                    ),
                    falseAction = MultiUserAction.SendToUserGmail(
                        targetUserId = managerUserId,
                        subject = "Support Escalation: {{email_subject}}",
                        body = "A support email has been escalated for your review.\n\nOriginal email details:\nFrom: {{email_from}}\nSubject: {{email_subject}}\n\nAI Analysis: {{urgency_analysis}}\n\nPlease review and provide guidance."
                    )
                ),
                MultiUserAction.DelayAction(delayMinutes = 30),
                MultiUserAction.RequireApproval(
                    approverUserId = managerUserId,
                    pendingAction = MultiUserAction.SendToUserGmail(
                        targetUserId = supportUserId,
                        subject = "Re: {{email_subject}} - Manager Response",
                        body = "This case has been reviewed by management. Please proceed with standard escalation procedures."
                    ),
                    timeoutMinutes = 120
                )
            )
        )
    }
    
    /**
     * Template: Language translation workflow
     */
    fun createTranslationWorkflowTemplate(creatorUserId: String, targetUserId: String): MultiUserWorkflow {
        return MultiUserWorkflow(
            id = UUID.randomUUID().toString(),
            name = "Email Translation Service",
            description = "Automatically translate emails and forward to team members",
            createdBy = creatorUserId,
            workflowType = WorkflowType.CROSS_USER,
            sharedWith = listOf(targetUserId),
            triggers = listOf(
                MultiUserTrigger.UserGmailNewEmail(
                    userId = creatorUserId,
                    condition = GmailIntegrationService.EmailCondition(
                        fromFilter = "@international",
                        isUnreadOnly = true
                    )
                )
            ),
            actions = listOf(
                MultiUserAction.AITranslateText(
                    text = "{{email_subject}}",
                    targetLanguage = "English",
                    outputVariable = "translated_subject"
                ),
                MultiUserAction.AITranslateText(
                    text = "{{email_body}}",
                    targetLanguage = "English",
                    outputVariable = "translated_body"
                ),
                MultiUserAction.SendToUserGmail(
                    targetUserId = targetUserId,
                    subject = "[TRANSLATED] {{translated_subject}}",
                    body = "📧 TRANSLATED EMAIL\n\nOriginal sender: {{email_from}}\nOriginal subject: {{email_subject}}\n\n--- TRANSLATION ---\nSubject: {{translated_subject}}\n\nBody:\n{{translated_body}}\n\n--- END TRANSLATION ---\n\nThis email was automatically translated using AI."
                ),
                MultiUserAction.SendToUserTelegram(
                    targetUserId = targetUserId,
                    text = "📧 New translated email from {{email_from}}\nSubject: {{translated_subject}}\n\nCheck your Gmail for the full translation."
                )
            )
        )
    }
    
    /**
     * Template: Meeting scheduler via Telegram
     */
    fun createMeetingSchedulerTemplate(creatorUserId: String, targetUserIds: List<String>): MultiUserWorkflow {
        return MultiUserWorkflow(
            id = UUID.randomUUID().toString(),
            name = "Telegram Meeting Scheduler",
            description = "Schedule meetings by sending Telegram commands",
            createdBy = creatorUserId,
            workflowType = WorkflowType.TEAM,
            sharedWith = targetUserIds,
            triggers = listOf(
                MultiUserTrigger.UserTelegramCommand(
                    userId = creatorUserId,
                    command = "/schedule"
                )
            ),
            actions = listOf(
                MultiUserAction.AIAnalyzeText(
                    inputText = "{{telegram_message}}",
                    analysisPrompt = "Extract meeting details from this message: date, time, attendees, subject, and agenda. Format as structured information.",
                    outputVariable = "meeting_details"
                ),
                MultiUserAction.SendToMultipleUsers(
                    targetUserIds = targetUserIds,
                    platform = Platform.GMAIL,
                    subject = "Meeting Invitation: {{telegram_message}}",
                    content = "You have been invited to a meeting.\n\nDetails:\n{{meeting_details}}\n\nPlease reply to confirm your attendance.\n\n---\nScheduled via Telegram automation"
                ),
                MultiUserAction.SendToUserTelegram(
                    targetUserId = creatorUserId,
                    text = "✅ Meeting invitations sent to ${targetUserIds.size} team members\n\nDetails:\n{{meeting_details}}"
                )
            )
        )
    }
    
    /**
     * Template: Telegram to Telegram message forwarding
     */
    fun createTelegramToTelegramTemplate(creatorUserId: String, targetUserId: String): MultiUserWorkflow {
        return MultiUserWorkflow(
            id = UUID.randomUUID().toString(),
            name = "Telegram → Telegram Forwarder",
            description = "Forward new Telegram messages to another user automatically",
            createdBy = creatorUserId,
            workflowType = WorkflowType.CROSS_USER,
            sharedWith = listOf(targetUserId),
            triggers = listOf(
                MultiUserTrigger.UserTelegramMessage(
                    userId = creatorUserId,
                    condition = TelegramBotService.TelegramCondition(
                        chatTypeFilter = "private"
                    )
                )
            ),
            actions = listOf(
                MultiUserAction.AIAnalyzeText(
                    inputText = "{{telegram_message}}",
                    analysisPrompt = "Analyze this message and extract key information, sentiment, and urgency level. Provide a brief summary.",
                    outputVariable = "message_analysis"
                ),
                MultiUserAction.SendToUserTelegram(
                    targetUserId = targetUserId,
                    text = "📨 New Message Forwarded\n\nFrom: {{telegram_sender_name}} (@{{telegram_username}})\nMessage: {{telegram_message}}\n\n🤖 AI Analysis: {{message_analysis}}\n\nTimestamp: {{telegram_timestamp}}"
                )
            )
        )
    }
    
    /**
     * Template: Email from specific sender trigger
     */
    fun createEmailFromSenderTemplate(creatorUserId: String, senderEmail: String, targetUserId: String): MultiUserWorkflow {
        return MultiUserWorkflow(
            id = UUID.randomUUID().toString(),
            name = "Email From: $senderEmail",
            description = "Trigger when receiving emails from $senderEmail",
            createdBy = creatorUserId,
            workflowType = WorkflowType.PERSONAL,
            triggers = listOf(
                MultiUserTrigger.UserGmailEmailReceived(
                    userId = creatorUserId,
                    fromFilter = senderEmail,
                    subjectFilter = null,
                    bodyFilter = null
                )
            ),
            actions = listOf(
                MultiUserAction.SendToUserTelegram(
                    targetUserId = targetUserId,
                    text = "📧 New email from $senderEmail\n\nSubject: {{email_subject}}\nFrom: {{email_from}}\n\nContent:\n{{email_body}}"
                )
            )
        )
    }
    
    /**
     * Template: Email with subject keyword trigger
     */
    fun createEmailSubjectKeywordTemplate(creatorUserId: String, keyword: String, targetUserId: String): MultiUserWorkflow {
        return MultiUserWorkflow(
            id = UUID.randomUUID().toString(),
            name = "Email Subject: $keyword",
            description = "Trigger when receiving emails with '$keyword' in subject",
            createdBy = creatorUserId,
            workflowType = WorkflowType.PERSONAL,
            triggers = listOf(
                MultiUserTrigger.UserGmailEmailReceived(
                    userId = creatorUserId,
                    fromFilter = null,
                    subjectFilter = keyword,
                    bodyFilter = null
                )
            ),
            actions = listOf(
                MultiUserAction.SendToUserTelegram(
                    targetUserId = targetUserId,
                    text = "🔔 Email Alert: '$keyword'\n\nFrom: {{email_from}}\nSubject: {{email_subject}}\n\nContent:\n{{email_body}}"
                )
            )
        )
    }
    
    /**
     * Template: Combined email filter (sender + subject)
     */
    fun createAdvancedEmailFilterTemplate(creatorUserId: String, senderFilter: String?, subjectFilter: String?, targetUserId: String): MultiUserWorkflow {
        val name = buildString {
            append("Email Filter:")
            if (!senderFilter.isNullOrBlank()) append(" From($senderFilter)")
            if (!subjectFilter.isNullOrBlank()) append(" Subject($subjectFilter)")
        }
        
        return MultiUserWorkflow(
            id = UUID.randomUUID().toString(),
            name = name,
            description = "Advanced email filter with multiple conditions",
            createdBy = creatorUserId,
            workflowType = WorkflowType.PERSONAL,
            triggers = listOf(
                MultiUserTrigger.UserGmailEmailReceived(
                    userId = creatorUserId,
                    fromFilter = senderFilter?.takeIf { it.isNotBlank() },
                    subjectFilter = subjectFilter?.takeIf { it.isNotBlank() },
                    bodyFilter = null
                )
            ),
            actions = listOf(
                MultiUserAction.SendToUserTelegram(
                    targetUserId = targetUserId,
                    text = "📬 Filtered Email Alert\n\nFrom: {{email_from}}\nSubject: {{email_subject}}\nReceived: {{trigger_timestamp}}\n\n📝 Content:\n{{email_body}}"
                )
            )
        )
    }
    
    /**
     * Template: Smart Telegram message relay with keywords
     */
    fun createSmartTelegramRelayTemplate(creatorUserId: String, targetUserId: String, keywords: List<String> = listOf("urgent", "important", "help")): MultiUserWorkflow {
        return MultiUserWorkflow(
            id = UUID.randomUUID().toString(),
            name = "Smart Telegram Relay",
            description = "Forward Telegram messages containing specific keywords to another user",
            createdBy = creatorUserId,
            workflowType = WorkflowType.CROSS_USER,
            sharedWith = listOf(targetUserId),
            triggers = listOf(
                MultiUserTrigger.UserTelegramMessage(
                    userId = creatorUserId,
                    condition = TelegramBotService.TelegramCondition(
                        textContains = keywords.joinToString("|")
                    )
                )
            ),
            actions = listOf(
                MultiUserAction.AIExtractKeywords(
                    text = "{{telegram_message}}",
                    count = 3,
                    outputVariable = "message_keywords"
                ),
                MultiUserAction.AISentimentAnalysis(
                    text = "{{telegram_message}}",
                    outputVariable = "message_sentiment"
                ),
                MultiUserAction.ConditionalAction(
                    condition = "message_sentiment == urgent",
                    trueAction = MultiUserAction.SendToUserTelegram(
                        targetUserId = targetUserId,
                        text = "🚨 URGENT MESSAGE RELAY\n\nFrom: {{telegram_sender_name}}\nMessage: {{telegram_message}}\n\nKeywords: {{message_keywords}}\nSentiment: {{message_sentiment}}\n\n⚡ This message was flagged as urgent!"
                    ),
                    falseAction = MultiUserAction.SendToUserTelegram(
                        targetUserId = targetUserId,
                        text = "📋 Message Relay\n\nFrom: {{telegram_sender_name}}\nMessage: {{telegram_message}}\n\nKeywords: {{message_keywords}}\nSentiment: {{message_sentiment}}"
                    )
                )
            )
        )
    }
    
    /**
     * Get all available templates
     */
    fun getAllTemplates(): List<WorkflowTemplate> {
        return listOf(
            WorkflowTemplate(
                id = "urgent-email-telegram",
                name = "Urgent Email → Telegram Alert",
                description = "Get instant Telegram notifications for urgent emails",
                category = "Email Notifications",
                platforms = listOf(Platform.GMAIL, Platform.TELEGRAM),
                requiredUsers = 1,
                optionalUsers = 1,
                estimatedSetupTime = "2 minutes",
                tags = listOf("urgent", "notifications", "email", "telegram")
            ),
            WorkflowTemplate(
                id = "telegram-email-sender",
                name = "Telegram → Send Email",
                description = "Send emails quickly via Telegram commands",
                category = "Email Management",
                platforms = listOf(Platform.TELEGRAM, Platform.GMAIL),
                requiredUsers = 1,
                optionalUsers = 1,
                estimatedSetupTime = "3 minutes",
                tags = listOf("email", "telegram", "commands", "productivity")
            ),
            WorkflowTemplate(
                id = "ai-auto-reply",
                name = "AI Email Auto-Reply",
                description = "Automatically respond to emails using AI",
                category = "AI Automation",
                platforms = listOf(Platform.GMAIL),
                requiredUsers = 1,
                optionalUsers = 0,
                estimatedSetupTime = "5 minutes",
                tags = listOf("ai", "auto-reply", "customer-service", "automation")
            ),
            WorkflowTemplate(
                id = "team-notifications",
                name = "Team Notification System",
                description = "Broadcast important emails to your team",
                category = "Team Collaboration",
                platforms = listOf(Platform.GMAIL, Platform.TELEGRAM),
                requiredUsers = 1,
                optionalUsers = 10,
                estimatedSetupTime = "4 minutes",
                tags = listOf("team", "broadcast", "notifications", "collaboration")
            ),
            WorkflowTemplate(
                id = "support-escalation",
                name = "Support Escalation",
                description = "Automatically escalate high-priority support emails",
                category = "Customer Support",
                platforms = listOf(Platform.GMAIL, Platform.TELEGRAM),
                requiredUsers = 2,
                optionalUsers = 0,
                estimatedSetupTime = "6 minutes",
                tags = listOf("support", "escalation", "priority", "management")
            ),
            WorkflowTemplate(
                id = "email-translation",
                name = "Email Translation Service",
                description = "Translate international emails automatically",
                category = "Communication",
                platforms = listOf(Platform.GMAIL, Platform.TELEGRAM),
                requiredUsers = 1,
                optionalUsers = 1,
                estimatedSetupTime = "3 minutes",
                tags = listOf("translation", "international", "communication", "ai")
            ),
            WorkflowTemplate(
                id = "meeting-scheduler",
                name = "Telegram Meeting Scheduler",
                description = "Schedule meetings using Telegram commands",
                category = "Meeting Management",
                platforms = listOf(Platform.TELEGRAM, Platform.GMAIL),
                requiredUsers = 1,
                optionalUsers = 10,
                estimatedSetupTime = "4 minutes",
                tags = listOf("meetings", "scheduling", "telegram", "team")
            ),
            WorkflowTemplate(
                id = "telegram-to-telegram",
                name = "Telegram → Telegram Forwarder",
                description = "Forward new Telegram messages to another user automatically",
                category = "Message Forwarding",
                platforms = listOf(Platform.TELEGRAM),
                requiredUsers = 2,
                optionalUsers = 0,
                estimatedSetupTime = "2 minutes",
                tags = listOf("telegram", "forwarding", "automation", "relay")
            ),
            WorkflowTemplate(
                id = "smart-telegram-relay",
                name = "Smart Telegram Relay",
                description = "Forward Telegram messages with specific keywords to another user",
                category = "Smart Filtering",
                platforms = listOf(Platform.TELEGRAM),
                requiredUsers = 2,
                optionalUsers = 0,
                estimatedSetupTime = "3 minutes",
                tags = listOf("telegram", "keywords", "ai", "smart-filtering", "relay")
            ),
            WorkflowTemplate(
                id = "email-from-sender",
                name = "Email From Specific Sender",
                description = "Trigger workflow when receiving emails from a specific sender",
                category = "Email Filtering",
                platforms = listOf(Platform.GMAIL, Platform.TELEGRAM),
                requiredUsers = 1,
                optionalUsers = 1,
                estimatedSetupTime = "2 minutes",
                tags = listOf("email", "gmail", "sender", "filtering", "notifications")
            ),
            WorkflowTemplate(
                id = "email-subject-keyword",
                name = "Email Subject Keyword",
                description = "Trigger workflow when emails contain specific keywords in subject",
                category = "Email Filtering",
                platforms = listOf(Platform.GMAIL, Platform.TELEGRAM),
                requiredUsers = 1,
                optionalUsers = 1,
                estimatedSetupTime = "2 minutes",
                tags = listOf("email", "gmail", "subject", "keywords", "filtering")
            ),
            WorkflowTemplate(
                id = "advanced-email-filter",
                name = "Advanced Email Filter",
                description = "Combine sender and subject filters for precise email matching",
                category = "Email Filtering",
                platforms = listOf(Platform.GMAIL, Platform.TELEGRAM),
                requiredUsers = 1,
                optionalUsers = 1,
                estimatedSetupTime = "3 minutes",
                tags = listOf("email", "gmail", "advanced", "filtering", "combined")
            )
        )
    }
    
    /**
     * Create workflow from template
     */
    fun createFromTemplate(
        templateId: String,
        creatorUserId: String,
        targetUserIds: List<String> = emptyList()
    ): MultiUserWorkflow? {
        return when (templateId) {
            "urgent-email-telegram" -> {
                val targetUserId = targetUserIds.firstOrNull() ?: creatorUserId
                createUrgentEmailToTelegramTemplate(creatorUserId, targetUserId)
            }
            "telegram-email-sender" -> {
                val targetUserId = targetUserIds.firstOrNull() ?: creatorUserId
                createTelegramToEmailTemplate(creatorUserId, targetUserId)
            }
            "ai-auto-reply" -> createAIAutoReplyTemplate(creatorUserId)
            "team-notifications" -> createTeamNotificationTemplate(creatorUserId, targetUserIds)
            "support-escalation" -> {
                val managerUserId = targetUserIds.firstOrNull()
                    ?: return null
                createSupportEscalationTemplate(creatorUserId, managerUserId)
            }
            "email-translation" -> {
                val targetUserId = targetUserIds.firstOrNull() ?: creatorUserId
                createTranslationWorkflowTemplate(creatorUserId, targetUserId)
            }
            "meeting-scheduler" -> createMeetingSchedulerTemplate(creatorUserId, targetUserIds)
            "telegram-to-telegram" -> {
                val targetUserId = targetUserIds.firstOrNull()
                    ?: return null
                createTelegramToTelegramTemplate(creatorUserId, targetUserId)
            }
            "smart-telegram-relay" -> {
                val targetUserId = targetUserIds.firstOrNull()
                    ?: return null
                createSmartTelegramRelayTemplate(creatorUserId, targetUserId)
            }
            else -> null
        }
    }
}

/**
 * Template metadata for UI display
 */
data class WorkflowTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val platforms: List<Platform>,
    val requiredUsers: Int,
    val optionalUsers: Int,
    val estimatedSetupTime: String,
    val tags: List<String>
)