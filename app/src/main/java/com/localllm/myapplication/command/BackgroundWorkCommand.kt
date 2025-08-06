package com.localllm.myapplication.command

import android.util.Log

class BackgroundWorkCommand(
    private val workType: WorkType,
    private val onSuccess: () -> Unit = {},
    private val onError: (Exception) -> Unit = {}
) : BackgroundCommand {

    enum class WorkType(val tag: String) {
        DATA_SYNC("data_sync"),
        CACHE_CLEANUP("cache_cleanup"),
        PERIODIC_CHECK("periodic_check"),
        CUSTOM_WORKFLOW("custom_workflow")
    }

    override fun execute() {
        when (workType) {
            WorkType.DATA_SYNC -> performDataSync()
            WorkType.CACHE_CLEANUP -> performCacheCleanup()
            WorkType.PERIODIC_CHECK -> performPeriodicCheck()
            WorkType.CUSTOM_WORKFLOW -> performCustomWorkflow()
        }
    }

    override fun canExecuteInBackground(): Boolean = true

    override fun getExecutionTag(): String = "BackgroundWork_${workType.tag}"

    override fun onExecutionComplete() {
        Log.d(getExecutionTag(), "Background work completed successfully")
        onSuccess()
    }

    override fun onExecutionFailed(error: Exception) {
        Log.e(getExecutionTag(), "Background work failed", error)
        onError(error)
    }

    private fun performDataSync() {
        Log.d(getExecutionTag(), "Starting data synchronization...")
        // Simulate work with Thread.sleep instead of coroutine delay
        Thread.sleep(2000)
        Log.d(getExecutionTag(), "Data synchronization completed")
    }

    private fun performCacheCleanup() {
        Log.d(getExecutionTag(), "Starting cache cleanup...")
        // Simulate work with Thread.sleep instead of coroutine delay
        Thread.sleep(1500)
        Log.d(getExecutionTag(), "Cache cleanup completed")
    }

    private fun performPeriodicCheck() {
        Log.d(getExecutionTag(), "Starting periodic check...")
        // Simulate work with Thread.sleep instead of coroutine delay
        Thread.sleep(1000)
        Log.d(getExecutionTag(), "Periodic check completed")
    }

    private fun performCustomWorkflow() {
        Log.d(getExecutionTag(), "Starting custom workflow...")
        // Simulate work with Thread.sleep instead of coroutine delay
        Thread.sleep(3000)
        Log.d(getExecutionTag(), "Custom workflow completed")
    }
}