package com.localllm.myapplication.command

import android.Manifest
import android.app.Activity
import androidx.core.app.ActivityCompat

class LocationPermissionCommand(
    activity: Activity,
    onSuccess: () -> Unit = {},
    onDenied: (List<String>) -> Unit = {},
    onError: (Exception) -> Unit = {}
) : PermissionCommand(activity, onSuccess, onDenied, onError) {

    companion object {
        const val REQUEST_CODE = 1001
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
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )

    override fun getPermissionRationale(): String =
        "This app needs location access to provide location-based services and track your activity in the background."

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