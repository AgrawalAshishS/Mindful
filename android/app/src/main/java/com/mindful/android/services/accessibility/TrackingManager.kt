package com.mindful.android.services.accessibility

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.WorkerThread
import com.mindful.android.AppConstants.SYSTEM_UI_PACKAGE
import com.mindful.android.helpers.storage.SharedPrefsHelper
import com.mindful.android.helpers.storage.UsageDatabaseHelper


class TrackingManager(
    private val context: Context,
) {

    companion object {
        const val ACTION_ACCESSIBILITY_ACTIVE = "com.mindful.android.action.accessibilityActive"
        const val ACTION_ACCESSIBILITY_INACTIVE = "com.mindful.android.action.accessibilityInactive"
        const val ACTION_NEW_APP_LAUNCHED = "com.mindful.android.action.newAppLaunched"
        const val EXTRA_PACKAGE_NAME: String = "com.mindful.android.extra.packageName"
    }

    private var lastActiveApp: String = ""
    private var sessionStartTime: Long = 0L
    private val dbHelper = UsageDatabaseHelper.getInstance(context)

    @WorkerThread
    fun onNewEvent(packageName: String) {
        if (packageName == SYSTEM_UI_PACKAGE) return

        if (lastActiveApp != packageName) {
            val currentTime = System.currentTimeMillis()

            // End previous session if exists
            if (lastActiveApp.isNotEmpty()) {
                dbHelper.insertUsageSession(lastActiveApp, sessionStartTime, currentTime)
            }

            // Start new session
            lastActiveApp = packageName
            sessionStartTime = currentTime

            broadcastEvent(ACTION_NEW_APP_LAUNCHED, packageName)
        }
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