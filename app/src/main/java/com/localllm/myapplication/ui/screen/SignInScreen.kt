package com.localllm.myapplication.ui.screen


import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.localllm.myapplication.command.BackgroundWorkCommand
import com.localllm.myapplication.di.AppContainer
import com.localllm.myapplication.ui.ChatActivity
import com.localllm.myapplication.ui.AIGalleryActivity
import com.localllm.myapplication.ui.viewmodel.AuthViewModel

@Composable
fun SignInScreen(viewModel: AuthViewModel) {
    val email by viewModel.userEmail
    val isSignedIn = email != null
    val context = LocalContext.current
    val backgroundServiceManager = remember { AppContainer.provideBackgroundServiceManager(context) }
    val permissionManager = remember { AppContainer.providePermissionManager(context as androidx.activity.ComponentActivity) }
    val modelManager = remember { AppContainer.provideModelManager(context) }
    
    // Model state
    val isModelLoaded by modelManager.isModelLoaded.collectAsState()
    val isModelLoading by modelManager.isModelLoading.collectAsState()
    val currentModelPath by modelManager.currentModelPath.collectAsState()
    val modelLoadError by modelManager.modelLoadError.collectAsState()
    
    // File picker launcher for model selection
    val modelFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            Log.d("SignInScreen", "Selected model file URI: $uri")
            // Show toast for file processing
            android.widget.Toast.makeText(
                context, 
                "Processing selected file...", 
                android.widget.Toast.LENGTH_SHORT
            ).show()
            
            modelManager.loadModelFromUri(uri) { result ->
                result.fold(
                    onSuccess = {
                        Log.d("SignInScreen", "Model loaded successfully")
                        android.widget.Toast.makeText(
                            context, 
                            "Model loaded successfully!", 
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    },
                    onFailure = { error ->
                        Log.e("SignInScreen", "Failed to load model", error)
                        android.widget.Toast.makeText(
                            context, 
                            "Failed to load model: ${error.message}", 
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        } else {
            Log.d("SignInScreen", "No file selected")
            android.widget.Toast.makeText(
                context, 
                "No file selected", 
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Model Management Section (Always visible)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AI Model Management",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Model status
                    if (isModelLoaded && currentModelPath != null) {
                        val modelName = currentModelPath!!.substringAfterLast("/")
                        Text(
                            text = "✅ Model loaded: $modelName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Ready for AI chat and analysis",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "❌ No model loaded",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Select a model file (.task or .tflite) from device storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "💡 Supported: Gemma, LLama, Mistral, Phi models",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Error message
                    modelLoadError?.let { error ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Error: $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Single Load/Unload Button
                    if (isModelLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Loading model...")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (isModelLoaded) {
                                    modelManager.unloadModel { result ->
                                        result.fold(
                                            onSuccess = {
                                                Log.d("SignInScreen", "Model unloaded successfully")
                                            },
                                            onFailure = { error ->
                                                Log.e("SignInScreen", "Failed to unload model", error)
                                            }
                                        )
                                    }
                                } else {
                                    // Launch file picker to select model from device storage
                                    Log.d("SignInScreen", "Launching model file picker")
                                    // Filter for common model file types
                                    modelFilePickerLauncher.launch("application/*")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (isModelLoaded) "🗑️ Unload Model" else "📁 Select Model File"
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Chat Access Section (Always visible)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AI Chat Access",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Chat buttons
                    Button(
                        onClick = {
                            Log.d("UI", "Chat button clicked - starting ChatActivity")
                            val intent = Intent(context, ChatActivity::class.java)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🤖 Local LLM Chat")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = {
                            Log.d("UI", "AI Gallery button clicked - starting AIGalleryActivity")
                            val intent = Intent(context, AIGalleryActivity::class.java)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🎨 AI Gallery (Full Features)")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Authentication Section
            if (isSignedIn) {
                Text(
                    text = "Signed in as:",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = email ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { viewModel.signOut() }) {
                    Text("Sign Out")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { viewModel.changeEmail() }) {
                    Text("Change Email")
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                // Background service controls
                Text(
                    text = "Background Service Controls",
                    style = MaterialTheme.typography.titleMedium
                )
                
                // Debug status button
                Button(
                    onClick = {
                        Log.d("UI", "=== SERVICE STATUS CHECK ===")
                        Log.d("UI", "Service running: ${backgroundServiceManager.isServiceRunning()}")
                        Log.d("UI", "=== END STATUS CHECK ===")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔍 Check Service Status")
                }
                
                // Simple test button
                Button(
                    onClick = {
                        Log.d("UI-TEST", "SIMPLE TEST BUTTON CLICKED - THIS SHOULD ALWAYS SHOW")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🧪 Simple Test Button")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            Log.d("UI", "=== SYNC DATA BUTTON CLICKED ===")
                            Log.d("UI", "Service running: ${backgroundServiceManager.isServiceRunning()}")
                            val command = BackgroundWorkCommand(BackgroundWorkCommand.WorkType.DATA_SYNC)
                            Log.d("UI", "Created command: ${command.getExecutionTag()}")
                            Log.d("UI", "About to execute command...")
                            backgroundServiceManager.executeBackgroundCommand(command)
                            Log.d("UI", "Command sent to service manager")
                            Log.d("UI", "=== BUTTON CLICK COMPLETE ===")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sync Data")
                    }
                    
                    Button(
                        onClick = {
                            Log.d("UI", "Clean Cache button clicked")
                            val command = BackgroundWorkCommand(BackgroundWorkCommand.WorkType.CACHE_CLEANUP)
                            Log.d("UI", "Created command: ${command.getExecutionTag()}")
                            backgroundServiceManager.executeBackgroundCommand(command)
                            Log.d("UI", "Command sent to service manager")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clean Cache")
                    }
                }
                
                Button(
                    onClick = {
                        Log.d("UI", "Custom Workflow button clicked")
                        val command = BackgroundWorkCommand(BackgroundWorkCommand.WorkType.CUSTOM_WORKFLOW)
                        Log.d("UI", "Created command: ${command.getExecutionTag()}")
                        backgroundServiceManager.executeBackgroundCommand(command)
                        Log.d("UI", "Command sent to service manager")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Run Custom Workflow")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Permission controls
                Text(
                    text = "Permission Controls",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { permissionManager.requestNotificationPermission() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔔 Request Notification Permission")
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { permissionManager.requestLocationPermissions() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Location")
                    }
                    
                    Button(
                        onClick = { permissionManager.requestSensorPermissions() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sensors")
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { permissionManager.requestMediaPermissions() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Media")
                    }
                    
                    Button(
                        onClick = { permissionManager.requestBackgroundAppPermission() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Background")
                    }
                }
                
                Button(
                    onClick = { 
                        permissionManager.requestAllPermissions(
                            onAllGranted = { /* Handle success */ },
                            onSomeDelayed = { /* Handle partial */ },
                            onError = { /* Handle error */ }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Request All Permissions")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Direct test button
                Button(
                    onClick = {
                        Log.d("UI", "Direct test button clicked")
                        val command = BackgroundWorkCommand(BackgroundWorkCommand.WorkType.DATA_SYNC)
                        Log.d("UI", "Executing command on background thread...")
                        Thread {
                            try {
                                command.execute()
                                Log.d("UI", "Direct execution completed")
                            } catch (e: Exception) {
                                Log.e("UI", "Direct execution failed", e)
                            }
                        }.start()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔧 Test Direct Execution")
                }
            } else {
                // Sign In Section for non-authenticated users
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Authentication",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sign in to access additional features",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.signIn() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🔐 Sign in with Google")
                        }
                    }
                }
            }
        }
    }
}
