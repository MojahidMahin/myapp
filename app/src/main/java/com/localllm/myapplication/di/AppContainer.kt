package com.localllm.myapplication.di


import android.app.Activity
import android.content.Context
import com.localllm.myapplication.command.SignInCommand
import com.localllm.myapplication.data.FirebaseAuthRepository
import com.localllm.myapplication.data.LLMRepository
import com.localllm.myapplication.data.MediaPipeLLMRepository
import com.localllm.myapplication.data.HybridLLMRepository
import com.localllm.myapplication.permission.PermissionManager
import com.localllm.myapplication.service.BackgroundServiceManager
import com.localllm.myapplication.ui.viewmodel.AuthViewModel
import com.localllm.myapplication.ui.viewmodel.ChatViewModel

object AppContainer {
    private var backgroundServiceManager: BackgroundServiceManager? = null
    private var permissionManager: PermissionManager? = null
    private var llmRepository: LLMRepository? = null
    private var chatViewModel: ChatViewModel? = null

    fun provideAuthViewModel(activity: Activity): AuthViewModel {
        val authRepository = FirebaseAuthRepository(activity)
        val signInCommand = SignInCommand(authRepository)
        return AuthViewModel(signInCommand)
    }

    fun provideBackgroundServiceManager(context: Context): BackgroundServiceManager {
        if (backgroundServiceManager == null) {
            backgroundServiceManager = BackgroundServiceManager(context.applicationContext)
        }
        return backgroundServiceManager!!
    }

    fun providePermissionManager(activity: Activity): PermissionManager {
        permissionManager = PermissionManager(activity)
        return permissionManager!!
    }

    fun provideLLMRepository(context: Context): LLMRepository {
        if (llmRepository == null) {
            llmRepository = HybridLLMRepository(context.applicationContext)
        }
        return llmRepository!!
    }

    fun provideChatViewModel(context: Context): ChatViewModel {
        if (chatViewModel == null) {
            val repository = provideLLMRepository(context)
            chatViewModel = ChatViewModel(repository)
        }
        return chatViewModel!!
    }

    fun cleanup() {
        backgroundServiceManager?.stopBackgroundService()
        backgroundServiceManager = null
        permissionManager = null
        llmRepository?.unloadModel()
        llmRepository = null
        chatViewModel = null
    }
}
