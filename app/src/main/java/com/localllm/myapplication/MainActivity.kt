package com.localllm.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.telegrambotcompose.ui.TelegramBotDynamicScreen
import com.localllm.myapplication.di.AppContainer
import com.localllm.myapplication.permission.PermissionManager
import com.localllm.myapplication.service.BackgroundServiceManager
import com.localllm.myapplication.ui.screen.SignInScreen

class MainActivity : ComponentActivity() {

    private val authViewModel by lazy { AppContainer.provideAuthViewModel(this) }
    private val backgroundServiceManager by lazy { AppContainer.provideBackgroundServiceManager(this) }
    private val permissionManager by lazy { AppContainer.providePermissionManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start background service immediately (permissions will be requested later)
        backgroundServiceManager.startBackgroundService()
        
        setContent {


            val navController = rememberNavController()
            NavHost(navController, startDestination = "sign_in") {
                composable("sign_in") { SignInScreen(authViewModel) }
                composable("telegram_bot") { TelegramBotDynamicScreen() }
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(onClick = { navController.navigate("telegram_bot") }) {
                    Text("Open Telegram Bot")
                }
            }
        }
        
        // Request permissions after UI is ready (optional)
        // Commented out to prevent ANR during startup
        /*
        Handler(Looper.getMainLooper()).postDelayed({
            permissionManager.requestNotificationPermission(
                onSuccess = { Log.d("MainActivity", "Notification permission granted") },
                onDenied = { Log.w("MainActivity", "Notification permission denied") },
                onError = { Log.e("MainActivity", "Permission error", it) }
            )
        }, 2000)
        */
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            9001 -> authViewModel.handleSignInResult(data)
            9002 -> authViewModel.handleChangeEmailResult(data)
            else -> permissionManager.handleActivityResult(requestCode, resultCode)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.handleRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            AppContainer.cleanup()
        }
    }
}
