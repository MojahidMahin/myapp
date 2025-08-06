package com.localllm.myapplication.command

import android.app.Activity

abstract class PermissionCommand(
    protected val activity: Activity,
    protected val onSuccess: () -> Unit = {},
    protected val onDenied: (List<String>) -> Unit = {},
    protected val onError: (Exception) -> Unit = {}
) : Command {
    
    abstract fun getRequiredPermissions(): Array<String>
    abstract fun getPermissionRationale(): String
    abstract fun isPermissionCritical(): Boolean
    
    protected fun handlePermissionResult(
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        val deniedPermissions = mutableListOf<String>()
        
        for (i in permissions.indices) {
            if (grantResults[i] != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                deniedPermissions.add(permissions[i])
            }
        }
        
        if (deniedPermissions.isEmpty()) {
            onSuccess()
        } else {
            onDenied(deniedPermissions)
        }
    }
}