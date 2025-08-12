package com.localllm.myapplication.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

class TelegramPreferences(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "telegram_prefs"
        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_BOT_USERNAME = "bot_username"
        private const val KEY_LAST_UPDATE_ID = "last_update_id"
        private const val KEY_SAVED_USERS = "saved_users"
        private const val KEYSTORE_ALIAS = "telegram_key"
        private const val TRANSFORMATION = "AES/CBC/PKCS7Padding"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    init {
        generateKey()
    }
    
    private fun generateKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            
            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(false)
                    .build()
                
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            // Fallback to unencrypted storage if keystore fails
            android.util.Log.w("TelegramPreferences", "Failed to generate key, using unencrypted storage", e)
        }
    }
    
    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            null
        }
    }
    
    private fun encrypt(plaintext: String): String? {
        return try {
            val secretKey = getSecretKey() ?: return plaintext
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray())
            
            val ivAndEncrypted = iv + encryptedBytes
            Base64.encodeToString(ivAndEncrypted, Base64.DEFAULT)
        } catch (e: Exception) {
            android.util.Log.e("TelegramPreferences", "Encryption failed", e)
            plaintext
        }
    }
    
    private fun decrypt(encryptedData: String): String? {
        return try {
            val secretKey = getSecretKey() ?: return encryptedData
            val ivAndEncrypted = Base64.decode(encryptedData, Base64.DEFAULT)
            
            val iv = ivAndEncrypted.sliceArray(0..15)
            val encrypted = ivAndEncrypted.sliceArray(16 until ivAndEncrypted.size)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            
            String(cipher.doFinal(encrypted))
        } catch (e: Exception) {
            android.util.Log.e("TelegramPreferences", "Decryption failed", e)
            encryptedData
        }
    }
    
    fun saveBotToken(token: String) {
        val encryptedToken = encrypt(token)
        sharedPreferences.edit()
            .putString(KEY_BOT_TOKEN, encryptedToken)
            .apply()
    }
    
    fun getBotToken(): String? {
        val encryptedToken = sharedPreferences.getString(KEY_BOT_TOKEN, null)
        return encryptedToken?.let { decrypt(it) }
    }
    
    fun saveBotUsername(username: String) {
        sharedPreferences.edit()
            .putString(KEY_BOT_USERNAME, username)
            .apply()
    }
    
    fun getBotUsername(): String? {
        return sharedPreferences.getString(KEY_BOT_USERNAME, null)
    }
    
    fun saveLastUpdateId(updateId: Long) {
        sharedPreferences.edit()
            .putLong(KEY_LAST_UPDATE_ID, updateId)
            .apply()
    }
    
    fun getLastUpdateId(): Long {
        return sharedPreferences.getLong(KEY_LAST_UPDATE_ID, 0L)
    }
    
    fun clearAll() {
        sharedPreferences.edit()
            .clear()
            .apply()
    }
    
    fun hasBotToken(): Boolean {
        return getBotToken()?.isNotEmpty() == true
    }
    
    // User Management Functions
    fun saveUser(user: TelegramUser) {
        val savedUsers = getSavedUsers().toMutableMap()
        savedUsers[user.id] = user
        saveSavedUsers(savedUsers)
    }
    
    fun saveUsers(users: List<TelegramUser>) {
        val savedUsers = getSavedUsers().toMutableMap()
        users.forEach { user ->
            savedUsers[user.id] = user
        }
        saveSavedUsers(savedUsers)
    }
    
    fun getSavedUsers(): Map<Long, TelegramUser> {
        val usersJson = sharedPreferences.getString(KEY_SAVED_USERS, null)
        if (usersJson.isNullOrEmpty()) {
            return emptyMap()
        }
        
        return try {
            val jsonArray = JSONArray(usersJson)
            val users = mutableMapOf<Long, TelegramUser>()
            
            for (i in 0 until jsonArray.length()) {
                val userJson = jsonArray.getJSONObject(i)
                val user = TelegramUser.fromJson(userJson)
                users[user.id] = user
            }
            
            users
        } catch (e: Exception) {
            android.util.Log.e("TelegramPrefs", "Failed to parse saved users", e)
            emptyMap()
        }
    }
    
    private fun saveSavedUsers(users: Map<Long, TelegramUser>) {
        val jsonArray = JSONArray()
        users.values.forEach { user ->
            jsonArray.put(user.toJson())
        }
        
        sharedPreferences.edit()
            .putString(KEY_SAVED_USERS, jsonArray.toString())
            .apply()
    }
    
    fun deleteUser(userId: Long): Boolean {
        val savedUsers = getSavedUsers().toMutableMap()
        val removed = savedUsers.remove(userId)
        if (removed != null) {
            saveSavedUsers(savedUsers)
            return true
        }
        return false
    }
    
    fun deleteUsers(userIds: Set<Long>): Int {
        val savedUsers = getSavedUsers().toMutableMap()
        var deletedCount = 0
        
        userIds.forEach { userId ->
            if (savedUsers.remove(userId) != null) {
                deletedCount++
            }
        }
        
        if (deletedCount > 0) {
            saveSavedUsers(savedUsers)
        }
        
        return deletedCount
    }
    
    fun clearAllUsers() {
        sharedPreferences.edit()
            .remove(KEY_SAVED_USERS)
            .apply()
    }
    
    fun getUserCount(): Int {
        return getSavedUsers().size
    }
}