package com.assistant.core.transcription.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Helper for managing RECORD_AUDIO permission
 *
 * Usage:
 * ```kotlin
 * val permissionHelper = AudioPermissionHelper(activity)
 * permissionHelper.requestPermission { granted ->
 *     if (granted) {
 *         // Start recording
 *     } else {
 *         // Show error message
 *     }
 * }
 * ```
 */
class AudioPermissionHelper(private val activity: ComponentActivity) {

    private var callback: ((Boolean) -> Unit)? = null

    private val requestPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        callback?.invoke(isGranted)
        callback = null
    }

    /**
     * Check if audio recording permission is granted
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Request audio recording permission
     *
     * @param onResult Callback with permission result (true if granted)
     */
    fun requestPermission(onResult: (Boolean) -> Unit) {
        if (hasPermission()) {
            onResult(true)
            return
        }

        callback = onResult
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    companion object {
        /**
         * Check if audio permission is granted (static helper for non-activity contexts)
         */
        fun hasPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
