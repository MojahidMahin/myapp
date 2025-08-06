package com.localllm.myapplication.ui.viewmodel

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.localllm.myapplication.command.SignInCommand
import com.localllm.myapplication.command.ChangeEmailCommand

class AuthViewModel(private val signInCommand: SignInCommand) : ViewModel() {

    val userEmail = mutableStateOf<String?>(null)
    private var changeEmailCommand: ChangeEmailCommand? = null

    init {
        signInCommand.authRepository.addAuthStateListener { email ->
            userEmail.value = email
        }
    }

    fun signIn() {
        signInCommand.execute()
    }

    fun handleSignInResult(data: Intent?) {
        signInCommand.handleResult(data) {
            userEmail.value = FirebaseAuth.getInstance().currentUser?.email
        }
    }

    fun handleChangeEmailResult(data: Intent?) {
        changeEmailCommand?.handleResult(data)
    }

    fun signOut() {
        signInCommand.authRepository.signOut()
        userEmail.value = null
    }

    fun changeEmail() {
        changeEmailCommand = ChangeEmailCommand(
            signInCommand.authRepository,
            onSuccess = {
                Log.d("ChangeEmail", "Email changed successfully via OAuth")
            },
            onError = { exception ->
                Log.e("ChangeEmail", "Failed to change email via OAuth", exception)
            }
        )
        changeEmailCommand?.execute()
    }

}
