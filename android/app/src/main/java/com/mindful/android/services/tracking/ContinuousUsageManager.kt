package com.mindful.android.services.tracking

import android.os.Handler
import android.os.Looper
import com.mindful.android.models.AppRestriction

class ContinuousUsageManager(
    private val overlayManager: OverlayManager,
    private val onAppBlocked: (String) -> Unit,
    private val onAppUnblocked: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var currentApp: String? = null
    private var runnable: Runnable? = null

    fun startTracking(appRestriction: AppRestriction) {
        stopTracking()
        currentApp = appRestriction.appPackage
        if (appRestriction.maxContinuousUsageSec > 0) {
            runnable = Runnable {
                onAppBlocked(appRestriction.appPackage)
                overlayManager.showSheetOverlay(
                    packageName = appRestriction.appPackage,
                    restrictionState = com.mindful.android.models.RestrictionState(
                        type = com.mindful.android.enums.RestrictionType.CONTINUOUS_USAGE,
                        timeLeftMillis = appRestriction.breakTimeSec * 1000L
                    ),
                )
                handler.postDelayed({
                    onAppUnblocked(appRestriction.appPackage)
                }, appRestriction.breakTimeSec * 1000L)
            }
            handler.postDelayed(runnable!!, appRestriction.maxContinuousUsageSec * 1000L)
        }
    }

    fun stopTracking() {
        runnable?.let { handler.removeCallbacks(it) }
        currentApp = null
        runnable = null
    }
}
