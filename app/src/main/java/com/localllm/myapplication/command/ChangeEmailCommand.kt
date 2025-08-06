package com.localllm.myapplication.command

import android.content.Intent
import com.localllm.myapplication.data.FirebaseAuthRepository

class ChangeEmailCommand(
    private val authRepository: FirebaseAuthRepository,
    private val onSuccess: () -> Unit,
    private val onError: (Exception?) -> Unit
) : Command {
    override fun execute() {
        authRepository.changeEmailWithOAuth()
    }

    fun handleResult(data: Intent?) {
        authRepository.handleChangeEmailResult(data, onSuccess, onError)
    }
}