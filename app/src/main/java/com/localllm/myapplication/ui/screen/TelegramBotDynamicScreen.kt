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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            OutlinedTextField(
                value = botToken,
                onValueChange = { 
                    botToken = it
                    tokenValidationMessage = ""
                },
                label = { Text("Bot Token") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    if (tokenValidationMessage.isNotEmpty()) {
                        Text(
                            text = tokenValidationMessage,
                            color = if (tokenValidationMessage.contains("Valid")) Color.Green else Color.Red
                        )
                    }
                }
            )
            
            if (telegramPrefs.hasBotToken()) {
                Text(
                    text = "Saved token: ${TelegramUtils.formatTokenForDisplay(telegramPrefs.getBotToken())}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
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
                modifier = Modifier.weight(1f)
            ) {
                Text("Fetch Updates & User IDs")
            }
            
            Button(
                onClick = {
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
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
            ) {
                Text("Clear Updates")
            }
        }

            if (allUserIds.isNotEmpty()) {
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

            OutlinedTextField(
                value = targetUserId,
                onValueChange = { targetUserId = it },
                label = { Text("Target User ID") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Last Message:\n$receivedMessage")

            OutlinedTextField(
                value = messageToSend,
                onValueChange = { messageToSend = it },
                label = { Text("Message to Send") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
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
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedUserIds.isNotEmpty() || targetUserId.isNotBlank()
            ) {
                val targetCount = if (selectedUserIds.isNotEmpty()) selectedUserIds.size else if (targetUserId.isNotBlank()) 1 else 0
                Text(if (targetCount > 1) "Send Message to $targetCount Users" else "Send Message")
            }

            if (sendStatus.isNotBlank()) {
                Text(sendStatus)
            }
        }
    }
}
