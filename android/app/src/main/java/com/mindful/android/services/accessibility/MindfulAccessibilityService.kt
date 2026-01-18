/*
 *
 *  *
 *  *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *  *
 *  *  * This source code is licensed under the GPL-2.0 license license found in the
 *  *  * LICENSE file in the root directory of this source tree.
 *  *
 *
 */
package com.mindful.android.services.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.annotation.WorkerThread
import com.mindful.android.AppConstants.FACEBOOK_PACKAGE
import com.mindful.android.AppConstants.INSTAGRAM_PACKAGE
import com.mindful.android.AppConstants.REDDIT_PACKAGE
import com.mindful.android.AppConstants.SETTINGS_PACKAGE
import com.mindful.android.AppConstants.SNAPCHAT_PACKAGE
import com.mindful.android.AppConstants.YOUTUBE_PACKAGE
import com.mindful.android.R
import com.mindful.android.enums.PlatformFeatures
import com.mindful.android.enums.RestrictionType
import com.mindful.android.helpers.device.PermissionsHelper
import com.mindful.android.helpers.storage.SharedPrefsHelper
import com.mindful.android.models.Wellbeing
import com.mindful.android.receivers.DeviceAppsChangedReceiver
import com.mindful.android.services.tracking.ContinuousUsageManager
import com.mindful.android.services.tracking.OverlayManager
import com.mindful.android.services.tracking.ReminderManager
import com.mindful.android.services.tracking.RestrictionManager
import com.mindful.android.utils.JsonUtils
import com.mindful.android.utils.ThreadUtils
import com.mindful.android.utils.executors.Throttler
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * An AccessibilityService that monitors app usage and blocks access to specified content based on user settings.
 */
class MindfulAccessibilityService : AccessibilityService(), OnSharedPreferenceChangeListener {
    companion object {
        private const val TAG = "Mindful.MindfulAccessibilityService"

        const val ACTION_PERFORM_HOME_PRESS = "com.mindful.android.action.performHomePress"
        const val ACTION_MIDNIGHT_ACCESSIBILITY_RESET =
            "com.mindful.android.action.midnightAccessibilityReset"
        const val ACTION_TAMPER_PROTECTION_CHANGED =
            "com.mindful.android.action.tamperProtectionChanged"
        const val ACTION_PAUSE_TRACKING = "com.mindful.android.action.pauseTracking"
        const val ACTION_RESUME_TRACKING = "com.mindful.android.action.resumeTracking"

        // Set of desired events which will be processed
        private val desiredEvents = setOf(
            TYPE_WINDOWS_CHANGED,
            TYPE_WINDOW_STATE_CHANGED,
            TYPE_VIEW_SCROLLED
        )

        private val browserPackages = mutableSetOf<String>()
        private val shortsPlatformPackages = mutableSetOf<String>()
        private val devicePlatformPackages = mutableSetOf<String>()
    }


    // Fixed thread pool for parallel event processing
    private val executorService: ExecutorService = Executors.newFixedThreadPool(4)
    private val throttler: Throttler = Throttler(500L)
    private val deviceAppsChangedReceiver: DeviceAppsChangedReceiver =
        DeviceAppsChangedReceiver(onAppsChanged = { refreshServiceConfig() })

    // Managers
    private lateinit var shortsPlatformManager: ShortsPlatformManager
    private lateinit var browserManager: BrowserManager
    private lateinit var deviceFeaturesManager: DeviceFeaturesManager
    private lateinit var trackingManager: TrackingManager

    // Restriction Managers
    private lateinit var overlayManager: OverlayManager
    private lateinit var reminderManager: ReminderManager
    private lateinit var continuousUsageManager: ContinuousUsageManager
    private lateinit var restrictionManager: RestrictionManager

    private var wellbeing = Wellbeing()

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Restriction Managers
        overlayManager = OverlayManager(this)
        reminderManager = ReminderManager(overlayManager, ::onNewAppLaunch)
        continuousUsageManager = ContinuousUsageManager({ packageName ->
            restrictionManager.addBlockedApp(packageName)
            onNewAppLaunch(packageName)
        }, { packageName ->
            restrictionManager.removeBlockedApp(packageName)
        })
        restrictionManager = RestrictionManager(this, { /* No-op: service stays alive */ }, continuousUsageManager)

        trackingManager = TrackingManager(context = this, onNewAppLaunched = ::onNewAppLaunch)
        
        deviceFeaturesManager = DeviceFeaturesManager(
            context = this,
            blockedContentGoBack = this::goBackWithToast
        )
        shortsPlatformManager = ShortsPlatformManager(
            context = this,
            blockedContentGoBack = this::goBackWithToast
        )
        browserManager = BrowserManager(
            context = this,
            shortsPlatformManager = shortsPlatformManager,
            blockedContentGoBack = this::goBackWithToast
        )

        // Register shared prefs listener and load data
        SharedPrefsHelper.registerUnregisterListenerToListenablePrefs(this, true, this)
        wellbeing = SharedPrefsHelper.getSetWellBeingSettings(this, null)
        loadRestrictionsFromPrefs()

        // Register listener for install and uninstall events
        deviceAppsChangedReceiver.register(this)
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MIDNIGHT_ACCESSIBILITY_RESET -> {
                shortsPlatformManager.resetShortsScreenTime()
                restrictionManager.resetCache()
                overlayManager.dismissSheetOverlay()
                reminderManager.cancelReminders()
                Log.d(TAG, "onStartCommand: Midnight reset completed")
            }

            ACTION_TAMPER_PROTECTION_CHANGED -> {
                Log.d(TAG, "onStartCommand: Tamper protection changed")
                refreshServiceConfig()
            }

            ACTION_PERFORM_HOME_PRESS -> {
                Log.d(TAG, "onStartCommand: Pressing home button")
                goBackWithToast(GLOBAL_ACTION_HOME)
            }

            ACTION_PAUSE_TRACKING -> {
                trackingManager.pauseTracking()
                Log.d(TAG, "onStartCommand: Tracking paused")
            }

            ACTION_RESUME_TRACKING -> {
                trackingManager.resumeTracking()
                Log.d(TAG, "onStartCommand: Tracking resumed")
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onServiceConnected() {
        refreshServiceConfig()
        loadRestrictionsFromPrefs()
        trackingManager.stopManualTracking()
        Log.d(TAG, "onCreate: Accessibility service started successfully")
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            // If not desired event or executor is shutdown, then just return
            if (!desiredEvents.contains(event.eventType) || executorService.isShutdown) return

            executorService.submit {
                // Determine package and event source node
                val eventPackageName = event.packageName.toString()
                val node = if (eventPackageName == REDDIT_PACKAGE) event.source
                else rootInActiveWindow ?: event.source

                node?.let {
                    // Broadcast event
                    trackingManager.onNewEvent("${it.packageName}")

                    // Only process if any of the content is blocked
                    if (shouldBlockContent()) {
                        processEventInBackground(
                            packageName = eventPackageName,
                            node = it,
                            wellBeing = wellbeing.copy()
                        )
                    }
                }
            }

        } catch (ignored: Exception) {
        }
    }

    /**
     * Processes accessibility event in background thread instead of main thread.
     *
     * @param packageName The package name of the app generating the event.
     * @param node        The accessibility node representing the UI element currently in focus.
     */
    private fun processEventInBackground(
        packageName: String,
        node: AccessibilityNodeInfo,
        wellBeing: Wellbeing,
    ) {
        try {
            when (packageName) {
                in devicePlatformPackages ->
                    deviceFeaturesManager.blockFeatures(packageName, node, wellBeing)

                in shortsPlatformPackages ->
                    shortsPlatformManager.blockDistraction(packageName, node, wellBeing)

                in browserPackages ->
                    browserManager.blockDistraction(packageName, node, wellBeing)
            }

        } catch (e: Exception) {
            Log.e(
                TAG,
                "processEventInBackground: Failed to process accessibility event in background",
                e
            )
            SharedPrefsHelper.insertCrashLogToPrefs(this, e)
        }
    }

    @WorkerThread
    private fun onNewAppLaunch(packageName: String?) {
        try {
            browserManager.stopTracking()
            reminderManager.cancelReminders()
            overlayManager.dismissSheetOverlay()

            if (packageName == null) {
                continuousUsageManager.stopTracking()
                return
            }

            val restriction = restrictionManager.getAppRestriction(packageName)
            if (restriction != null) {
                continuousUsageManager.startTracking(packageName, restriction)
            } else {
                continuousUsageManager.stopTracking()
            }

            /// check current restrictions
            val currentOrFutureState = restrictionManager.isAppRestricted(packageName)
            Log.d(TAG, "onNewAppLaunch: $packageName's evaluated state => $currentOrFutureState")

            currentOrFutureState?.let {
                /// Already restricted
                if (it.timeLeftMillis <= 0L) {
                    if (it.type == RestrictionType.CONTINUOUS_USAGE) {
                        restriction?.let { restriction ->
                            continuousUsageManager.startBreak(packageName, restriction)
                        }
                    }
                    overlayManager.showSheetOverlay(
                        packageName = packageName,
                        restrictionState = it,
                    )
                }
                /// Under limit but will be exhausted in some time
                else {
                    reminderManager.scheduleReminders(
                        packageName = packageName,
                        state = it,
                    )
                }
            }
        } catch (e: Exception) {
            SharedPrefsHelper.insertCrashLogToPrefs(this, e)
            Log.e(TAG, "onNewAppLaunch: Failed to process new app launch event", e)
        }
    }

    private fun loadRestrictionsFromPrefs() {
        try {
            // Load App Restrictions
            val appRestrictionsJson = SharedPrefsHelper.getSetAppRestrictions(this, null)
            val appRestrictions = JsonUtils.parseAppRestrictionsMap(appRestrictionsJson)

            // Load Groups
            val groupsJson = SharedPrefsHelper.getSetRestrictionGroups(this, null)
            val groups = JsonUtils.parseRestrictionGroupsMap(groupsJson)

            restrictionManager.updateRestrictions(appRestrictions, groups)

            // Load Focused Apps
            val focusedApps = SharedPrefsHelper.getSetFocusedApps(this, null)
            restrictionManager.updateFocusedApps(focusedApps)

            // Load Bedtime Apps
            val bedtimeApps = SharedPrefsHelper.getSetBedtimeApps(this, null)
            restrictionManager.updateBedtimeApps(bedtimeApps)

            // Re-evaluate current app
            onNewAppLaunch(trackingManager.getLastActiveApp)
            
            Log.d(TAG, "loadRestrictionsFromPrefs: Restrictions loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "loadRestrictionsFromPrefs: Failed to load restrictions", e)
        }
    }


    /**
     * Determines whether content should be blocked based on the current settings.
     *
     * @return `true` if content should be blocked based on the current settings,
     * `false` otherwise.
     */
    private fun shouldBlockContent(): Boolean {
        return wellbeing.blockedFeatures.isNotEmpty() ||
                wellbeing.blockedWebsites.isNotEmpty() ||
                wellbeing.nsfwWebsites.isNotEmpty() ||
                wellbeing.blockNsfwSites ||
                wellbeing.websiteTimeLimits.isNotEmpty()
    }


    /**
     * Performs the back action and shows a toast message indicating that the content is blocked.
     */
    private fun goBackWithToast(customAction: Int? = null) {
        throttler.submit {
            ThreadUtils.runOnMainThread {
                // Perform the back action (can be done on background thread)
                performGlobalAction(customAction ?: GLOBAL_ACTION_BACK)

                // Post Toast to main thread
                Toast.makeText(
                    this@MindfulAccessibilityService,
                    getString(R.string.toast_blocked_content),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Updates the service info with the latest settings and registered packages.
     */
    private fun refreshServiceConfig() {
        try {
            // Using hashset to avoid duplicates
            browserPackages.clear()
            devicePlatformPackages.clear()
            shortsPlatformPackages.clear()
            val pm = packageManager

            // Check admin and add settings to blocked packages
            if (PermissionsHelper.getAndAskAdminPermission(this, false)) {
                devicePlatformPackages.add(SETTINGS_PACKAGE)
            }

            // Fetch installed browser packages
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"))
            pm.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL).forEach {
                browserPackages.add(it.activityInfo.packageName)
            }

            wellbeing.blockedFeatures.forEach { feature ->
                when (feature) {
                    /// Instagram
                    PlatformFeatures.INSTAGRAM_REELS,
                    PlatformFeatures.INSTAGRAM_EXPLORE,
                        -> shortsPlatformPackages.add(INSTAGRAM_PACKAGE)

                    // Snapchat
                    PlatformFeatures.SNAPCHAT_SPOTLIGHT,
                    PlatformFeatures.SNAPCHAT_DISCOVER,
                        -> shortsPlatformPackages.add(SNAPCHAT_PACKAGE)

                    // Facebook
                    PlatformFeatures.FACEBOOK_REELS ->
                        shortsPlatformPackages.add(FACEBOOK_PACKAGE)

                    // Reddit
                    PlatformFeatures.REDDIT_SHORTS ->
                        shortsPlatformPackages.add(REDDIT_PACKAGE)

                    // Youtube
                    PlatformFeatures.YOUTUBE_SHORTS -> {
                        // Add official package
                        shortsPlatformPackages.add(YOUTUBE_PACKAGE)

                        // Now add other unofficial clients
                        val ytIntent =
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
                        pm.queryIntentActivities(ytIntent, PackageManager.MATCH_ALL)
                            .filterNot { browserPackages.contains(it.activityInfo.packageName) }
                            .forEach {
                                shortsPlatformPackages.add(it.activityInfo.packageName)
                            }
                    }
                }
            }


            // Load nsfw website domains if needed
            if (wellbeing.blockNsfwSites) BrowserManager.initializeNsfwDomains()
            else BrowserManager.clearNsfwDomains()

            Log.d(
                TAG, "refreshServiceConfig: Accessibility service config updated successfully: " +
                        "\n settings: $wellbeing" +
                        "\n device platforms: $devicePlatformPackages" +
                        "\n short platforms: $shortsPlatformPackages" +
                        "\n browsers: $browserPackages"
            )
        } catch (e: Exception) {
            Log.e(TAG, "refreshServiceInfo: Failed to refresh service info", e)
            SharedPrefsHelper.insertCrashLogToPrefs(this, e)
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, changedKey: String?) {
        changedKey?.let { key ->
            when (key) {
                SharedPrefsHelper.PREF_KEY_WELLBEING_SETTINGS -> {
                    Log.d(TAG, "OnSharedPrefsChanged: Key changed = $changedKey")
                    wellbeing = SharedPrefsHelper.getSetWellBeingSettings(this, null)
                    refreshServiceConfig()
                }
                SharedPrefsHelper.PREF_KEY_APP_RESTRICTIONS,
                SharedPrefsHelper.PREF_KEY_RESTRICTION_GROUPS,
                SharedPrefsHelper.PREF_KEY_FOCUSED_APPS,
                SharedPrefsHelper.PREF_KEY_BEDTIME_APPS -> {
                    Log.d(TAG, "OnSharedPrefsChanged: Restrictions changed = $changedKey")
                    loadRestrictionsFromPrefs()
                }
            }
        }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        try {
            executorService.shutdownNow()
            trackingManager.startManualTracking()
            reminderManager.cancelReminders()
            overlayManager.dismissSheetOverlay()

            // Unregister prefs listener and receiver
            deviceAppsChangedReceiver.unRegister(this)
            SharedPrefsHelper.registerUnregisterListenerToListenablePrefs(this, false, this)
        } catch (e: Exception) {
            // ignored
        }

        Log.d(TAG, "onDestroy: Accessibility service destroyed")
        super.onDestroy()
    }
}
