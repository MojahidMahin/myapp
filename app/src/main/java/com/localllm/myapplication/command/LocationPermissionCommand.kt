package com.localllm.myapplication.command

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class LocationPermissionCommand(
    activity: Activity,
    onSuccess: () -> Unit = {},
    onDenied: (List<String>) -> Unit = {},
    onError: (Exception) -> Unit = {}
) : PermissionCommand(activity, onSuccess, onDenied, onError) {

    companion object {
        const val REQUEST_CODE_FOREGROUND = 1001
        const val REQUEST_CODE_BACKGROUND = 1002
        private const val TAG = "LocationPermissionCommand"
    }

    private var isRequestingBackground = false

    override fun execute() {
        try {
            Log.d(TAG, "Starting location permission request")
            // First request foreground location permissions only
            requestForegroundLocationPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting location permissions", e)
            onError(e)
        }
    }

    private fun requestForegroundLocationPermissions() {
        val foregroundPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        Log.d(TAG, "Requesting foreground location permissions: ${foregroundPermissions.joinToString()}")
        ActivityCompat.requestPermissions(
            activity,
            foregroundPermissions,
            REQUEST_CODE_FOREGROUND
        )
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isRequestingBackground = true
            Log.d(TAG, "Requesting background location permission")
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                REQUEST_CODE_BACKGROUND
            )
        } else {
            // Background location not needed for older versions
            onSuccess()
        }
    }

    override fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    override fun getPermissionRationale(): String =
        "This app needs location access to provide location-based services and track your activity in the background."

    override fun isPermissionCritical(): Boolean = true

    fun handleRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        Log.d(TAG, "Permission result received - requestCode: $requestCode, permissions: ${permissions.joinToString()}")
        
        when (requestCode) {
            REQUEST_CODE_FOREGROUND -> {
                val deniedPermissions = mutableListOf<String>()
                var hasForegroundLocationGranted = false
                
                for (i in permissions.indices) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        deniedPermissions.add(permissions[i])
                        Log.w(TAG, "Permission denied: ${permissions[i]}")
                    } else {
                        Log.d(TAG, "Permission granted: ${permissions[i]}")
                        if (permissions[i] == Manifest.permission.ACCESS_FINE_LOCATION || 
                            permissions[i] == Manifest.permission.ACCESS_COARSE_LOCATION) {
                            hasForegroundLocationGranted = true
                        }
                    }
                }
                
                if (hasForegroundLocationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Foreground location granted, now request background
                    requestBackgroundLocationPermission()
                } else if (deniedPermissions.isEmpty()) {
                    // All permissions granted (older Android)
                    onSuccess()
                } else {
                    // Some foreground permissions denied
                    onDenied(deniedPermissions)
                }
            }
            
            REQUEST_CODE_BACKGROUND -> {
                isRequestingBackground = false
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Background location permission granted")
                    onSuccess()
                } else {
                    Log.w(TAG, "Background location permission denied")
                    // Background denied but foreground was granted - still considered success for basic functionality
                    onDenied(listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                }
            }
        }
    }
}