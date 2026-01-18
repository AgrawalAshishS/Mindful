package com.mindful.android.services.tracking

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import com.mindful.android.receivers.AccessibilityReceiver
import com.mindful.android.receivers.DeviceLockUnlockReceiver
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class LaunchTrackingManager(
    private val context: Context,
    private val onNewAppLaunched: (packageName: String) -> Unit,
    private val dismissOverlay: () -> Unit,
    private val cancelReminders: () -> Unit,
) {
    companion object {
        private const val TAG = "Mindful.LaunchTrackingManager"
    }

    private val executorService: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    private val lockUnlockReceiver = DeviceLockUnlockReceiver { isUnlocked ->
        if (!isUnlocked) onDeviceLocked()
    }
    private val accessibilityReceiver = AccessibilityReceiver(
        onNewAppLaunched = { executorService.submit { invokeNewAppLaunched(it) } },
        onServiceStatusChanged = { _ -> },
    )

    private var isTrackingPaused: Boolean = false
    private var lastLaunchedApp = ""

    init {
        // Register receivers
        lockUnlockReceiver.register(context)
        accessibilityReceiver.register(context)
    }

    @WorkerThread
    private fun onDeviceLocked() {
        // Cancel reminders and remove overlay
        cancelReminders.invoke()
        dismissOverlay.invoke()
        Log.d(TAG, "onDeviceLocked: Device locked, clearing overlays")
    }

    /**
     * Check and Invoke method when new app is launched and tracking is not paused.
     */
    private fun invokeNewAppLaunched(packageName: String) {
        lastLaunchedApp = packageName
        if (isTrackingPaused || packageName.isEmpty()) return

        onNewAppLaunched.invoke(packageName)
    }

    fun reInvokeLastLaunchEvent() = invokeNewAppLaunched(lastLaunchedApp)

    /**
     * Pauses or resumes app launch tracking.
     *
     * @param shouldPause True to pause tracking, false to resume.
     */
    fun pauseResumeTracking(shouldPause: Boolean) {
        isTrackingPaused = shouldPause
        if (!shouldPause) invokeNewAppLaunched(lastLaunchedApp)
    }

    /**
     * Detect if any app is opened and it is still active.
     * Show overlay if that app is marked as distracting bedtime app.
     */
    fun detectActiveAppForBedtime() {
        // With accessibility service, we rely on the last event received.
        // If the service is active, we should have the latest app.
        invokeNewAppLaunched(lastLaunchedApp)
    }

    fun dispose() {
        // Un-Register receivers
        lockUnlockReceiver.unRegister(context)
        accessibilityReceiver.unRegister(context)

        executorService.shutdownNow()

        onDeviceLocked()
    }
}
