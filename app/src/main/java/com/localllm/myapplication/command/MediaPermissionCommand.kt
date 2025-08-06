package com.localllm.myapplication.command

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.core.app.ActivityCompat

class MediaPermissionCommand(
    activity: Activity,
    onSuccess: () -> Unit = {},
    onDenied: (List<String>) -> Unit = {},
    onError: (Exception) -> Unit = {}
) : PermissionCommand(activity, onSuccess, onDenied, onError) {

    companion object {
        const val REQUEST_CODE = 1003
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

    override fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    override fun getPermissionRationale(): String =
        "This app needs camera, microphone, and storage access to capture and manage media files."

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