package com.mindful.android.services.accessibility

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.WorkerThread
import com.mindful.android.AppConstants.SYSTEM_UI_PACKAGE
import com.mindful.android.helpers.storage.SharedPrefsHelper
import com.mindful.android.helpers.storage.UsageDatabaseHelper


class TrackingManager(
    private val context: Context,
    private val onNewAppLaunched: (String) -> Unit,
) {

    companion object {
        const val ACTION_ACCESSIBILITY_ACTIVE = "com.mindful.android.action.accessibilityActive"
        const val ACTION_ACCESSIBILITY_INACTIVE = "com.mindful.android.action.accessibilityInactive"
        const val EXTRA_PACKAGE_NAME: String = "com.mindful.android.extra.packageName"
    }

    private var lastActiveApp: String = ""
    val getLastActiveApp: String get() = lastActiveApp
    
    private var sessionStartTime: Long = 0L
    private var isPaused: Boolean = false
    private val dbHelper = UsageDatabaseHelper.getInstance(context)

    @WorkerThread
    fun onNewEvent(packageName: String) {
        if (isPaused) return

        val isHomeOrSystem = packageName == SYSTEM_UI_PACKAGE || isLauncher(packageName)

        // optimize: if we are already in "no app" state and receive home/system event, ignore
        if (lastActiveApp.isEmpty() && isHomeOrSystem) return

        if (lastActiveApp != packageName) {
            val currentTime = System.currentTimeMillis()

            // End previous session if exists
            if (lastActiveApp.isNotEmpty()) {
                dbHelper.insertUsageSession(lastActiveApp, sessionStartTime, currentTime)
            }

            if (isHomeOrSystem) {
                lastActiveApp = ""
                // No new session started
            } else {
                // Start new session
                lastActiveApp = packageName
                sessionStartTime = currentTime
                onNewAppLaunched(packageName)
            }
        }
    }

    private fun isLauncher(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo =
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return packageName == resolveInfo?.activityInfo?.packageName
    }

    fun pauseTracking() {
        isPaused = true
    }

    fun resumeTracking() {
        isPaused = false
    }

    // Called when accessibility service is stopped
    fun startManualTracking() {
        // Close any open session before stopping
        if (lastActiveApp.isNotEmpty()) {
            val currentTime = System.currentTimeMillis()
            dbHelper.insertUsageSession(lastActiveApp, sessionStartTime, currentTime)
            lastActiveApp = ""
        }
        broadcastEvent(ACTION_ACCESSIBILITY_INACTIVE)
    }

    // Called when accessibility service is started
    fun stopManualTracking() {
        // Reset state
        lastActiveApp = ""
        sessionStartTime = System.currentTimeMillis()
        broadcastEvent(ACTION_ACCESSIBILITY_ACTIVE)
    }


    private fun broadcastEvent(action: String, extraPackage: String? = null) {
        try {
            val intent = Intent(action).apply {
                setPackage(context.packageName)
                extraPackage?.let { putExtra(EXTRA_PACKAGE_NAME, it) }
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(
                "Mindful.Accessibility.TrackingManager",
                "broadcastEvent: Failed to send broadcast for action:$action",
                e
            )
            SharedPrefsHelper.insertCrashLogToPrefs(context, e)
        }
    }
}