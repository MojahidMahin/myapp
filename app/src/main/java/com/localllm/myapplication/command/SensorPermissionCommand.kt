package com.localllm.myapplication.command

import android.Manifest
import android.app.Activity
import androidx.core.app.ActivityCompat

class SensorPermissionCommand(
    activity: Activity,
    onSuccess: () -> Unit = {},
    onDenied: (List<String>) -> Unit = {},
    onError: (Exception) -> Unit = {}
) : PermissionCommand(activity, onSuccess, onDenied, onError) {

    companion object {
        const val REQUEST_CODE = 1002
    }

    override fun execute() {
        try {
            ActivityCompat.requestPermissions(
                activity,
                getRequiredPermissions(),
                REQUEST_CODE
            )
        } catch (e: Exception) {
            onError(e)
        }
    }

    override fun getRequiredPermissions(): Array<String> = arrayOf(
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.BODY_SENSORS_BACKGROUND,
        Manifest.permission.ACTIVITY_RECOGNITION
    )

    override fun getPermissionRationale(): String =
        "This app needs sensor access to monitor your device sensors and activity recognition for health and fitness tracking."

    override fun isPermissionCritical(): Boolean = false

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