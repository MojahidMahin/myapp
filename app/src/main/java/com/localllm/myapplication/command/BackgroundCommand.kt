package com.localllm.myapplication.command

interface BackgroundCommand : Command {
    fun canExecuteInBackground(): Boolean
    fun getExecutionTag(): String
    fun onExecutionComplete()
    fun onExecutionFailed(error: Exception)
}