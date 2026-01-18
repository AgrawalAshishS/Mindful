package com.mindful.android.services.tracking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mindful.android.enums.ReminderType
import com.mindful.android.enums.RestrictionType
import com.mindful.android.helpers.storage.UsageDatabaseHelper
import com.mindful.android.models.AppRestriction
import com.mindful.android.models.RestrictionGroup
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for RestrictionManager to verify restriction evaluation logic
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RestrictionManagerTest {

    private lateinit var context: Context
    private lateinit var restrictionManager: RestrictionManager
    private var stopIfNoUsageCalled = false

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // Mock UsageDatabaseHelper singleton
        mockkObject(UsageDatabaseHelper)
        val mockDbHelper = mockk<UsageDatabaseHelper>(relaxed = true)
        every { UsageDatabaseHelper.getInstance(any()) } returns mockDbHelper

        // Mock ContinuousUsageManager
        val mockContinuousUsageManager = mockk<ContinuousUsageManager>(relaxed = true)

        // Initialize RestrictionManager
        restrictionManager = RestrictionManager(
            context = context,
            stopIfNoUsage = { stopIfNoUsageCalled = true },
            continuousUsageManager = mockContinuousUsageManager
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Test Case 1: Focus Mode - Blocks Focused Apps
     */
    @Test
    fun testFocusMode_BlocksFocusedApps() {
        val instagram = "com.instagram.android"
        val calculator = "com.android.calculator2"

        // Enable focus mode for Instagram
        restrictionManager.updateFocusedApps(setOf(instagram))

        // Try to launch Instagram (in focus list, should be blocked)
        val instagramState = restrictionManager.isAppRestricted(instagram)
        assert(instagramState != null)
        assert(instagramState?.type == RestrictionType.FOCUS)

        // Try to launch Calculator (not in focus list)
        val calcState = restrictionManager.isAppRestricted(calculator)
        assert(calcState == null) // Not restricted
    }

    /**
     * Test Case 2: Bedtime Mode - Blocks Bedtime Apps
     */
    @Test
    fun testBedtimeMode_BlocksBedtimeApps() {
        val facebook = "com.facebook.katana"
        val calculator = "com.android.calculator2"

        // Enable bedtime mode for Facebook
        restrictionManager.updateBedtimeApps(setOf(facebook))

        // Try to launch Facebook (should be blocked)
        val facebookState = restrictionManager.isAppRestricted(facebook)
        assert(facebookState != null)
        assert(facebookState?.type == RestrictionType.BEDTIME)

        // Try to launch Calculator (not in bedtime list)
        val calcState = restrictionManager.isAppRestricted(calculator)
        assert(calcState == null) // Not restricted
    }

    /**
     * Test Case 3: Launch Limit - Blocks After Exceeding Count
     */
    @Test
    fun testLaunchLimit_BlocksAfterExceedingCount() {
        val instagram = "com.instagram.android"

        val restriction = AppRestriction(
            appPackage = instagram,
            launchLimit = 3
        )

        val restrictionsMap = hashMapOf(instagram to restriction)
        restrictionManager.updateRestrictions(restrictionsMap, null)

        // Launch 1st time - allowed
        val state1 = restrictionManager.isAppRestricted(instagram)
        assert(state1 == null)

        // Launch 2nd time - allowed
        val state2 = restrictionManager.isAppRestricted(instagram)
        assert(state2 == null)

        // Launch 3rd time - allowed
        val state3 = restrictionManager.isAppRestricted(instagram)
        assert(state3 == null)

        // Launch 4th time - blocked (exceeds limit of 3)
        val state4 = restrictionManager.isAppRestricted(instagram)
        assert(state4 != null)
        assert(state4?.type == RestrictionType.LAUNCH_COUNT)
    }

    /**
     * Test Case 4: Cache Invalidation - Refreshes On Change
     */
    @Test
    fun testCacheInvalidation_RefreshesOnChange() {
        val instagram = "com.instagram.android"

        // Set bedtime restriction
        restrictionManager.updateBedtimeApps(setOf(instagram))

        // Verify blocked
        val state1 = restrictionManager.isAppRestricted(instagram)
        assert(state1 != null)
        assert(state1?.type == RestrictionType.BEDTIME)

        // Remove bedtime restriction
        restrictionManager.updateBedtimeApps(null)

        // Verify no longer blocked
        val state2 = restrictionManager.isAppRestricted(instagram)
        assert(state2 == null)
    }

    /**
     * Test Case 5: Midnight Reset - Clears Cache
     */
    @Test
    fun testMidnightReset_ClearsCacheAndRecalculates() {
        val instagram = "com.instagram.android"

        // Set launch limit
        val restriction = AppRestriction(
            appPackage = instagram,
            launchLimit = 2
        )
        val restrictionsMap = hashMapOf(instagram to restriction)
        restrictionManager.updateRestrictions(restrictionsMap, null)

        // Launch twice (reaches limit)
        restrictionManager.isAppRestricted(instagram) // 1st
        restrictionManager.isAppRestricted(instagram) // 2nd

        // Verify launch count is tracked
        assert(restrictionManager.getAppsLaunchCount[instagram] == 2)

        // Midnight reset
        restrictionManager.resetCache()

        // Verify cache cleared
        assert(restrictionManager.getAppsLaunchCount.isEmpty())

        // Launch again (should work, count restarted)
        val state = restrictionManager.isAppRestricted(instagram)
        assert(state == null) // Allowed again (count is 1)
    }

    /**
     * Test Case 6: No Restrictions - App Allowed
     */
    @Test
    fun testNoRestrictions_AppAllowed() {
        val calculator = "com.android.calculator2"

        // Try to launch Calculator (no restrictions)
        val state = restrictionManager.isAppRestricted(calculator)
        assert(state == null) // Not restricted
    }

    /**
     * Test Case 7: IsIdle Property
     */
    @Test
    fun testIsIdle_ReturnsCorrectState() {
        // Initially idle
        assert(restrictionManager.isIdle)

        // Add restriction
        val restriction = AppRestriction(appPackage = "com.test.app", timerSec = 3600)
        restrictionManager.updateRestrictions(hashMapOf("com.test.app" to restriction), null)

        // No longer idle
        assert(!restrictionManager.isIdle)

        // Clear restrictions
        restrictionManager.updateRestrictions(hashMapOf(), null)

        // Idle again
        assert(restrictionManager.isIdle)
    }

    /**
     * Test Case 8: Restriction Priority - Focus Overrides Others
     */
    @Test
    fun testRestrictionPriority_FocusOverridesOthers() {
        val instagram = "com.instagram.android"

        // Set timer restriction
        val restriction = AppRestriction(
            appPackage = instagram,
            timerSec = 3600
        )
        val restrictionsMap = hashMapOf(instagram to restriction)
        restrictionManager.updateRestrictions(restrictionsMap, null)

        // Also enable focus mode for same app
        restrictionManager.updateFocusedApps(setOf(instagram))

        // Try to launch Instagram
        val state = restrictionManager.isAppRestricted(instagram)
        assert(state != null)
        assert(state?.type == RestrictionType.FOCUS) // Focus takes priority
    }

    /**
     * Test Case 9: Multiple Apps with Different Restrictions
     */
    @Test
    fun testMultipleAppsWithDifferentRestrictions() {
        val instagram = "com.instagram.android"
        val twitter = "com.twitter.android"
        val facebook = "com.facebook.katana"

        // Instagram: Focus mode
        restrictionManager.updateFocusedApps(setOf(instagram))

        // Twitter: Bedtime mode
        restrictionManager.updateBedtimeApps(setOf(twitter))

        // Facebook: Launch limit
        val facebookRestriction = AppRestriction(
            appPackage = facebook,
            launchLimit = 1
        )
        restrictionManager.updateRestrictions(hashMapOf(facebook to facebookRestriction), null)

        // Test Instagram - Focus
        val instagramState = restrictionManager.isAppRestricted(instagram)
        assert(instagramState?.type == RestrictionType.FOCUS)

        // Test Twitter - Bedtime
        val twitterState = restrictionManager.isAppRestricted(twitter)
        assert(twitterState?.type == RestrictionType.BEDTIME)

        // Test Facebook - Launch (first launch allowed)
        val facebookState1 = restrictionManager.isAppRestricted(facebook)
        assert(facebookState1 == null) // First launch OK

        // Test Facebook - Launch (second launch blocked)
        val facebookState2 = restrictionManager.isAppRestricted(facebook)
        assert(facebookState2?.type == RestrictionType.LAUNCH_COUNT)
    }

    /**
     * Test Case 10: StopIfNoUsage Callback
     */
    @Test
    fun testStopIfNoUsage_CallbackTriggered() {
        val instagram = "com.instagram.android"

        // Set bedtime
        restrictionManager.updateBedtimeApps(setOf(instagram))
        stopIfNoUsageCalled = false

        // Clear bedtime (triggers callback)
        restrictionManager.updateBedtimeApps(null)

        // Verify callback was triggered
        assert(stopIfNoUsageCalled)
    }

    /**
     * Test Case 11: Update Restrictions Clears App Cache
     */
    @Test
    fun testUpdateRestrictions_ClearsAppCache() {
        val instagram = "com.instagram.android"

        // Set initial restriction and block it
        restrictionManager.updateFocusedApps(setOf(instagram))
        restrictionManager.isAppRestricted(instagram) // Caches the block

        // Update with timer restriction
        val restriction = AppRestriction(appPackage = instagram, timerSec = 3600)
        restrictionManager.updateRestrictions(hashMapOf(instagram to restriction), null)

        // Focus should be cleared, so no longer blocked by focus
        restrictionManager.updateFocusedApps(emptySet())

        val state = restrictionManager.isAppRestricted(instagram)
        // Should not be focus anymore (cache was cleared)
        assert(state == null || state.type != RestrictionType.FOCUS)
    }

    /**
     * Test Case 12: Get App Restriction
     */
    @Test
    fun testGetAppRestriction_ReturnsCorrectRestriction() {
        val instagram = "com.instagram.android"
        val timerSec = 3600

        val restriction = AppRestriction(
            appPackage = instagram,
            timerSec = timerSec
        )
        restrictionManager.updateRestrictions(hashMapOf(instagram to restriction), null)

        // Get restriction
        val retrieved = restrictionManager.getAppRestriction(instagram)
        assert(retrieved != null)
        assert(retrieved?.appPackage == instagram)
        assert(retrieved?.timerSec == timerSec)

        // Get non-existent restriction
        val nonExistent = restrictionManager.getAppRestriction("com.nonexistent.app")
        assert(nonExistent == null)
    }
}
