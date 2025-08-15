package com.localllm.myapplication.service.integration

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.ListMessagesResponse
import com.google.api.services.gmail.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * Custom exception for auth consent requirements
 */
class AuthConsentRequiredException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Gmail integration service for workflow automation
 * Handles authentication, message monitoring, and email sending
 */
class GmailIntegrationService(private val context: Context) {
    
    companion object {
        private const val TAG = "GmailIntegrationService"
        private const val APPLICATION_NAME = "LocalLLM Workflow Automation"
    }
    
    private var googleSignInClient: GoogleSignInClient? = null
    private var gmailService: Gmail? = null
    private var currentAccount: GoogleSignInAccount? = null
    
    data class EmailMessage(
        val id: String,
        val threadId: String,
        val from: String,
        val to: String,
        val subject: String,
        val body: String,
        val timestamp: Long,
        val isRead: Boolean,
        val labels: List<String>
    )
    
    data class EmailCondition(
        val fromFilter: String? = null,
        val subjectFilter: String? = null,
        val bodyFilter: String? = null,
        val labelFilter: String? = null,
        val isUnreadOnly: Boolean = true,
        val newerThan: Long? = null, // Only get emails newer than this timestamp
        val maxAgeHours: Int = 24 // Only process emails from last 24 hours by default
    )
    
    /**
     * Initialize Gmail integration with proper scopes
     */
    fun initialize(): Result<Unit> {
        return try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(
                    Scope(GmailScopes.GMAIL_READONLY),
                    Scope(GmailScopes.GMAIL_SEND),
                    Scope(GmailScopes.GMAIL_MODIFY)
                )
                .build()
                
            googleSignInClient = GoogleSignIn.getClient(context, gso)
            
            // Check if already signed in
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                currentAccount = account
                setupGmailService(account)
            }
            
            Log.d(TAG, "Gmail integration initialized")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Gmail integration", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get Google Sign-In client for authentication
     */
    fun getSignInClient(): GoogleSignInClient? = googleSignInClient
    
    /**
     * Handle successful sign-in
     */
    fun handleSignInResult(account: GoogleSignInAccount): Result<Unit> {
        return try {
            currentAccount = account
            setupGmailService(account)
            Log.d(TAG, "Gmail sign-in successful for: ${account.email}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle Gmail sign-in", e)
            Result.failure(e)
        }
    }
    
    /**
     * Setup Gmail service with authenticated account
     */
    private fun setupGmailService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(
                GmailScopes.GMAIL_READONLY,
                GmailScopes.GMAIL_SEND,
                GmailScopes.GMAIL_MODIFY
            )
        )
        credential.selectedAccount = account.account
        
        gmailService = Gmail.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName(APPLICATION_NAME)
            .build()
    }
    
    /**
     * Get the most recent email (last email)
     */
    suspend fun getLastEmail(): Result<EmailMessage?> {
        return checkForNewEmails(EmailCondition(isUnreadOnly = false), limit = 1)
            .map { emails -> emails.firstOrNull() }
    }

    /**
     * Get recent emails with a custom count
     */
    suspend fun getRecentEmails(count: Int = 5): Result<List<EmailMessage>> {
        return checkForNewEmails(EmailCondition(isUnreadOnly = false), limit = count)
    }

    /**
     * Check for new emails matching conditions
     */
    suspend fun checkForNewEmails(condition: EmailCondition, limit: Int = 10): Result<List<EmailMessage>> {
        return withContext(Dispatchers.IO) {
            try {
                val service = gmailService ?: return@withContext Result.failure(
                    Exception("Gmail service not initialized. Please sign in first.")
                )
                
                // Build query string
                val queryBuilder = mutableListOf<String>()
                if (condition.isUnreadOnly) queryBuilder.add("is:unread")
                condition.fromFilter?.let { queryBuilder.add("from:$it") }
                condition.subjectFilter?.let { queryBuilder.add("subject:$it") }
                condition.bodyFilter?.let { queryBuilder.add("$it") }
                condition.labelFilter?.let { queryBuilder.add("label:$it") }
                
                // Add time-based filtering
                val cutoffTime = condition.newerThan ?: (System.currentTimeMillis() - (condition.maxAgeHours * 60 * 60 * 1000))
                val cutoffSeconds = cutoffTime / 1000
                queryBuilder.add("after:$cutoffSeconds")
                
                val query = queryBuilder.joinToString(" ")
                Log.d(TAG, "=== GMAIL API CALL ===")
                Log.d(TAG, "Query: '$query'")
                Log.d(TAG, "Limit: $limit")
                Log.d(TAG, "Condition: isUnreadOnly=${condition.isUnreadOnly}, fromFilter=${condition.fromFilter}, maxAgeHours=${condition.maxAgeHours}")
                Log.d(TAG, "Cutoff time: $cutoffTime (${java.util.Date(cutoffTime)})")
                
                // Get message list
                val request = service.users().messages().list("me")
                if (query.isNotEmpty()) {
                    request.q = query
                }
                request.maxResults = limit.toLong()
                
                val startTime = System.currentTimeMillis()
                val response: ListMessagesResponse = request.execute()
                val apiTime = System.currentTimeMillis() - startTime
                
                val messages = response.messages ?: emptyList()
                
                Log.d(TAG, "Gmail API response time: ${apiTime}ms")
                Log.d(TAG, "Found ${messages.size} matching message references")
                
                // Get full message details
                val emailMessages = messages.mapIndexed { index, messageRef ->
                    Log.d(TAG, "Fetching email ${index + 1}/${messages.size}: ${messageRef.id}")
                    val fullMessage = service.users().messages().get("me", messageRef.id).execute()
                    val parsed = parseEmailMessage(fullMessage)
                    Log.d(TAG, "Email ${messageRef.id}: from=${parsed.from}, subject='${parsed.subject}', timestamp=${parsed.timestamp}, isRead=${parsed.isRead}")
                    parsed
                }
                
                Log.d(TAG, "=== GMAIL API RESULT ===")
                Log.d(TAG, "Successfully fetched ${emailMessages.size} emails")
                emailMessages.forEach { email ->
                    Log.d(TAG, "  ${email.id}: '${email.subject}' from ${email.from} (read: ${email.isRead})")
                }
                
                Result.success(emailMessages)
                
            } catch (e: UserRecoverableAuthIOException) {
                Log.e(TAG, "User consent required for Gmail access", e)
                Result.failure(AuthConsentRequiredException("User needs to grant additional Gmail permissions", e))
            } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
                Log.e(TAG, "Gmail API error: ${e.details?.code} - ${e.details?.message}", e)
                when (e.details?.code) {
                    403 -> {
                        val isServiceDisabled = e.details.errors?.any { 
                            it.reason == "accessNotConfigured" || it.reason == "SERVICE_DISABLED" 
                        } == true
                        if (isServiceDisabled) {
                            Result.failure(Exception("Gmail API is not enabled. Please enable it in Google Cloud Console and wait a few minutes before trying again."))
                        } else {
                            Result.failure(Exception("Access denied. Please check your Gmail permissions."))
                        }
                    }
                    401 -> Result.failure(AuthConsentRequiredException("Authentication expired. Please sign in again.", e))
                    else -> Result.failure(Exception("Gmail API error: ${e.details?.message ?: e.message}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for new emails", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Send an email
     */
    suspend fun sendEmail(
        to: String,
        subject: String,
        body: String,
        isHtml: Boolean = false
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val service = gmailService ?: return@withContext Result.failure(
                    Exception("Gmail service not initialized. Please sign in first.")
                )
                
                val contentType = if (isHtml) "text/html" else "text/plain"
                val rawMessage = createRawMessage(to, subject, body, contentType)
                
                val message = Message()
                message.raw = rawMessage
                
                val sent = service.users().messages().send("me", message).execute()
                
                Log.d(TAG, "Email sent successfully. Message ID: ${sent.id}")
                Result.success(sent.id)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send email", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Reply to an email
     */
    suspend fun replyToEmail(
        originalMessageId: String,
        replyBody: String,
        isHtml: Boolean = false
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val service = gmailService ?: return@withContext Result.failure(
                    Exception("Gmail service not initialized. Please sign in first.")
                )
                
                // Get original message to extract reply information
                val originalMessage = service.users().messages().get("me", originalMessageId).execute()
                val originalEmail = parseEmailMessage(originalMessage)
                
                val replySubject = if (originalEmail.subject.startsWith("Re:")) {
                    originalEmail.subject
                } else {
                    "Re: ${originalEmail.subject}"
                }
                
                val contentType = if (isHtml) "text/html" else "text/plain"
                val rawMessage = createRawMessage(originalEmail.from, replySubject, replyBody, contentType)
                
                val message = Message()
                message.raw = rawMessage
                message.threadId = originalEmail.threadId
                
                val sent = service.users().messages().send("me", message).execute()
                
                Log.d(TAG, "Reply sent successfully. Message ID: ${sent.id}")
                Result.success(sent.id)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send reply", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Mark email as read
     */
    suspend fun markAsRead(messageId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val service = gmailService ?: return@withContext Result.failure(
                    Exception("Gmail service not initialized. Please sign in first.")
                )
                
                val modifyRequest = com.google.api.services.gmail.model.ModifyMessageRequest()
                modifyRequest.removeLabelIds = listOf("UNREAD")
                
                service.users().messages().modify("me", messageId, modifyRequest).execute()
                
                Log.d(TAG, "Message marked as read: $messageId")
                Result.success(Unit)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark message as read", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Parse Gmail message to our EmailMessage format
     */
    private fun parseEmailMessage(message: Message): EmailMessage {
        val headers = message.payload?.headers ?: emptyList()
        
        val from = headers.find { it.name.equals("From", true) }?.value ?: "Unknown"
        val to = headers.find { it.name.equals("To", true) }?.value ?: "Unknown"
        val subject = headers.find { it.name.equals("Subject", true) }?.value ?: "No Subject"
        
        // Extract body text
        var body = ""
        message.payload?.let { payload ->
            body = extractTextFromPayload(payload)
        }
        
        val labels = message.labelIds ?: emptyList()
        val isRead = !labels.contains("UNREAD")
        
        return EmailMessage(
            id = message.id,
            threadId = message.threadId,
            from = from,
            to = to,
            subject = subject,
            body = body,
            timestamp = message.internalDate ?: System.currentTimeMillis(),
            isRead = isRead,
            labels = labels
        )
    }
    
    /**
     * Extract text content from message payload
     */
    private fun extractTextFromPayload(payload: com.google.api.services.gmail.model.MessagePart): String {
        if (payload.body?.data != null) {
            return String(android.util.Base64.decode(payload.body.data, android.util.Base64.URL_SAFE))
        }
        
        payload.parts?.let { parts ->
            for (part in parts) {
                if (part.mimeType == "text/plain" || part.mimeType == "text/html") {
                    part.body?.data?.let { data ->
                        return String(android.util.Base64.decode(data, android.util.Base64.URL_SAFE))
                    }
                }
                // Recursively check nested parts
                val nestedText = extractTextFromPayload(part)
                if (nestedText.isNotEmpty()) {
                    return nestedText
                }
            }
        }
        
        return ""
    }
    
    /**
     * Create raw email message for sending
     */
    private fun createRawMessage(to: String, subject: String, body: String, contentType: String): String {
        val account = currentAccount ?: throw Exception("No authenticated account")
        
        val message = """
            From: ${account.email}
            To: $to
            Subject: $subject
            Content-Type: $contentType; charset=UTF-8
            
            $body
        """.trimIndent()
        
        return android.util.Base64.encodeToString(
            message.toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
    }
    
    /**
     * Check if user is signed in
     */
    fun isSignedIn(): Boolean = currentAccount != null && gmailService != null
    
    /**
     * Get current signed-in account email
     */
    fun getCurrentUserEmail(): String? = currentAccount?.email
    
    /**
     * Sign out
     */
    suspend fun signOut(): Result<Unit> {
        return try {
            googleSignInClient?.signOut()
            currentAccount = null
            gmailService = null
            Log.d(TAG, "Gmail sign-out successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign out from Gmail", e)
            Result.failure(e)
        }
    }
}