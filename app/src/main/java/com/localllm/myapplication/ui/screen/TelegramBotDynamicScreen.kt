package com.localllm.myapplication.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localllm.myapplication.command.model.GetUpdatesResponse
import com.localllm.myapplication.command.service.TelegramService
import com.localllm.myapplication.data.TelegramPreferences
import com.localllm.myapplication.data.TelegramUser
import com.localllm.myapplication.utils.TelegramUtils

import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.collections.filter
import kotlin.collections.firstOrNull
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import kotlin.collections.map
import kotlin.collections.mapNotNull
import kotlin.collections.minus
import kotlin.collections.plus
import kotlin.collections.takeLast
import kotlin.collections.toList
import kotlin.collections.toMap
import kotlin.collections.toSet
import kotlin.jvm.java
import kotlin.let
import kotlin.text.isEmpty
import kotlin.text.isNotBlank
import kotlin.text.isNotEmpty
import kotlin.text.trim
import kotlin.to
import kotlin.toString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramBotDynamicScreen() {
    val context = LocalContext.current
    val telegramPrefs = remember { TelegramPreferences(context) }
    
    var botToken by remember { mutableStateOf(telegramPrefs.getBotToken() ?: "") }
    var targetUserId by remember { mutableStateOf("") }
    var selectedUserIds by remember { mutableStateOf(setOf<Long>()) }
    var messageToSend by remember { mutableStateOf("") }
    var receivedMessage by remember { mutableStateOf("No messages yet.") }
    var sendStatus by remember { mutableStateOf("") }
    var allUserIds by remember { mutableStateOf(setOf<Long>()) }
    var allUsers by remember { mutableStateOf(mapOf<Long, TelegramUser>()) }
    var savedUsers by remember { mutableStateOf(telegramPrefs.getSavedUsers()) }
    var tokenValidationMessage by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    
    // Initialize with saved users
    LaunchedEffect(Unit) {
        savedUsers = telegramPrefs.getSavedUsers()
        allUsers = savedUsers
        allUserIds = savedUsers.keys
    }

    fun createTelegramService(): TelegramService {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
            
        return Retrofit.Builder()
            .baseUrl("https://api.telegram.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
            .create(TelegramService::class.java)
    }
    
    fun validateAndSaveToken(token: String): Boolean {
        val (isValid, message) = TelegramUtils.validateTokenFormat(token)
        tokenValidationMessage = message
        
        if (isValid) {
            telegramPrefs.saveBotToken(token.trim())
            return true
        }
        return false
    }

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("User ID", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
    }

    val scrollState = rememberScrollState()
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Beautiful Header Card
            ModernTelegramHeader()
            
            // Bot Token Section
            ModernBotTokenCard(
                botToken = botToken,
                onTokenChange = { 
                    botToken = it
                    tokenValidationMessage = ""
                },
                validationMessage = tokenValidationMessage,
                savedToken = if (telegramPrefs.hasBotToken()) telegramPrefs.getBotToken() else null
            )


            // Action Buttons Section - Horizontal Scrollable Row
            ModernActionButtonsSection(
                onFetchUpdates = {
                        scope.launch {
                            try {
                                val trimmedToken = botToken.trim()
                                
                                if (!validateAndSaveToken(trimmedToken)) {
                                    receivedMessage = "❌ Invalid token format"
                                    return@launch
                                }
                                
                                val telegramService = createTelegramService()
                                val offset = telegramPrefs.getLastUpdateId() + 1
                                val updatesUrl = "https://api.telegram.org/bot$trimmedToken/getUpdates?offset=$offset&limit=10&timeout=0"
                                val response = telegramService.getUpdates(updatesUrl)
                            
                            if (response.isSuccessful) {
                                val updates = response.body()?.result
                                
                                // Update the last update ID to prevent conflicts
                                updates?.let { updatesList ->
                                    if (updatesList.isNotEmpty()) {
                                        val latestUpdateId = updatesList.maxOfOrNull { it.update_id } ?: 0
                                        if (latestUpdateId > telegramPrefs.getLastUpdateId()) {
                                            telegramPrefs.saveLastUpdateId(latestUpdateId)
                                        }
                                    }
                                }
                                
                                val userIds = updates?.mapNotNull { it.message?.chat?.id }?.toSet()
                                allUserIds = userIds ?: emptySet()
                                
                                // Convert updates to TelegramUser objects
                                val newUsers = updates?.mapNotNull { update ->
                                    update.message?.chat?.let { chat ->
                                        val telegramUser = TelegramUser.fromChatInfo(
                                            chat.id,
                                            chat.first_name,
                                            chat.last_name,
                                            chat.username
                                        )
                                        chat.id to telegramUser
                                    }
                                }?.toMap() ?: emptyMap()
                                
                                // Merge new users with saved users
                                val mergedUsers = savedUsers.toMutableMap()
                                newUsers.forEach { (id, user) ->
                                    mergedUsers[id] = user
                                }
                                
                                // Save new users persistently
                                if (newUsers.isNotEmpty()) {
                                    telegramPrefs.saveUsers(newUsers.values.toList())
                                    savedUsers = telegramPrefs.getSavedUsers()
                                    
                                    // Auto-save contacts for new Telegram users
                                    try {
                                        val contactAutoSaveService = com.localllm.myapplication.di.AppContainer.provideContactAutoSaveService(context)
                                        newUsers.values.forEach { telegramUser ->
                                            contactAutoSaveService.autoSaveTelegramContact(telegramUser)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("TelegramBotScreen", "Failed to auto-save contacts", e)
                                    }
                                }
                                
                                allUsers = mergedUsers

                                val targetIds = if (selectedUserIds.isNotEmpty()) {
                                    selectedUserIds.map { it.toString() }
                                } else if (targetUserId.isNotBlank()) {
                                    listOf(targetUserId)
                                } else {
                                    emptyList()
                                }
                                
                                if (targetIds.isNotEmpty()) {
                                    val filteredMessages = updates?.filter { update ->
                                        targetIds.contains(update.message?.chat?.id.toString())
                                    } ?: emptyList()
                                    
                                    if (filteredMessages.isNotEmpty()) {
                                        val messageBuilder = kotlin.text.StringBuilder()
                                        messageBuilder.append("Messages from selected users:\n\n")
                                        
                                        filteredMessages.takeLast(10).forEach { update ->
                                            val user = allUsers[update.message?.chat?.id]
                                            val userName = user?.displayName ?: "Unknown User"
                                            val message = update.message?.text ?: "No text"
                                            messageBuilder.append("$userName: $message\n")
                                        }
                                        
                                        receivedMessage = messageBuilder.toString()
                                    } else {
                                        receivedMessage = "No messages found from selected users."
                                    }
                                } else {
                                    receivedMessage = "Fetched ${userIds?.size ?: 0} user(s)."
                                }
                            } else {
                                val errorBody = response.errorBody()?.string()
                                val telegramError = TelegramUtils.parseApiError(errorBody)
                                val commonMessage = TelegramUtils.getCommonErrorMessage(response.code())
                                receivedMessage = "❌ $commonMessage\n${telegramError.description}"
                            }
                        } catch (e: Exception) {
                            receivedMessage = "❌ Network Error: ${e.message}\nCheck your internet connection."
                        }
                    }
                },
                onClearUpdates = {
                    scope.launch {
                        try {
                            val trimmedToken = botToken.trim()
                            
                            if (!TelegramUtils.isValidBotToken(trimmedToken)) {
                                receivedMessage = "❌ Please enter a valid bot token first"
                                return@launch
                            }
                            
                            val telegramService = createTelegramService()
                            // Use a very high offset to clear all pending updates
                            val clearUrl = "https://api.telegram.org/bot$trimmedToken/getUpdates?offset=-1&limit=1"
                            val response = telegramService.getUpdates(clearUrl)
                            
                            if (response.isSuccessful) {
                                telegramPrefs.saveLastUpdateId(0) // Reset offset
                                receivedMessage = "✅ Cleared all pending updates. You can now fetch fresh data."
                            } else {
                                val errorBody = response.errorBody()?.string()
                                val telegramError = TelegramUtils.parseApiError(errorBody)
                                receivedMessage = "❌ Failed to clear updates: ${telegramError.description}"
                            }
                        } catch (e: Exception) {
                            receivedMessage = "❌ Error clearing updates: ${e.message}"
                        }
                    }
                }
            )

            // Users Management Section
            if (allUserIds.isNotEmpty()) {
                ModernUsersSection(
                    savedUsers = savedUsers,
                    allUsers = allUsers,
                    allUserIds = allUserIds,
                    selectedUserIds = selectedUserIds,
                    onSelectAll = { selectedUserIds = allUserIds },
                    onClearSelection = { 
                        selectedUserIds = emptySet()
                        targetUserId = ""
                    },
                    onDeleteAll = {
                        scope.launch {
                            telegramPrefs.clearAllUsers()
                            savedUsers = emptyMap()
                            allUsers = emptyMap()
                            allUserIds = emptySet()
                            selectedUserIds = emptySet()
                            receivedMessage = "✅ All users deleted permanently"
                        }
                    },
                    onUserSelectionChanged = { id, isSelected ->
                        selectedUserIds = if (isSelected) {
                            selectedUserIds + id
                        } else {
                            selectedUserIds - id
                        }
                        targetUserId = selectedUserIds.firstOrNull()?.toString() ?: ""
                    },
                    onDeleteUser = { id ->
                        scope.launch {
                            telegramPrefs.deleteUser(id)
                            savedUsers = telegramPrefs.getSavedUsers()
                            allUsers = savedUsers
                            allUserIds = savedUsers.keys
                            selectedUserIds = selectedUserIds - id
                            if (targetUserId == id.toString()) {
                                targetUserId = ""
                            }
                        }
                    }
                )
            }
            
            // The old user section will be replaced 
            if (false && allUserIds.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Saved Users (${savedUsers.size}):", fontSize = 18.sp)
                    Row {
                        Button(
                            onClick = { selectedUserIds = allUserIds },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text("Select All", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { 
                                selectedUserIds = emptySet()
                                targetUserId = ""
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text("Clear Selection", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    telegramPrefs.clearAllUsers()
                                    savedUsers = emptyMap()
                                    allUsers = emptyMap()
                                    allUserIds = emptySet()
                                    selectedUserIds = emptySet()
                                    receivedMessage = "✅ All users deleted permanently"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                        ) {
                            Text("Delete All", fontSize = 12.sp)
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .height(150.dp)
                        .fillMaxWidth()
                ) {
                    items(allUserIds.toList()) { id ->
                        val user = allUsers[id]
                        val userName = user?.displayName ?: "Unknown User"
                        val isSelected = selectedUserIds.contains(id)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedUserIds = if (isSelected) {
                                        selectedUserIds - id
                                    } else {
                                        selectedUserIds + id
                                    }
                                    targetUserId = selectedUserIds.firstOrNull()?.toString() ?: ""
                                }
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedUserIds = if (checked) {
                                        selectedUserIds + id
                                    } else {
                                        selectedUserIds - id
                                    }
                                    targetUserId = selectedUserIds.firstOrNull()?.toString() ?: ""
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userName,
                                    color = if (isSelected) Color.Blue else Color.Black,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "ID: $id",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        telegramPrefs.deleteUser(id)
                                        savedUsers = telegramPrefs.getSavedUsers()
                                        allUsers = savedUsers
                                        allUserIds = savedUsers.keys
                                        selectedUserIds = selectedUserIds - id
                                        if (targetUserId == id.toString()) {
                                            targetUserId = ""
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f)),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text("Delete", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            if (selectedUserIds.isNotEmpty()) {
                Text("Selected Users (${selectedUserIds.size}):", fontSize = 16.sp)
                LazyColumn(
                    modifier = Modifier
                        .height(100.dp)
                        .fillMaxWidth()
                ) {
                    items(selectedUserIds.toList()) { id ->
                        val user = allUsers[id]
                        val userName = user?.displayName ?: "Unknown User"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• $userName (ID: $id)",
                                fontSize = 14.sp,
                                color = Color.Blue
                            )
                        }
                    }
                }
            }

            // Messages and Status Section
            ModernMessagesSection(
                receivedMessage = receivedMessage,
                selectedUserIds = selectedUserIds,
                allUsers = allUsers
            )
            
            // Message Input and Send Section
            ModernMessageInputSection(
                targetUserId = targetUserId,
                onTargetUserIdChange = { targetUserId = it },
                messageToSend = messageToSend,
                onMessageChange = { messageToSend = it },
                sendStatus = sendStatus,
                selectedUserIds = selectedUserIds,
                onSendMessage = {
                    scope.launch {
                        try {
                            val trimmedToken = botToken.trim()
                            
                            if (!TelegramUtils.isValidBotToken(trimmedToken)) {
                                sendStatus = "❌ Please enter a valid bot token first"
                                return@launch
                            }
                            
                            if (messageToSend.trim().isEmpty()) {
                                sendStatus = "❌ Please enter a message to send"
                                return@launch
                            }
                            
                            val telegramService = createTelegramService()
                            val sendUrl = "https://api.telegram.org/bot$trimmedToken/sendMessage"
                            
                            val targetIds = if (selectedUserIds.isNotEmpty()) {
                                selectedUserIds.map { it.toString() }
                            } else if (targetUserId.isNotBlank()) {
                                listOf(targetUserId)
                            } else {
                                emptyList()
                            }
                            
                            if (targetIds.isEmpty()) {
                                sendStatus = "❌ No target users selected."
                                return@launch
                            }
                            
                            var successCount = 0
                            var failureCount = 0
                            val failureReasons = mutableListOf<String>()
                            
                            for (userId in targetIds) {
                                try {
                                    val response = telegramService.sendMessage(
                                        sendUrl,
                                        userId,
                                        messageToSend.trim()
                                    )
                                    if (response.isSuccessful) {
                                        successCount++
                                    } else {
                                        failureCount++
                                        val errorBody = response.errorBody()?.string()
                                        val telegramError = TelegramUtils.parseApiError(errorBody)
                                        failureReasons.add("User $userId: ${telegramError.description}")
                                    }
                                } catch (e: Exception) {
                                    failureCount++
                                    failureReasons.add("User $userId: ${e.message}")
                                }
                            }
                            
                            sendStatus = when {
                                failureCount == 0 -> "✅ Message sent to all $successCount users!"
                                successCount == 0 -> "❌ Failed to send message to all users.\n${failureReasons.take(3).joinToString("\n")}"
                                else -> "⚠️ Sent to $successCount users, failed for $failureCount users.\nFirst failures:\n${failureReasons.take(2).joinToString("\n")}"
                            }
                        } catch (e: Exception) {
                            sendStatus = "❌ Network Error: ${e.message}"
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ModernTelegramHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Telegram Icon
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🤖 Telegram Bot Manager",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Connect, manage users, and send messages through your Telegram bot",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ModernBotTokenCard(
    botToken: String,
    onTokenChange: (String) -> Unit,
    validationMessage: String,
    savedToken: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Bot Token Configuration",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            OutlinedTextField(
                value = botToken,
                onValueChange = onTokenChange,
                label = { Text("Enter Bot Token") },
                placeholder = { Text("123456789:ABCdefGHIjklMNOpqrsTUVwxyz") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                supportingText = {
                    if (validationMessage.isNotEmpty()) {
                        Text(
                            text = validationMessage,
                            color = if (validationMessage.contains("Valid")) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "Get your bot token from @BotFather on Telegram",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            
            if (savedToken != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Saved: ${TelegramUtils.formatTokenForDisplay(savedToken)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernActionButtonsSection(
    onFetchUpdates: () -> Unit,
    onClearUpdates: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Bot Actions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Horizontal scrollable row of buttons that never stack vertically
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Fetch Updates Button
                Button(
                    onClick = onFetchUpdates,
                    modifier = Modifier.widthIn(min = 180.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fetch Updates")
                }
                
                // Clear Updates Button
                OutlinedButton(
                    onClick = onClearUpdates,
                    modifier = Modifier.widthIn(min = 150.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Updates")
                }
            }
        }
    }
}

@Composable
private fun ModernUsersSection(
    savedUsers: Map<Long, TelegramUser>,
    allUsers: Map<Long, TelegramUser>,
    allUserIds: Set<Long>,
    selectedUserIds: Set<Long>,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteAll: () -> Unit,
    onUserSelectionChanged: (Long, Boolean) -> Unit,
    onDeleteUser: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with user count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Saved Users",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${savedUsers.size} users found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Action buttons - horizontal scrollable row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSelectAll,
                    modifier = Modifier.widthIn(min = 100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Select All", fontSize = 12.sp)
                }
                
                OutlinedButton(
                    onClick = onClearSelection,
                    modifier = Modifier.widthIn(min = 120.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear Selection", fontSize = 12.sp)
                }
                
                OutlinedButton(
                    onClick = onDeleteAll,
                    modifier = Modifier.widthIn(min = 110.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete All", fontSize = 12.sp)
                }
            }
            
            // Users list
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 200.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allUserIds.toList()) { id ->
                    val user = allUsers[id]
                    val userName = user?.displayName ?: "Unknown User"
                    val isSelected = selectedUserIds.contains(id)
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUserSelectionChanged(id, !isSelected)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    onUserSelectionChanged(id, checked)
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) 
                                        MaterialTheme.colorScheme.onPrimaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "ID: $id",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onDeleteUser(id) }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete user",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // Selected users summary
            if (selectedUserIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Selected Users (${selectedUserIds.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        selectedUserIds.take(3).forEach { id ->
                            val user = allUsers[id]
                            val userName = user?.displayName ?: "Unknown User"
                            Text(
                                text = "• $userName (ID: $id)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (selectedUserIds.size > 3) {
                            Text(
                                text = "... and ${selectedUserIds.size - 3} more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernMessagesSection(
    receivedMessage: String,
    selectedUserIds: Set<Long>,
    allUsers: Map<Long, TelegramUser>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Messages & Status",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Latest Status:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = receivedMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernMessageInputSection(
    targetUserId: String,
    onTargetUserIdChange: (String) -> Unit,
    messageToSend: String,
    onMessageChange: (String) -> Unit,
    sendStatus: String,
    selectedUserIds: Set<Long>,
    onSendMessage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Send Message",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            OutlinedTextField(
                value = targetUserId,
                onValueChange = onTargetUserIdChange,
                label = { Text("Target User ID (Optional)") },
                placeholder = { Text("Leave empty to use selected users") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null
                    )
                },
                supportingText = {
                    if (selectedUserIds.isNotEmpty()) {
                        Text(
                            text = "${selectedUserIds.size} users selected above",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
            
            OutlinedTextField(
                value = messageToSend,
                onValueChange = onMessageChange,
                label = { Text("Message to Send") },
                placeholder = { Text("Enter your message here...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null
                    )
                }
            )
            
            // Send button that adapts to selection
            Button(
                onClick = onSendMessage,
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedUserIds.isNotEmpty() || targetUserId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                val targetCount = if (selectedUserIds.isNotEmpty()) selectedUserIds.size else if (targetUserId.isNotBlank()) 1 else 0
                Text(
                    text = if (targetCount > 1) "Send to $targetCount Users" else "Send Message",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Send status
            if (sendStatus.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        sendStatus.contains("✅") -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        sendStatus.contains("❌") -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                ) {
                    Text(
                        text = sendStatus,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
