package com.localllm.myapplication.service

import android.content.Context
import android.util.Log
import com.localllm.myapplication.data.*
import com.localllm.myapplication.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Service to automatically save contacts from Gmail and Telegram interactions
 */
class ContactAutoSaveService(private val context: Context) {
    
    private val contactRepository = AppContainer.provideContactRepository(context)
    private val telegramPrefs = TelegramPreferences(context)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Auto-save a contact from email interaction
     */
    fun autoSaveEmailContact(emailAddress: String, name: String? = null) {
        serviceScope.launch {
            try {
                val result = contactRepository.findOrCreateContactByEmail(emailAddress, name)
                result.fold(
                    onSuccess = { contact ->
                        Log.d("ContactAutoSave", "Auto-saved email contact: ${contact.name} ($emailAddress)")
                    },
                    onFailure = { error ->
                        Log.e("ContactAutoSave", "Failed to auto-save email contact: $emailAddress", error)
                    }
                )
            } catch (e: Exception) {
                Log.e("ContactAutoSave", "Exception auto-saving email contact: $emailAddress", e)
            }
        }
    }
    
    /**
     * Auto-save a contact from Telegram interaction
     */
    fun autoSaveTelegramContact(telegramUser: TelegramUser) {
        serviceScope.launch {
            try {
                val result = contactRepository.findOrCreateContactByTelegram(
                    telegramId = telegramUser.id.toString(),
                    telegramUsername = telegramUser.username,
                    name = telegramUser.displayName
                )
                result.fold(
                    onSuccess = { contact ->
                        Log.d("ContactAutoSave", "Auto-saved Telegram contact: ${contact.name} (ID: ${telegramUser.id})")
                    },
                    onFailure = { error ->
                        Log.e("ContactAutoSave", "Failed to auto-save Telegram contact: ${telegramUser.displayName}", error)
                    }
                )
            } catch (e: Exception) {
                Log.e("ContactAutoSave", "Exception auto-saving Telegram contact: ${telegramUser.displayName}", e)
            }
        }
    }
    
    /**
     * Sync all existing Telegram users from preferences to contacts
     */
    fun syncTelegramUsers() {
        serviceScope.launch {
            try {
                val savedUsers = telegramPrefs.getSavedUsers()
                Log.d("ContactAutoSave", "Syncing ${savedUsers.size} Telegram users to contacts")
                
                savedUsers.values.forEach { telegramUser ->
                    autoSaveTelegramContact(telegramUser)
                }
                
                Log.d("ContactAutoSave", "Completed Telegram user sync")
            } catch (e: Exception) {
                Log.e("ContactAutoSave", "Exception syncing Telegram users", e)
            }
        }
    }
    
    /**
     * Auto-save contact from Gmail email data
     */
    fun autoSaveFromGmailEmail(fromAddress: String, fromName: String? = null) {
        serviceScope.launch {
            try {
                // Extract name from email header if provided
                val contactName = fromName?.let { name ->
                    // Clean up the name (remove quotes, email addresses in brackets, etc.)
                    name.replace("\"", "")
                        .replace(Regex("<.*>"), "")
                        .trim()
                        .takeIf { it.isNotBlank() }
                }
                
                autoSaveEmailContact(fromAddress, contactName)
            } catch (e: Exception) {
                Log.e("ContactAutoSave", "Exception auto-saving Gmail contact: $fromAddress", e)
            }
        }
    }
    
    /**
     * Auto-save contact from workflow trigger data
     */
    fun autoSaveFromWorkflowData(triggerData: Map<String, Any>) {
        serviceScope.launch {
            try {
                // Check for email data
                val emailFrom = triggerData["email_from"] as? String
                val emailFromName = triggerData["email_from_name"] as? String
                
                if (!emailFrom.isNullOrBlank()) {
                    autoSaveFromGmailEmail(emailFrom, emailFromName)
                }
                
                // Check for Telegram data
                val telegramUserId = (triggerData["telegram_user_id"] as? Number)?.toLong()
                val telegramUsername = triggerData["telegram_username"] as? String
                val telegramFirstName = triggerData["telegram_first_name"] as? String
                val telegramLastName = triggerData["telegram_last_name"] as? String
                
                if (telegramUserId != null) {
                    val telegramUser = TelegramUser.fromChatInfo(
                        id = telegramUserId,
                        firstName = telegramFirstName,
                        lastName = telegramLastName,
                        username = telegramUsername
                    )
                    autoSaveTelegramContact(telegramUser)
                }
                
            } catch (e: Exception) {
                Log.e("ContactAutoSave", "Exception auto-saving from workflow data", e)
            }
        }
    }
    
    /**
     * Get contact statistics
     */
    suspend fun getContactStats(): ContactStats = withContext(Dispatchers.IO) {
        try {
            val allContactsResult = contactRepository.getAllContacts()
            val autoSavedResult = contactRepository.getAutoSavedContacts()
            
            val totalContacts = allContactsResult.getOrNull()?.size ?: 0
            val autoSavedContacts = autoSavedResult.getOrNull()?.size ?: 0
            val manualContacts = totalContacts - autoSavedContacts
            
            ContactStats(
                totalContacts = totalContacts,
                autoSavedContacts = autoSavedContacts,
                manualContacts = manualContacts
            )
        } catch (e: Exception) {
            Log.e("ContactAutoSave", "Exception getting contact stats", e)
            ContactStats(0, 0, 0)
        }
    }
}

data class ContactStats(
    val totalContacts: Int,
    val autoSavedContacts: Int,
    val manualContacts: Int
)