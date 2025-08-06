package com.localllm.myapplication.di


import android.app.Activity
import com.localllm.myapplication.command.SignInCommand
import com.localllm.myapplication.data.FirebaseAuthRepository
import com.localllm.myapplication.ui.viewmodel.AuthViewModel

object AppContainer {
    fun provideAuthViewModel(activity: Activity): AuthViewModel {
        val authRepository = FirebaseAuthRepository(activity)
        val signInCommand = SignInCommand(authRepository)
        return AuthViewModel(signInCommand)
    }
}
