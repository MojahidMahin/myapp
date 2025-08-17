package com.localllm.myapplication.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.localllm.myapplication.data.Contact
import com.localllm.myapplication.di.AppContainer
import com.localllm.myapplication.ui.theme.MyApplicationTheme
import com.localllm.myapplication.ui.theme.*
import kotlinx.coroutines.launch

class ContactActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MyApplicationTheme {
                GradientBackground {
                    ContactScreen(
                        onNavigateBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val contactRepository = remember { AppContainer.provideContactRepository(context) }
    
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableStateOf(0) }
    var selectedTab by remember { mutableStateOf(0) }
    
    val refreshContacts = {
        isLoading = true
        refreshTrigger += 1
    }
    
    LaunchedEffect(refreshTrigger, searchQuery, selectedTab) {
        try {
            val result = when {
                searchQuery.isNotBlank() -> contactRepository.searchContacts(searchQuery)
                selectedTab == 1 -> contactRepository.getAutoSavedContacts()
                else -> contactRepository.getAllContacts()
            }
            
            result.fold(
                onSuccess = { contactList ->
                    contacts = contactList
                    isLoading = false
                },
                onFailure = { error ->
                    Log.e("ContactScreen", "Error loading contacts: ${error.message}")
                    isLoading = false
                }
            )
        } catch (e: Exception) {
            Log.e("ContactScreen", "Exception loading contacts", e)
            isLoading = false
        }
    }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshContacts()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Contacts") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = {
                    scope.launch {
                        val contactAutoSaveService = AppContainer.provideContactAutoSaveService(context)
                        contactAutoSaveService.syncTelegramUsers()
                        refreshContacts()
                    }
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync Telegram Users")
                }
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Contact")
                }
            }
        )
        
        // Search bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search contacts") },
                placeholder = { Text("Enter name, email, or telegram username...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true
            )
        }
        
        // Tabs
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                text = { Text("All Contacts") },
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 }
            )
            Tab(
                text = { Text("Auto-Saved") },
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 }
            )
        }
        
        // Content
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (contacts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (searchQuery.isNotBlank()) "No contacts found" else "No contacts yet",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (searchQuery.isNotBlank()) 
                        "Try searching with a different term" 
                    else 
                        "Add contacts manually or they'll be saved automatically from workflows",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Contact")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(contacts) { contact ->
                    ContactCard(
                        contact = contact,
                        onEdit = { editedContact ->
                            scope.launch {
                                contactRepository.updateContact(editedContact).fold(
                                    onSuccess = { refreshContacts() },
                                    onFailure = { error ->
                                        Log.e("ContactScreen", "Failed to update contact", error)
                                    }
                                )
                            }
                        },
                        onDelete = {
                            scope.launch {
                                contactRepository.deleteContact(contact).fold(
                                    onSuccess = { refreshContacts() },
                                    onFailure = { error ->
                                        Log.e("ContactScreen", "Failed to delete contact", error)
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
    
    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onAddContact = { newContact ->
                scope.launch {
                    contactRepository.insertContact(newContact).fold(
                        onSuccess = {
                            showAddDialog = false
                            refreshContacts()
                        },
                        onFailure = { error ->
                            Log.e("ContactScreen", "Failed to add contact", error)
                        }
                    )
                }
            }
        )
    }
}

@Composable
private fun ContactCard(
    contact: Contact,
    onEdit: (Contact) -> Unit,
    onDelete: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = contact.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (contact.isAutoSaved) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "Auto",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = contact.getDisplayInfo(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
    
    if (showEditDialog) {
        EditContactDialog(
            contact = contact,
            onDismiss = { showEditDialog = false },
            onSaveContact = { editedContact ->
                onEdit(editedContact)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onAddContact: (Contact) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var gmail by remember { mutableStateOf("") }
    var telegramId by remember { mutableStateOf("") }
    var telegramUsername by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Contact") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = gmail,
                    onValueChange = { gmail = it },
                    label = { Text("Gmail") },
                    placeholder = { Text("example@gmail.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = telegramId,
                    onValueChange = { telegramId = it },
                    label = { Text("Telegram ID") },
                    placeholder = { Text("123456789") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = telegramUsername,
                    onValueChange = { telegramUsername = it },
                    label = { Text("Telegram Username") },
                    placeholder = { Text("username") },
                    leadingIcon = { Text("@") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Text(
                    text = "* Required. At least one contact method (Gmail or Telegram) must be provided.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val contact = Contact(
                        name = name.trim(),
                        gmail = gmail.trim().takeIf { it.isNotBlank() },
                        telegramId = telegramId.trim().takeIf { it.isNotBlank() },
                        telegramUsername = telegramUsername.trim().takeIf { it.isNotBlank() }
                    )
                    if (contact.isValid()) {
                        onAddContact(contact)
                    }
                },
                enabled = name.trim().isNotBlank() && 
                    (gmail.trim().isNotBlank() || telegramId.trim().isNotBlank())
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditContactDialog(
    contact: Contact,
    onDismiss: () -> Unit,
    onSaveContact: (Contact) -> Unit
) {
    var name by remember { mutableStateOf(contact.name) }
    var gmail by remember { mutableStateOf(contact.gmail ?: "") }
    var telegramId by remember { mutableStateOf(contact.telegramId ?: "") }
    var telegramUsername by remember { mutableStateOf(contact.telegramUsername ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Contact") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = gmail,
                    onValueChange = { gmail = it },
                    label = { Text("Gmail") },
                    placeholder = { Text("example@gmail.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = telegramId,
                    onValueChange = { telegramId = it },
                    label = { Text("Telegram ID") },
                    placeholder = { Text("123456789") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = telegramUsername,
                    onValueChange = { telegramUsername = it },
                    label = { Text("Telegram Username") },
                    placeholder = { Text("username") },
                    leadingIcon = { Text("@") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedContact = contact.copy(
                        name = name.trim(),
                        gmail = gmail.trim().takeIf { it.isNotBlank() },
                        telegramId = telegramId.trim().takeIf { it.isNotBlank() },
                        telegramUsername = telegramUsername.trim().takeIf { it.isNotBlank() }
                    )
                    if (updatedContact.isValid()) {
                        onSaveContact(updatedContact)
                    }
                },
                enabled = name.trim().isNotBlank() && 
                    (gmail.trim().isNotBlank() || telegramId.trim().isNotBlank())
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}