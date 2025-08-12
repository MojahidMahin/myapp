package com.localllm.myapplication.service

import android.content.Context
import android.util.Log
import com.localllm.myapplication.data.*
import com.localllm.myapplication.service.integration.GmailIntegrationService
import com.localllm.myapplication.service.integration.TelegramBotService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * User management service for multi-user workflows
 * Handles user registration, authentication, and service connections
 */
class UserManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UserManager"
    }
    
    private val userRepository: UserRepository = InMemoryUserRepository()
    private val _currentUser = MutableStateFlow<WorkflowUser?>(null)
    val currentUser: StateFlow<WorkflowUser?> = _currentUser.asStateFlow()
    
    // Connected services per user
    private val gmailServices = mutableMapOf<String, GmailIntegrationService>()
    private val telegramServices = mutableMapOf<String, TelegramBotService>()
    
    /**
     * Register a new user or get existing user by email
     */
    suspend fun registerOrGetUser(email: String, displayName: String): Result<WorkflowUser> {
        return try {
            // Check if user already exists
            val existingUser = userRepository.getUserByEmail(email).getOrNull()
            if (existingUser != null) {
                _currentUser.value = existingUser
                Log.d(TAG, "User already exists: $email")
                return Result.success(existingUser)
            }
            
            // Create new user
            val newUser = WorkflowUser(
                id = UUID.randomUUID().toString(),
                email = email,
                displayName = displayName,
                permissions = setOf(
                    Permission.CREATE_WORKFLOW,
                    Permission.EDIT_WORKFLOW,
                    Permission.EXECUTE_WORKFLOW,
                    Permission.VIEW_WORKFLOW,
                    Permission.SHARE_WORKFLOW
                )
            )
            
            val result = userRepository.createUser(newUser)
            result.fold(
                onSuccess = {
                    _currentUser.value = newUser
                    Log.d(TAG, "User registered successfully: $email")
                    Result.success(newUser)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to register user: $email", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error registering user: $email", e)
            Result.failure(e)
        }
    }
    
    /**
     * Sign in existing user by email
     */
    suspend fun signInUser(email: String): Result<WorkflowUser> {
        return try {
            val user = userRepository.getUserByEmail(email).getOrNull()
            if (user != null) {
                _currentUser.value = user
                Log.d(TAG, "User signed in: $email")
                Result.success(user)
            } else {
                Log.w(TAG, "User not found: $email")
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error signing in user: $email", e)
            Result.failure(e)
        }
    }
    
    /**
     * Sign out current user
     */
    fun signOut() {
        _currentUser.value = null
        // Clear connected services
        gmailServices.clear()
        telegramServices.clear()
        Log.d(TAG, "User signed out")
    }
    
    /**
     * Connect Gmail for current user
     */
    suspend fun connectGmail(user: WorkflowUser): Result<Unit> {
        return try {
            // Create Gmail service for this user
            val gmailService = GmailIntegrationService(context)
            val initResult = gmailService.initialize()
            
            initResult.fold(
                onSuccess = {
                    gmailServices[user.id] = gmailService
                    
                    // Update user record
                    val updatedUser = user.copy(gmailConnected = true)
                    val updateResult = userRepository.updateUser(updatedUser)
                    updateResult.fold(
                        onSuccess = {
                            _currentUser.value = updatedUser
                            Log.d(TAG, "Gmail connected for user: ${user.email}")
                            Result.success(Unit)
                        },
                        onFailure = { error ->
                            gmailServices.remove(user.id)
                            Result.failure(error)
                        }
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to initialize Gmail service", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting Gmail for user: ${user.email}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Connect Telegram for current user
     */
    suspend fun connectTelegram(user: WorkflowUser, telegramUserId: Long, telegramUsername: String?): Result<Unit> {
        return try {
            // Create Telegram service for this user
            val telegramService = TelegramBotService(context)
            telegramServices[user.id] = telegramService
            
            // Update user record
            val updatedUser = user.copy(
                telegramConnected = true,
                telegramUserId = telegramUserId,
                telegramUsername = telegramUsername
            )
            
            val updateResult = userRepository.updateUser(updatedUser)
            updateResult.fold(
                onSuccess = {
                    _currentUser.value = updatedUser
                    Log.d(TAG, "Telegram connected for user: ${user.email}")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    telegramServices.remove(user.id)
                    Log.e(TAG, "Failed to update user with Telegram info", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting Telegram for user: ${user.email}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Disconnect Gmail for user
     */
    suspend fun disconnectGmail(user: WorkflowUser): Result<Unit> {
        return try {
            gmailServices.remove(user.id)
            
            val updatedUser = user.copy(gmailConnected = false)
            val updateResult = userRepository.updateUser(updatedUser)
            updateResult.fold(
                onSuccess = {
                    _currentUser.value = updatedUser
                    Log.d(TAG, "Gmail disconnected for user: ${user.email}")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to update user after Gmail disconnect", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting Gmail for user: ${user.email}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Disconnect Telegram for user
     */
    suspend fun disconnectTelegram(user: WorkflowUser): Result<Unit> {
        return try {
            telegramServices.remove(user.id)
            
            val updatedUser = user.copy(
                telegramConnected = false,
                telegramUserId = null,
                telegramUsername = null
            )
            
            val updateResult = userRepository.updateUser(updatedUser)
            updateResult.fold(
                onSuccess = {
                    _currentUser.value = updatedUser
                    Log.d(TAG, "Telegram disconnected for user: ${user.email}")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to update user after Telegram disconnect", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting Telegram for user: ${user.email}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get Gmail service for a user
     */
    fun getGmailService(userId: String): GmailIntegrationService? {
        return gmailServices[userId]
    }
    
    /**
     * Get Telegram service for a user
     */
    fun getTelegramService(userId: String): TelegramBotService? {
        return telegramServices[userId]
    }
    
    /**
     * Search users for sharing workflows
     */
    suspend fun searchUsers(query: String): Result<List<WorkflowUser>> {
        return try {
            val result = userRepository.searchUsers(query)
            result.fold(
                onSuccess = { users ->
                    // Filter out current user from search results
                    val currentUserId = _currentUser.value?.id
                    val filteredUsers = users.filter { it.id != currentUserId }
                    Log.d(TAG, "Found ${filteredUsers.size} users for query: $query")
                    Result.success(filteredUsers)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to search users", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error searching users", e)
            Result.failure(e)
        }
    }
    
    /**
     * Find user by email
     */
    suspend fun findUserByEmail(email: String): Result<WorkflowUser?> {
        return userRepository.getUserByEmail(email)
    }
    
    /**
     * Find user by Telegram ID
     */
    suspend fun findUserByTelegramId(telegramId: Long): Result<WorkflowUser?> {
        return userRepository.getUserByTelegramId(telegramId)
    }
    
    /**
     * Get user by ID
     */
    suspend fun getUserById(userId: String): Result<WorkflowUser?> {
        return userRepository.getUserById(userId)
    }
    
    /**
     * Update user profile
     */
    suspend fun updateUserProfile(user: WorkflowUser): Result<Unit> {
        return try {
            val result = userRepository.updateUser(user)
            result.fold(
                onSuccess = {
                    // Update current user if it's the same user
                    if (_currentUser.value?.id == user.id) {
                        _currentUser.value = user
                    }
                    Log.d(TAG, "User profile updated: ${user.email}")
                    Result.success(Unit)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to update user profile", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user profile", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all users (for admin purposes)
     */
    suspend fun getAllUsers(): Result<List<WorkflowUser>> {
        return userRepository.getAllUsers()
    }
    
    /**
     * Check if user has specific permission
     */
    fun hasPermission(user: WorkflowUser, permission: Permission): Boolean {
        return user.permissions.contains(permission) || 
               user.permissions.contains(Permission.ADMIN_ALL_WORKFLOWS)
    }
    
    /**
     * Check if current user is signed in
     */
    fun isSignedIn(): Boolean = _currentUser.value != null
    
    /**
     * Get current user ID
     */
    fun getCurrentUserId(): String? = _currentUser.value?.id
    
    /**
     * Check if user has both Gmail and Telegram connected
     */
    fun isFullyConnected(user: WorkflowUser): Boolean {
        return user.gmailConnected && user.telegramConnected
    }
    
    /**
     * Get connection status for user
     */
    fun getConnectionStatus(user: WorkflowUser): ConnectionStatus {
        return ConnectionStatus(
            gmail = user.gmailConnected,
            telegram = user.telegramConnected,
            hasGmailService = gmailServices.containsKey(user.id),
            hasTelegramService = telegramServices.containsKey(user.id)
        )
    }
    
    /**
     * Initialize services for existing user connections
     */
    suspend fun initializeUserServices(user: WorkflowUser): Result<Unit> {
        return try {
            var success = true
            val errors = mutableListOf<String>()
            
            // Initialize Gmail if connected
            if (user.gmailConnected && !gmailServices.containsKey(user.id)) {
                val gmailResult = connectGmail(user)
                if (gmailResult.isFailure) {
                    success = false
                    errors.add("Gmail initialization failed: ${gmailResult.exceptionOrNull()?.message}")
                }
            }
            
            // Initialize Telegram if connected
            if (user.telegramConnected && !telegramServices.containsKey(user.id) && user.telegramUserId != null) {
                val telegramResult = connectTelegram(user, user.telegramUserId!!, user.telegramUsername)
                if (telegramResult.isFailure) {
                    success = false
                    errors.add("Telegram initialization failed: ${telegramResult.exceptionOrNull()?.message}")
                }
            }
            
            if (success) {
                Log.d(TAG, "User services initialized successfully")
                Result.success(Unit)
            } else {
                val errorMessage = errors.joinToString("; ")
                Log.w(TAG, "Some services failed to initialize: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing user services", e)
            Result.failure(e)
        }
    }
}

/**
 * Connection status data class
 */
data class ConnectionStatus(
    val gmail: Boolean,
    val telegram: Boolean,
    val hasGmailService: Boolean,
    val hasTelegramService: Boolean
) {
    val isFullyConnected: Boolean get() = gmail && telegram
    val hasAllServices: Boolean get() = hasGmailService && hasTelegramService
}