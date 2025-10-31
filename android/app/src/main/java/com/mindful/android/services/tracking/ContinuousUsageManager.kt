package com.mindful.android.services.tracking

import android.os.Handler
import android.os.Looper
import com.mindful.android.models.AppRestriction
import java.util.concurrent.TimeUnit

class ContinuousUsageManager(
    private val onUsageExceeded: (String) -> Unit,
    private val onBreakTimeFinished: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var currentApp: String? = null
    private var startTime: Long = 0
    private var runnable: Runnable? = null

    fun startTracking(packageName: String, restriction: AppRestriction) {
        if (restriction.maxContinuousUsageSec <= 0) return

        if (currentApp == packageName) {
            // Already tracking
            return
        }

        stopTracking()
        currentApp = packageName
        startTime = System.currentTimeMillis()

        runnable = Runnable {
            onUsageExceeded(packageName)
        }.also {
            handler.postDelayed(it, TimeUnit.SECONDS.toMillis(restriction.maxContinuousUsageSec.toLong()))
        }
    }

    fun stopTracking() {
        runnable?.let { handler.removeCallbacks(it) }
        currentApp = null
        runnable = null
    }

    fun startBreak(packageName: String, restriction: AppRestriction) {
        if (restriction.breakTimeSec <= 0) return

        handler.postDelayed({
            onBreakTimeFinished(packageName)
        }, TimeUnit.SECONDS.toMillis(restriction.breakTimeSec.toLong()))
    }
}
