package com.mindful.android.services.accessibility

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import com.mindful.android.AppConstants.SYSTEM_UI_PACKAGE
import com.mindful.android.helpers.storage.UsageDatabaseHelper
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for TrackingManager to verify app session tracking logic
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TrackingManagerTest {

    private lateinit var context: Context
    private lateinit var trackingManager: TrackingManager
    private var lastLaunchedApp: String? = null
    private val launchedApps = mutableListOf<String>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // Mock the singleton UsageDatabaseHelper
        mockkObject(UsageDatabaseHelper)
        val mockDbHelper = mockk<UsageDatabaseHelper>(relaxed = true)
        every { UsageDatabaseHelper.getInstance(any()) } returns mockDbHelper

        // Initialize TrackingManager with callback
        trackingManager = TrackingManager(context) { packageName ->
            lastLaunchedApp = packageName
            launchedApps.add(packageName)
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Test Case 1: App Switch - Session Ends and Starts
     */
    @Test
    fun testAppSwitch_SessionEndsAndStarts() {
        val appA = "com.example.appA"
        val appB = "com.example.appB"

        // Launch App A
        trackingManager.onNewEvent(appA)
        assert(trackingManager.getLastActiveApp == appA)
        assert(lastLaunchedApp == appA)

        // Launch App B
        trackingManager.onNewEvent(appB)

        // Verify App B is now active
        assert(trackingManager.getLastActiveApp == appB)
        assert(lastLaunchedApp == appB)

        // Verify both apps were launched
        assert(launchedApps.contains(appA))
        assert(launchedApps.contains(appB))
    }

    /**
     * Test Case 2: System UI Handling - No New Session
     */
    @Test
    fun testSystemUI_NoNewSession() {
        val appA = "com.example.appA"

        // Launch App A
        trackingManager.onNewEvent(appA)
        assert(trackingManager.getLastActiveApp == appA)

        // Open Recents (System UI)
        trackingManager.onNewEvent(SYSTEM_UI_PACKAGE)

        // Verify no app is active
        assert(trackingManager.getLastActiveApp.isEmpty())

        // Verify only App A was in launched list (not system UI)
        assert(launchedApps.size == 1)
        assert(launchedApps[0] == appA)
    }

    /**
     * Test Case 3: Pause/Resume - Session Handling
     */
    @Test
    fun testPauseResume_SessionHandling() {
        val appA = "com.example.appA"
        val appB = "com.example.appB"

        // Launch App A
        trackingManager.onNewEvent(appA)
        assert(trackingManager.getLastActiveApp == appA)

        // Pause tracking
        trackingManager.pauseTracking()

        // Try to launch App B (should be ignored)
        trackingManager.onNewEvent(appB)

        // App A should still be the last active
        assert(trackingManager.getLastActiveApp == appA)
        assert(!launchedApps.contains(appB))

        // Resume tracking
        trackingManager.resumeTracking()

        // Now launch App B (should work)
        trackingManager.onNewEvent(appB)
        assert(trackingManager.getLastActiveApp == appB)
        assert(launchedApps.contains(appB))
    }

    /**
     * Test Case 4: Manual Tracking Stop - Closes Open Session
     */
    @Test
    fun testStartManualTracking_ClosesOpenSession() {
        val appA = "com.example.appA"

        // Launch App A
        trackingManager.onNewEvent(appA)
        assert(trackingManager.getLastActiveApp == appA)

        // Accessibility service stops
        trackingManager.startManualTracking()

        // Verify no app is active
        assert(trackingManager.getLastActiveApp.isEmpty())
    }

    /**
     * Test Case 5: Manual Tracking Start - Resets State
     */
    @Test
    fun testStopManualTracking_ResetsState() {
        val appA = "com.example.appA"

        // Launch App A
        trackingManager.onNewEvent(appA)
        assert(trackingManager.getLastActiveApp == appA)

        // Accessibility service stops then starts
        trackingManager.startManualTracking()
        trackingManager.stopManualTracking()

        // Verify state is reset
        assert(trackingManager.getLastActiveApp.isEmpty())

        // Launch new app should work
        trackingManager.onNewEvent(appA)
        assert(trackingManager.getLastActiveApp == appA)
    }

    /**
     * Test Case 6: Same App Event - No Duplicate Session
     */
    @Test
    fun testSameAppEvent_NoDuplicateSession() {
        val appA = "com.example.appA"

        // Launch App A
        trackingManager.onNewEvent(appA)
        assert(trackingManager.getLastActiveApp == appA)
        val launchCount = launchedApps.size

        // Receive App A event again (e.g., window state change)
        trackingManager.onNewEvent(appA)

        // Should not trigger callback again
        assert(launchedApps.size == launchCount)

        // Still on App A
        assert(trackingManager.getLastActiveApp == appA)
    }

    /**
     * Test Case 7: Multiple Sequential Apps
     */
    @Test
    fun testMultipleSequentialApps() {
        val apps = listOf("com.app1", "com.app2", "com.app3", "com.app4")

        apps.forEach { app ->
            trackingManager.onNewEvent(app)
            assert(trackingManager.getLastActiveApp == app)
        }

        // Verify all apps were launched
        assert(launchedApps.size == apps.size)
        apps.forEach { app ->
            assert(launchedApps.contains(app))
        }
    }

    /**
     * Test Case 8: System UI Between Apps
     */
    @Test
    fun testSystemUIBetweenApps() {
        val appA = "com.example.appA"
        val appB = "com.example.appB"

        // App A
        trackingManager.onNewEvent(appA)
        assert(trackingManager.getLastActiveApp == appA)

        // System UI
        trackingManager.onNewEvent(SYSTEM_UI_PACKAGE)
        assert(trackingManager.getLastActiveApp.isEmpty())

        // App B
        trackingManager.onNewEvent(appB)
        assert(trackingManager.getLastActiveApp == appB)

        // Should have launched A and B, but not system UI
        assert(launchedApps.size == 2)
        assert(launchedApps[0] == appA)
        assert(launchedApps[1] == appB)
    }
}
