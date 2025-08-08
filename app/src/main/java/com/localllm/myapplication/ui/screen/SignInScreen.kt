package com.localllm.myapplication.ui.screen


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
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
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // LLM Chat button
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
                
                // AI Gallery button
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
            } else {
                Button(onClick = { viewModel.signIn() }) {
                    Text("Sign in with Google")
                }
            }
        }
    }
}
