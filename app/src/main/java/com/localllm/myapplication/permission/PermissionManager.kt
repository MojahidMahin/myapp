package com.localllm.myapplication.permission

import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.localllm.myapplication.command.*

class PermissionManager(private val activity: Activity) {

    companion object {
        private const val TAG = "PermissionManager"
    }

    private var currentPermissionCommand: PermissionCommand? = null
    private val permissionResults = mutableMapOf<String, Boolean>()

    fun requestAllPermissions(
        onAllGranted: () -> Unit,
        onSomeDelayed: (List<String>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        Log.d(TAG, "Starting comprehensive permission request flow")
        
        try {
            // Start with notification permission first (most critical for background service)
            requestNotificationPermission(
                onSuccess = {
                    Log.d(TAG, "Notification permission granted, app ready for background operation")
                    onAllGranted()
                },
                onDenied = { denied ->
                    Log.w(TAG, "Notification permission denied: $denied")
                    onSomeDelayed(denied)
                },
                onError = onError
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in permission flow", e)
            onError(e)
        }
    }

    fun requestLocationPermissions(
        onSuccess: () -> Unit = {},
        onDenied: (List<String>) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        currentPermissionCommand = LocationPermissionCommand(
            activity, onSuccess, onDenied, onError
        )
        currentPermissionCommand?.execute()
    }

    fun requestSensorPermissions(
        onSuccess: () -> Unit = {},
        onDenied: (List<String>) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        currentPermissionCommand = SensorPermissionCommand(
            activity, onSuccess, onDenied, onError
        )
        currentPermissionCommand?.execute()
    }

    fun requestMediaPermissions(
        onSuccess: () -> Unit = {},
        onDenied: (List<String>) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        currentPermissionCommand = MediaPermissionCommand(
            activity, onSuccess, onDenied, onError
        )
        currentPermissionCommand?.execute()
    }

    fun requestBackgroundAppPermission(
        onSuccess: () -> Unit = {},
        onDenied: (List<String>) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        currentPermissionCommand = BackgroundAppPermissionCommand(
            activity, onSuccess, onDenied, onError
        )
        currentPermissionCommand?.execute()
    }

    fun requestNotificationPermission(
        onSuccess: () -> Unit = {},
        onDenied: (List<String>) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        currentPermissionCommand = NotificationPermissionCommand(
            activity, onSuccess, onDenied, onError
        )
        currentPermissionCommand?.execute()
    }

    fun handleRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            LocationPermissionCommand.REQUEST_CODE -> {
                (currentPermissionCommand as? LocationPermissionCommand)
                    ?.handleRequestPermissionsResult(requestCode, permissions, grantResults)
            }
            SensorPermissionCommand.REQUEST_CODE -> {
                (currentPermissionCommand as? SensorPermissionCommand)
                    ?.handleRequestPermissionsResult(requestCode, permissions, grantResults)
            }
            MediaPermissionCommand.REQUEST_CODE -> {
                (currentPermissionCommand as? MediaPermissionCommand)
                    ?.handleRequestPermissionsResult(requestCode, permissions, grantResults)
            }
            NotificationPermissionCommand.REQUEST_CODE -> {
                (currentPermissionCommand as? NotificationPermissionCommand)
                    ?.handleRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
        
        // Update permission results
        for (i in permissions.indices) {
            permissionResults[permissions[i]] = 
                grantResults[i] == PackageManager.PERMISSION_GRANTED
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int) {
        when (requestCode) {
            BackgroundAppPermissionCommand.REQUEST_CODE -> {
                (currentPermissionCommand as? BackgroundAppPermissionCommand)
                    ?.handleActivityResult(requestCode, resultCode)
            }
        }
    }

    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == 
            PackageManager.PERMISSION_GRANTED
    }

    fun getPermissionStatus(): Map<String, Boolean> = permissionResults.toMap()

    private inner class PermissionFlow(
        private val onAllGranted: () -> Unit,
        private val onSomeDelayed: (List<String>) -> Unit,
        private val onError: (Exception) -> Unit
    ) {
        private val deniedPermissions = mutableListOf<String>()
        private var currentStep = 0
        private val totalSteps = 5

        fun start() {
            requestNextPermissionGroup()
        }

        private fun requestNextPermissionGroup() {
            currentStep++
            
            when (currentStep) {
                1 -> requestNotificationPermission(::onStepSuccess, ::onStepDenied, onError)
                2 -> requestLocationPermissions(::onStepSuccess, ::onStepDenied, onError)
                3 -> requestSensorPermissions(::onStepSuccess, ::onStepDenied, onError)
                4 -> requestMediaPermissions(::onStepSuccess, ::onStepDenied, onError)
                5 -> requestBackgroundAppPermission(::onStepSuccess, ::onStepDenied, onError)
                else -> finishPermissionFlow()
            }
        }

        private fun onStepSuccess() {
            Log.d(TAG, "Permission group $currentStep granted")
            if (currentStep < totalSteps) {
                requestNextPermissionGroup()
            } else {
                finishPermissionFlow()
            }
        }

        private fun onStepDenied(denied: List<String>) {
            Log.w(TAG, "Permission group $currentStep denied: $denied")
            deniedPermissions.addAll(denied)
            if (currentStep < totalSteps) {
                requestNextPermissionGroup()
            } else {
                finishPermissionFlow()
            }
        }

        private fun finishPermissionFlow() {
            if (deniedPermissions.isEmpty()) {
                Log.d(TAG, "All permissions granted successfully")
                onAllGranted()
            } else {
                Log.w(TAG, "Some permissions denied: $deniedPermissions")
                onSomeDelayed(deniedPermissions)
            }
        }
    }
}