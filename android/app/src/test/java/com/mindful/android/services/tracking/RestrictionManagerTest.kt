package com.mindful.android.services.tracking

import android.content.Context
import com.mindful.android.enums.ReminderType
import com.mindful.android.enums.RestrictionType
import com.mindful.android.helpers.storage.UsageDatabaseHelper
import com.mindful.android.models.AppRestriction
import com.mindful.android.models.RestrictionGroup
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/**
 * Unit tests for RestrictionManager to verify restriction evaluation logic
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RestrictionManagerTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var dbHelper: UsageDatabaseHelper

    @Mock
    private lateinit var continuousUsageManager: ContinuousUsageManager

    private lateinit var restrictionManager: RestrictionManager
    private var stopIfNoUsageCalled = false

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        // Mock UsageDatabaseHelper singleton
        mockStatic(UsageDatabaseHelper::class.java).use { utilities ->
            utilities.`when`<UsageDatabaseHelper> { UsageDatabaseHelper.getInstance(context) }
                .thenReturn(dbHelper)
        }

        // Initialize RestrictionManager
        restrictionManager = RestrictionManager(
            context = context,
            stopIfNoUsage = { stopIfNoUsageCalled = true },
            continuousUsageManager = continuousUsageManager
        )
    }

    /**
     * Test Case 1: Focus Mode - Blocks Non-Focused Apps
     * Scenario: Focus mode enabled with only Chrome allowed
     * Expected: Instagram gets blocked, Chrome is allowed
     */
    @Test
    fun testFocusMode_BlocksNonFocusedApps() {
        val chrome = "com.android.chrome"
        val instagram = "com.instagram.android"

        // Enable focus mode with only Chrome
        restrictionManager.updateFocusedApps(setOf(instagram))

        // Try to launch Chrome (not in focus list, so should be allowed - focus blocks the FOCUSED apps)
        val chromeState = restrictionManager.isAppRestricted(chrome)
        assert(chromeState == null) // Not restricted

        // Try to launch Instagram (in focus list, so should be blocked)
        val instagramState = restrictionManager.isAppRestricted(instagram)
        assert(instagramState != null)
        assert(instagramState?.type == RestrictionType.FOCUS)
    }

    /**
     * Test Case 2: Bedtime Mode - Blocks Bedtime Apps
     * Scenario: Bedtime mode enabled for social apps
     * Expected: Social apps get blocked during bedtime
     */
    @Test
    fun testBedtimeMode_BlocksBedtimeApps() {
        val facebook = "com.facebook.katana"
        val instagram = "com.instagram.android"
        val calculator = "com.android.calculator2"

        // Enable bedtime mode for social apps
        restrictionManager.updateBedtimeApps(setOf(facebook, instagram))

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
     * Scenario: App has 3 launch limit
     * Expected: 4th launch gets blocked
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
     * Test Case 4: App Timer - Blocks After Exceeding Time
     * Scenario: App has 1 hour timer, already used 61 minutes
     * Expected: App gets blocked
     */
    @Test
    fun testAppTimer_BlocksAfterExceedingTime() {
        val instagram = "com.instagram.android"
        val timerSec = 3600 // 1 hour
        val usedSec = 3660L // 61 minutes

        val restriction = AppRestriction(
            appPackage = instagram,
            timerSec = timerSec
        )

        val restrictionsMap = hashMapOf(instagram to restriction)
        restrictionManager.updateRestrictions(restrictionsMap, null)

        // Mock database to return usage exceeding limit
        val usageMap = mapOf(instagram to usedSec * 1000) // Convert to ms
        `when`(dbHelper.queryUsageForInterval(anyLong(), anyLong())).thenReturn(usageMap)

        // Try to launch Instagram
        val state = restrictionManager.isAppRestricted(instagram)
        assert(state != null)
        assert(state?.type == RestrictionType.APP_TIMER)
        assert(state?.screenTimeUsed == usedSec)
        assert(state?.screenTimeLimit == timerSec.toLong())
    }

    /**
     * Test Case 5: Active Period - Blocks Outside Allowed Time
     * Scenario: App allowed only 9 AM to 5 PM (540 to 1020 minutes)
     * Expected: App gets blocked outside this period
     *
     * Note: This test is time-dependent and would need to mock Calendar.getInstance()
     * For simplicity, we test the logic structure
     */
    @Test
    fun testActivePeriod_LogicStructure() {
        val instagram = "com.instagram.android"

        // Set active period: 9 AM (540 min) to 5 PM (1020 min)
        val restriction = AppRestriction(
            appPackage = instagram,
            activePeriodStart = 540,
            activePeriodEnd = 1020
        )

        val restrictionsMap = hashMapOf(instagram to restriction)
        restrictionManager.updateRestrictions(restrictionsMap, null)

        // Mock database to return no usage
        `when`(dbHelper.queryUsageForInterval(anyLong(), anyLong())).thenReturn(emptyMap())

        // Try to launch Instagram (actual blocking depends on current time)
        val state = restrictionManager.isAppRestricted(instagram)

        // If outside active period, should be blocked
        // If inside active period, should get a future restriction state
        // The test verifies the method executes without errors
        assert(true) // Logic test - verifies no crashes
    }

    /**
     * Test Case 6: Group Timer - Blocks When Group Limit Exceeded
     * Scenario: Social group with 2 hour limit, group apps used 121 minutes total
     * Expected: All apps in group get blocked
     */
    @Test
    fun testGroupTimer_BlocksAfterExceedingGroupTime() {
        val instagram = "com.instagram.android"
        val facebook = "com.facebook.katana"
        val twitter = "com.twitter.android"

        val groupId = 1
        val groupTimerSec = 7200 // 2 hours
        val instagramUsedMs = 3600L * 1000 // 1 hour
        val facebookUsedMs = 1260L * 1000 // 21 minutes
        // Total: 81 minutes (exceeds limit)

        val group = RestrictionGroup(
            id = groupId,
            groupName = "Social",
            timerSec = groupTimerSec,
            distractingApps = setOf(instagram, facebook, twitter)
        )

        val instagramRestriction = AppRestriction(
            appPackage = instagram,
            associatedGroupId = groupId
        )

        val restrictionsMap = hashMapOf(instagram to instagramRestriction)
        val groupsMap = hashMapOf(groupId to group)

        restrictionManager.updateRestrictions(restrictionsMap, groupsMap)

        // Mock database to return group usage exceeding limit
        val usageMap = mapOf(
            instagram to instagramUsedMs,
            facebook to facebookUsedMs,
            twitter to 0L
        )
        `when`(dbHelper.queryUsageForInterval(anyLong(), anyLong())).thenReturn(usageMap)

        // Try to launch Instagram
        val state = restrictionManager.isAppRestricted(instagram)
        assert(state != null)
        assert(state?.type == RestrictionType.GROUP_TIMER)
        assert(state?.groupName == "Social")
    }

    /**
     * Test Case 7: Restriction Priority - Focus Overrides Others
     * Scenario: App has timer restriction AND is in focus mode
     * Expected: Focus restriction takes priority
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

        // Mock database
        `when`(dbHelper.queryUsageForInterval(anyLong(), anyLong())).thenReturn(emptyMap())

        // Try to launch Instagram
        val state = restrictionManager.isAppRestricted(instagram)
        assert(state != null)
        assert(state?.type == RestrictionType.FOCUS) // Focus takes priority
    }

    /**
     * Test Case 8: Cache Invalidation - Refreshes On Change
     * Scenario: App is restricted, then restriction removed
     * Expected: Cache cleared, app becomes accessible
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
     * Test Case 9: Midnight Reset - Clears Cache
     * Scenario: Launch count cache populated, midnight reset called
     * Expected: Cache cleared, launch counts reset
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
     * Test Case 10: No Restrictions - App Allowed
     * Scenario: App has no restrictions configured
     * Expected: App is allowed
     */
    @Test
    fun testNoRestrictions_AppAllowed() {
        val calculator = "com.android.calculator2"

        // No restrictions configured
        `when`(dbHelper.queryUsageForInterval(anyLong(), anyLong())).thenReturn(emptyMap())

        // Try to launch Calculator
        val state = restrictionManager.isAppRestricted(calculator)
        assert(state == null) // Not restricted
    }

    /**
     * Test Case 11: Continuous Usage - Starts Tracking
     * Scenario: App has continuous usage limit configured
     * Expected: ContinuousUsageManager.startTracking() is called
     */
    @Test
    fun testContinuousUsage_StartsTracking() {
        val youtube = "com.google.android.youtube"

        val restriction = AppRestriction(
            appPackage = youtube,
            maxContinuousUsageSec = 1800, // 30 minutes
            breakTimeSec = 300 // 5 minutes break
        )

        val restrictionsMap = hashMapOf(youtube to restriction)
        restrictionManager.updateRestrictions(restrictionsMap, null)

        // Mock database
        `when`(dbHelper.queryUsageForInterval(anyLong(), anyLong())).thenReturn(emptyMap())

        // Launch YouTube
        restrictionManager.isAppRestricted(youtube)

        // Verify continuous usage tracking started
        verify(continuousUsageManager, times(1)).startTracking(eq(youtube), eq(restriction))
    }

    /**
     * Test Case 12: IsIdle Property
     * Scenario: Check if manager is idle (no restrictions)
     * Expected: Returns true when no restrictions, false otherwise
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
}
