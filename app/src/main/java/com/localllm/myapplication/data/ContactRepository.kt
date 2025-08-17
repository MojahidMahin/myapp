package com.localllm.myapplication.data

import android.content.Context
import android.util.Log
import com.localllm.myapplication.data.database.ContactDao
import com.localllm.myapplication.data.database.WorkflowDatabase
import com.localllm.myapplication.data.database.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ContactRepository {
    suspend fun getAllContacts(): Result<List<Contact>>
    suspend fun getContactById(id: String): Result<Contact?>
    suspend fun searchContacts(query: String): Result<List<Contact>>
    suspend fun getAutoSavedContacts(): Result<List<Contact>>
    suspend fun insertContact(contact: Contact): Result<Unit>
    suspend fun updateContact(contact: Contact): Result<Unit>
    suspend fun deleteContact(contact: Contact): Result<Unit>
    suspend fun deleteContactById(id: String): Result<Unit>
    suspend fun getContactCount(): Result<Int>
    suspend fun findOrCreateContactByEmail(email: String, name: String? = null): Result<Contact>
    suspend fun findOrCreateContactByTelegram(telegramId: String, telegramUsername: String? = null, name: String? = null): Result<Contact>
}

class ContactRepositoryImpl(private val context: Context) : ContactRepository {
    
    private val database by lazy { WorkflowDatabase.getDatabase(context) }
    private val contactDao: ContactDao by lazy { database.contactDao() }
    
    override suspend fun getAllContacts(): Result<List<Contact>> = withContext(Dispatchers.IO) {
        try {
            val entities = contactDao.getAllContacts()
            val contacts = entities.map { it.toContact() }
            Log.d("ContactRepository", "Retrieved ${contacts.size} contacts")
            Result.success(contacts)
        } catch (e: Exception) {
            Log.e("ContactRepository", "Failed to get all contacts", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getContactById(id: String): Result<Contact?> = withContext(Dispatchers.IO) {
        try {
            val entity = contactDao.getContactById(id)
            val contact = entity?.toContact()
            Log.d("ContactRepository", "Retrieved contact: ${contact?.name}")
            Result.success(contact)
        } catch (e: Exception) {
            Log.e("ContactRepository", "Failed to get contact by id: $id", e)
            Result.failure(e)
        }
    }
    
    override suspend fun searchContacts(query: String): Result<List<Contact>> = withContext(Dispatchers.IO) {
        try {
            val entities = contactDao.searchContacts(query)
            val contacts = entities.map { it.toContact() }
            Log.d("ContactRepository", "Found ${contacts.size} contacts for query: $query")
            Result.success(contacts)
        } catch (e: Exception) {
            Log.e("ContactRepository", "Failed to search contacts with query: $query", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getAutoSavedContacts(): Result<List<Contact>> = withContext(Dispatchers.IO) {
        try {
            val entities = contactDao.getAutoSavedContacts()
            val contacts = entities.map { it.toContact() }
            Log.d("ContactRepository", "Retrieved ${contacts.size} auto-saved contacts")
            Result.success(contacts)
        } catch (e: Exception) {
            Log.e("ContactRepository", "Failed to get auto-saved contacts", e)
            Result.failure(e)
        }
    }
    
    override suspend fun insertContact(contact: Contact): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!contact.isValid()) {
                return@withContext Result.failure(IllegalArgumentException("Contact is not valid. Name and at least one contact method (email or telegram) are required."))
            }
            
            val entity = contact.toEntity()
            contactDao.insertContact(entity)
            Log.d("ContactRepository", "Inserted contact: ${contact.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ContactRepository", "Failed to insert contact: ${contact.name}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun updateContact(contact: Contact): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!contact.isValid()) {
                return@withContext Result.failure(IllegalArgumentException("Contact is not valid. Name and at least one contact method (email or telegram) are required."))
            }
            
            val updatedContact = contact.copy(updatedAt = System.currentTimeMillis())
            val entity = updatedContact.toEntity()
            contactDao.updateContact(entity)
            Log.d("ContactRepository", "Updated contact: ${contact.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ContactRepository", "Failed to update contact: ${contact.name}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun deleteContact(contact: Contact): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = contact.toEntity()
            contactDao.deleteContact(entity)
            Log.d("ContactRepository", "Deleted contact: ${contact.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ContactRepository", "Failed to delete contact: ${contact.name}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun deleteContactById(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            contactDao.deleteContactById(id)
            Log.d("ContactRepository", "Deleted contact by id: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ContactRepository", "Failed to delete contact by id: $id", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getContactCount(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val count = contactDao.getContactCount()
            Log.d("ContactRepository", "Contact count: $count")
            Result.success(count)
        } catch (e: Exception) {
            Log.e("ContactRepository", "Failed to get contact count", e)
            Result.failure(e)
        }
    }
    
    override suspend fun findOrCreateContactByEmail(email: String, name: String?): Result<Contact> = withContext(Dispatchers.IO) {
        try {
            val existingContact = contactDao.getContactByGmail(email)
            if (existingContact != null) {
                Log.d("ContactRepository", "Found existing contact for email: $email")
                return@withContext Result.success(existingContact.toContact())
            }
            
            val contactName = name ?: extractNameFromEmail(email)
            val newContact = Contact(
                name = contactName,
                gmail = email,
                isAutoSaved = true
            )
            
            contactDao.insertContact(newContact.toEntity())
            Log.d("ContactRepository", "Created new auto-saved contact for email: $email")
            Result.success(newContact)
        } catch (e: Exception) {
            Log.e("ContactRepository", "Failed to find or create contact for email: $email", e)
            Result.failure(e)
        }
    }
    
    override suspend fun findOrCreateContactByTelegram(telegramId: String, telegramUsername: String?, name: String?): Result<Contact> = withContext(Dispatchers.IO) {
        try {
            val existingContact = contactDao.getContactByTelegramId(telegramId) 
                ?: telegramUsername?.let { contactDao.getContactByTelegramUsername(it) }
            
            if (existingContact != null) {
                Log.d("ContactRepository", "Found existing contact for telegram: $telegramId")
                return@withContext Result.success(existingContact.toContact())
            }
            
            val contactName = name ?: telegramUsername ?: "Telegram User $telegramId"
            val newContact = Contact(
                name = contactName,
                telegramId = telegramId,
                telegramUsername = telegramUsername,
                isAutoSaved = true
            )
            
            contactDao.insertContact(newContact.toEntity())
            Log.d("ContactRepository", "Created new auto-saved contact for telegram: $telegramId")
            Result.success(newContact)
        } catch (e: Exception) {
            Log.e("ContactRepository", "Failed to find or create contact for telegram: $telegramId", e)
            Result.failure(e)
        }
    }
    
    private fun extractNameFromEmail(email: String): String {
        return email.substringBefore("@")
            .replace(".", " ")
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}