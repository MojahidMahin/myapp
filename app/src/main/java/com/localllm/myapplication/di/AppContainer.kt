package com.localllm.myapplication.di


import android.app.Activity
import android.content.Context
import com.localllm.myapplication.command.SignInCommand
import com.localllm.myapplication.data.*
import com.localllm.myapplication.permission.PermissionManager
import com.localllm.myapplication.service.*
import com.localllm.myapplication.ui.viewmodel.AuthViewModel
import com.localllm.myapplication.ui.viewmodel.ChatViewModel
import com.localllm.myapplication.ui.viewmodel.AIGalleryViewModel

object AppContainer {
    private var backgroundServiceManager: BackgroundServiceManager? = null
    private var permissionManager: PermissionManager? = null
    private var llmRepository: LLMRepository? = null
    private var chatViewModel: ChatViewModel? = null
    private var aiGalleryViewModel: AIGalleryViewModel? = null
    
    // Multi-user workflow services
    private var userManager: UserManager? = null
    private var workflowRepository: WorkflowRepository? = null
    private var userRepository: UserRepository? = null
    private var executionRepository: WorkflowExecutionRepository? = null
    private var aiWorkflowProcessor: AIWorkflowProcessor? = null
    private var workflowEngine: MultiUserWorkflowEngine? = null

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
            val modelManager = provideModelManager(context)
            chatViewModel = ChatViewModel(context, modelManager)
        }
        return chatViewModel!!
    }

    fun provideModelManager(context: Context): ModelManager {
        return ModelManager.getInstance(context)
    }

    fun provideAIGalleryViewModel(context: Context): AIGalleryViewModel {
        if (aiGalleryViewModel == null) {
            val modelManager = provideModelManager(context)
            aiGalleryViewModel = AIGalleryViewModel(context, modelManager)
        }
        return aiGalleryViewModel!!
    }

    // Multi-user workflow service providers
    fun provideUserManager(context: Context): UserManager {
        if (userManager == null) {
            userManager = UserManager(context.applicationContext)
        }
        return userManager!!
    }
    
    fun provideWorkflowRepository(): WorkflowRepository {
        if (workflowRepository == null) {
            workflowRepository = InMemoryWorkflowRepository()
        }
        return workflowRepository!!
    }
    
    fun provideUserRepository(): UserRepository {
        if (userRepository == null) {
            userRepository = InMemoryUserRepository()
        }
        return userRepository!!
    }
    
    fun provideExecutionRepository(): WorkflowExecutionRepository {
        if (executionRepository == null) {
            executionRepository = InMemoryWorkflowExecutionRepository()
        }
        return executionRepository!!
    }
    
    fun provideAIWorkflowProcessor(context: Context): AIWorkflowProcessor {
        if (aiWorkflowProcessor == null) {
            val modelManager = provideModelManager(context)
            aiWorkflowProcessor = AIWorkflowProcessor(modelManager)
        }
        return aiWorkflowProcessor!!
    }
    
    fun provideWorkflowEngine(context: Context): MultiUserWorkflowEngine {
        if (workflowEngine == null) {
            val userMgr = provideUserManager(context)
            val workflowRepo = provideWorkflowRepository()
            val executionRepo = provideExecutionRepository()
            val aiProcessor = provideAIWorkflowProcessor(context)
            
            workflowEngine = MultiUserWorkflowEngine(
                context = context.applicationContext,
                userManager = userMgr,
                workflowRepository = workflowRepo,
                executionRepository = executionRepo,
                aiProcessor = aiProcessor
            )
        }
        return workflowEngine!!
    }

    fun cleanup() {
        backgroundServiceManager?.stopBackgroundService()
        backgroundServiceManager = null
        permissionManager = null
        llmRepository?.unloadModel()
        llmRepository = null
        chatViewModel = null
        aiGalleryViewModel = null
        
        // Cleanup workflow services
        userManager = null
        workflowRepository = null
        userRepository = null
        executionRepository = null
        aiWorkflowProcessor = null
        workflowEngine = null
    }
}
