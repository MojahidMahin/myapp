package com.localllm.myapplication.command

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.core.app.ActivityCompat

class NotificationPermissionCommand(
    activity: Activity,
    onSuccess: () -> Unit = {},
    onDenied: (List<String>) -> Unit = {},
    onError: (Exception) -> Unit = {}
) : PermissionCommand(activity, onSuccess, onDenied, onError) {

    companion object {
        const val REQUEST_CODE = 1005
    }

    override fun execute() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    activity,
                    getRequiredPermissions(),
                    REQUEST_CODE
                )
            } else {
                // Notifications are automatically granted on older versions
                onSuccess()
            }
        } catch (e: Exception) {
            onError(e)
        }
    }

    override fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf() // No permission needed for older versions
        }
    }

    override fun getPermissionRationale(): String =
        "This app needs notification permission to show background service status and important updates."

    override fun isPermissionCritical(): Boolean = true

    fun handleRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE) {
            handlePermissionResult(permissions, grantResults)
        }
    }
}