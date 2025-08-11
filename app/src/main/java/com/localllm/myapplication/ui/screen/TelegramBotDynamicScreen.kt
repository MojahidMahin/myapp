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

import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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
    var botToken by remember { mutableStateOf("") }
    var targetUserId by remember { mutableStateOf("") }
    var selectedUserIds by remember { mutableStateOf(setOf<Long>()) }
    var messageToSend by remember { mutableStateOf("") }
    var receivedMessage by remember { mutableStateOf("No messages yet.") }
    var sendStatus by remember { mutableStateOf("") }
    var allUserIds by remember { mutableStateOf(setOf<Long>()) }
    var allUsers by remember { mutableStateOf(mapOf<Long, String>()) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun createTelegramService(): TelegramService {
        return Retrofit.Builder()
            .baseUrl("https://api.telegram.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkHttpClient())
            .build()
            .create(TelegramService::class.java)
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
                onValueChange = { botToken = it },
                label = { Text("Bot Token") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    scope.launch {
                        try {
                            val telegramService = createTelegramService()
                            val trimmedToken = botToken.trim()
                            val updatesUrl = "https://api.telegram.org/bot$trimmedToken/getUpdates"
                            val response = telegramService.getUpdates(updatesUrl)
                            if (response.isSuccessful) {
                                val updates = response.body()?.result
                                val userIds = updates?.mapNotNull { it.message?.chat?.id }?.toSet()
                                allUserIds = userIds ?: emptySet()
                                
                                val userMap = updates?.mapNotNull { update ->
                                    update.message?.chat?.let { chat ->
                                        val displayName = buildString {
                                            chat.first_name?.let { append(it) }
                                            chat.last_name?.let {
                                                if (isNotEmpty()) append(" ")
                                                append(it)
                                            }
                                            chat.username?.let { username ->
                                                if (isEmpty()) {
                                                    append("@$username")
                                                } else {
                                                    append(" (@$username)")
                                                }
                                            }
                                            if (isEmpty()) append("Unknown User")
                                        }
                                        chat.id to displayName
                                    }
                                }?.toMap() ?: emptyMap()
                                allUsers = userMap

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
                                            val userName = allUsers[update.message?.chat?.id] ?: "Unknown User"
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
                                val errorText = response.errorBody()?.string()
                                receivedMessage = "Failed to fetch messages. $errorText"
                            }
                        } catch (e: Exception) {
                            receivedMessage = "Error: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Fetch Updates & User IDs")
            }

            if (allUserIds.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("All Users:", fontSize = 18.sp)
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
                            }
                        ) {
                            Text("Clear All", fontSize = 12.sp)
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .height(150.dp)
                        .fillMaxWidth()
                ) {
                    items(allUserIds.toList()) { id ->
                        val userName = allUsers[id] ?: "Unknown User"
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
                            Column {
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
                        val userName = allUsers[id] ?: "Unknown User"
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
                            val telegramService = createTelegramService()
                            val trimmedToken = botToken.trim()
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
                            
                            for (userId in targetIds) {
                                try {
                                    val response = telegramService.sendMessage(
                                        sendUrl,
                                        userId,
                                        messageToSend
                                    )
                                    if (response.isSuccessful) {
                                        successCount++
                                    } else {
                                        failureCount++
                                    }
                                } catch (e: Exception) {
                                    failureCount++
                                }
                            }
                            
                            sendStatus = when {
                                failureCount == 0 -> "✅ Message sent to all $successCount users!"
                                successCount == 0 -> "❌ Failed to send message to all users."
                                else -> "⚠️ Sent to $successCount users, failed for $failureCount users."
                            }
                        } catch (e: Exception) {
                            sendStatus = "Error: ${e.message}"
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
