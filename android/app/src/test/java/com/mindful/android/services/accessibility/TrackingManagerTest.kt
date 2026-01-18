package com.mindful.android.services.accessibility

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.mindful.android.AppConstants.SYSTEM_UI_PACKAGE
import com.mindful.android.helpers.storage.UsageDatabaseHelper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for TrackingManager to verify app session tracking logic
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TrackingManagerTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var packageManager: PackageManager

    @Mock
    private lateinit var dbHelper: UsageDatabaseHelper

    private lateinit var trackingManager: TrackingManager
    private var lastLaunchedApp: String? = null

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        // Mock context and package manager
        `when`(context.packageManager).thenReturn(packageManager)
        `when`(context.packageName).thenReturn("com.mindful.android")

        // Mock launcher detection
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.launcher3"
            }
        }
        `when`(packageManager.resolveActivity(any(Intent::class.java), anyInt()))
            .thenReturn(resolveInfo)

        // Mock UsageDatabaseHelper singleton
        mockStatic(UsageDatabaseHelper::class.java).use { utilities ->
            utilities.`when`<UsageDatabaseHelper> { UsageDatabaseHelper.getInstance(context) }
                .thenReturn(dbHelper)
        }

        // Initialize TrackingManager with callback
        trackingManager = TrackingManager(context) { packageName ->
            lastLaunchedApp = packageName
        }
    }

    /**
     * Test Case 1: App Switch - Session Ends and Starts
     * Scenario: Launch App A → Launch App B
     * Expected: Session A saved with correct duration, Session B started
     */
    @Test
    fun testAppSwitch_SessionEndsAndStarts() {
        val appA = "com.example.appA"
        val appB = "com.example.appB"
        val startTime = System.currentTimeMillis()

        // Launch App A
        trackingManager.onNewEvent(appA)
        assert(trackingManager.getLastActiveApp == appA)
        assert(lastLaunchedApp == appA)

        // Wait a bit (simulating usage)
        Thread.sleep(100)

        // Launch App B
        trackingManager.onNewEvent(appB)

        // Verify App A session was saved
        verify(dbHelper, times(1)).insertUsageSession(
            eq(appA),
            anyLong(),
            anyLong()
        )

        // Verify App B is now active
        assert(trackingManager.getLastActiveApp == appB)
        assert(lastLaunchedApp == appB)
    }

    /**
     * Test Case 2: Home Press - Session Ends
     * Scenario: Launch App A → Press Home
     * Expected: Session A saved, no new session started
     */
    @Test
    fun testHomePress_SessionEnds() {
        val appA = "com.example.appA"
        val launcher = "com.android.launcher3"

        // Launch App A
        trackingManager.onNewEvent(appA)
        assert(trackingManager.getLastActiveApp == appA)

        // Wait a bit
        Thread.sleep(100)

        // Go to home
        trackingManager.onNewEvent(launcher)

        // Verify App A session was saved
        verify(dbHelper, times(1)).insertUsageSession(
            eq(appA),
            anyLong(),
            anyLong()
        )

        // Verify no app is active now
        assert(trackingManager.getLastActiveApp.isEmpty())
    }

    /**
     * Test Case 3: System UI Handling - No New Session
     * Scenario: Launch App A → Open Recents (System UI) → Launch App B
     * Expected: Session A ends, Session B starts, no system UI session
     */
    @Test
    fun testSystemUI_NoNewSession() {
        val appA = "com.example.appA"
        val appB = "com.example.appB"

        // Launch App A
        trackingManager.onNewEvent(appA)
        assert(trackingManager.getLastActiveApp == appA)

        Thread.sleep(100)

        // Open Recents (System UI)
        trackingManager.onNewEvent(SYSTEM_UI_PACKAGE)

        // Verify App A session was saved
        verify(dbHelper, times(1)).insertUsageSession(
            eq(appA),
            anyLong(),
            anyLong()
        )

        // Verify no app is active
        assert(trackingManager.getLastActiveApp.isEmpty())

        // Launch App B
        trackingManager.onNewEvent(appB)

        // Verify App B is active (no System UI session created)
        assert(trackingManager.getLastActiveApp == appB)

        // Verify only 1 session saved (App A), not System UI
        verify(dbHelper, times(1)).insertUsageSession(anyString(), anyLong(), anyLong())
    }

    /**
     * Test Case 4: Launcher Optimization - Ignored When Idle
     * Scenario: Press Home → Press Home again
     * Expected: Second event ignored (optimization)
     */
    @Test
    fun testLauncherIgnored_WhenIdle() {
        val launcher = "com.android.launcher3"

        // Already idle (no app active)
        assert(trackingManager.getLastActiveApp.isEmpty())

        // Press home (redundant)
        trackingManager.onNewEvent(launcher)

        // Should not create any session or trigger callback
        verify(dbHelper, never()).insertUsageSession(anyString(), anyLong(), anyLong())
        assert(trackingManager.getLastActiveApp.isEmpty())

        // Press home again (still redundant)
        trackingManager.onNewEvent(launcher)

        // Still no session created
        verify(dbHelper, never()).insertUsageSession(anyString(), anyLong(), anyLong())
    }

    /**
     * Test Case 5: Pause/Resume - Session Handling
     * Scenario: Launch App A → Pause tracking → Wait → Resume
     * Expected: Events ignored during pause
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

        Thread.sleep(100)

        // Try to launch App B (should be ignored)
        trackingManager.onNewEvent(appB)

        // App A should still be the last active (no session saved yet)
        assert(trackingManager.getLastActiveApp == appA)
        verify(dbHelper, never()).insertUsageSession(anyString(), anyLong(), anyLong())

        // Resume tracking
        trackingManager.resumeTracking()

        // Now launch App B (should work)
        trackingManager.onNewEvent(appB)

        // Verify App A session was saved
        verify(dbHelper, times(1)).insertUsageSession(eq(appA), anyLong(), anyLong())
        assert(trackingManager.getLastActiveApp == appB)
    }

    /**
     * Test Case 6: Manual Tracking Stop - Closes Open Session
     * Scenario: Launch App A → Accessibility service stops
     * Expected: Session closed, broadcast sent
     */
    @Test
    fun testStartManualTracking_ClosesOpenSession() {
        val appA = "com.example.appA"

        // Launch App A
        trackingManager.onNewEvent(appA)
        assert(trackingManager.getLastActiveApp == appA)

        Thread.sleep(100)

        // Accessibility service stops
        trackingManager.startManualTracking()

        // Verify session was saved
        verify(dbHelper, times(1)).insertUsageSession(eq(appA), anyLong(), anyLong())

        // Verify no app is active
        assert(trackingManager.getLastActiveApp.isEmpty())
    }

    /**
     * Test Case 7: Manual Tracking Start - Resets State
     * Scenario: Accessibility service starts
     * Expected: State reset, ready for new sessions
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
     * Test Case 8: Same App Event - No Duplicate Session
     * Scenario: Launch App A → Receive App A event again
     * Expected: No new session created, no save operation
     */
    @Test
    fun testSameAppEvent_NoDuplicateSession() {
        val appA = "com.example.appA"

        // Launch App A
        trackingManager.onNewEvent(appA)
        assert(trackingManager.getLastActiveApp == appA)

        // Receive App A event again (e.g., window state change)
        trackingManager.onNewEvent(appA)

        // Should not save any session
        verify(dbHelper, never()).insertUsageSession(anyString(), anyLong(), anyLong())

        // Still on App A
        assert(trackingManager.getLastActiveApp == appA)
    }
}
