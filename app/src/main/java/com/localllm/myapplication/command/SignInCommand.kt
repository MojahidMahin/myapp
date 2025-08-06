package com.localllm.myapplication.command

import android.content.Intent
import com.localllm.myapplication.data.FirebaseAuthRepository

class SignInCommand(val authRepository: FirebaseAuthRepository) : Command {
    override fun execute() {
        authRepository.signIn()
    }

    fun handleResult(data: Intent?, onSuccess: () -> Unit) {
        authRepository.handleSignInResult(data, onSuccess)
    }
}
