package com.localllm.myapplication.ui.screen


import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.localllm.myapplication.ui.viewmodel.AuthViewModel

@Composable
fun SignInScreen(viewModel: AuthViewModel) {
    val email by viewModel.userEmail
    val isSignedIn = email != null

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
            } else {
                Button(onClick = { viewModel.signIn() }) {
                    Text("Sign in with Google")
                }
            }
        }
    }
}
