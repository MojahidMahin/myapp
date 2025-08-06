package com.localllm.myapplication.command

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

class BackgroundAppPermissionCommand(
    activity: Activity,
    onSuccess: () -> Unit = {},
    onDenied: (List<String>) -> Unit = {},
    onError: (Exception) -> Unit = {}
) : PermissionCommand(activity, onSuccess, onDenied, onError) {

    companion object {
        const val REQUEST_CODE = 1004
    }

    override fun execute() {
        try {
            val powerManager = ContextCompat.getSystemService(activity, PowerManager::class.java)
            val packageName = activity.packageName
            
            if (powerManager?.isIgnoringBatteryOptimizations(packageName) == false) {
                // Request to ignore battery optimizations
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                activity.startActivityForResult(intent, REQUEST_CODE)
            } else {
                // Already have permission or not available
                onSuccess()
            }
        } catch (e: Exception) {
            onError(e)
        }
    }

    override fun getRequiredPermissions(): Array<String> = arrayOf(
        android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
    )

    override fun getPermissionRationale(): String =
        "This app needs to run in the background without battery optimization to ensure continuous operation and data synchronization."

    override fun isPermissionCritical(): Boolean = true

    fun handleActivityResult(requestCode: Int, resultCode: Int) {
        if (requestCode == REQUEST_CODE) {
            val powerManager = ContextCompat.getSystemService(activity, PowerManager::class.java)
            val packageName = activity.packageName
            
            if (powerManager?.isIgnoringBatteryOptimizations(packageName) == true) {
                onSuccess()
            } else {
                onDenied(listOf("Battery optimization permission denied"))
            }
        }
    }
}