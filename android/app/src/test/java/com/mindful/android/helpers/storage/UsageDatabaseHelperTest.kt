package com.mindful.android.helpers.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for UsageDatabaseHelper to verify app usage tracking functionality
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UsageDatabaseHelperTest {

    private lateinit var context: Context
    private lateinit var dbHelper: UsageDatabaseHelper

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dbHelper = UsageDatabaseHelper(context)

        // Clear any existing data
        dbHelper.writableDatabase.execSQL("DELETE FROM ${UsageDatabaseHelper.TABLE_USAGE}")
    }

    /**
     * Test Case 1: Insert and Query Single Session
     * Scenario: Insert one session and query for it
     * Expected: Returns correct usage duration
     */
    @Test
    fun testInsertAndQuery_SingleSession() {
        val packageName = "com.example.app"
        val startTime = 1000L
        val endTime = 5000L
        val expectedDuration = endTime - startTime

        // Insert session
        dbHelper.insertUsageSession(packageName, startTime, endTime)

        // Query usage for the exact interval
        val usage = dbHelper.queryUsageForInterval(startTime, endTime)

        assertEquals(1, usage.size)
        assertEquals(expectedDuration, usage[packageName])
    }

    /**
     * Test Case 2: Insert and Query Multiple Apps
     * Scenario: Insert sessions for multiple apps
     * Expected: Returns usage for each app separately
     */
    @Test
    fun testInsertAndQuery_MultipleApps() {
        val app1 = "com.example.app1"
        val app2 = "com.example.app2"
        val app3 = "com.example.app3"

        // Insert sessions
        dbHelper.insertUsageSession(app1, 1000L, 3000L) // 2 sec
        dbHelper.insertUsageSession(app2, 2000L, 5000L) // 3 sec
        dbHelper.insertUsageSession(app3, 4000L, 10000L) // 6 sec

        // Query all
        val usage = dbHelper.queryUsageForInterval(0L, 20000L)

        assertEquals(3, usage.size)
        assertEquals(2000L, usage[app1])
        assertEquals(3000L, usage[app2])
        assertEquals(6000L, usage[app3])
    }

    /**
     * Test Case 3: Overlapping Sessions - Accumulates Duration
     * Scenario: Same app has multiple sessions
     * Expected: Durations are summed
     */
    @Test
    fun testInsertAndQuery_OverlappingSessions() {
        val packageName = "com.example.app"

        // Insert multiple sessions for same app
        dbHelper.insertUsageSession(packageName, 1000L, 3000L) // 2 sec
        dbHelper.insertUsageSession(packageName, 5000L, 8000L) // 3 sec
        dbHelper.insertUsageSession(packageName, 10000L, 15000L) // 5 sec

        // Query all
        val usage = dbHelper.queryUsageForInterval(0L, 20000L)

        assertEquals(1, usage.size)
        assertEquals(10000L, usage[packageName]) // 2 + 3 + 5 = 10 sec
    }

    /**
     * Test Case 4: Launch Count - Multiple Apps
     * Scenario: Count launches for multiple apps in an interval
     * Expected: Returns correct launch counts
     */
    @Test
    fun testGetLaunchCounts_MultipleApps() {
        val app1 = "com.example.app1"
        val app2 = "com.example.app2"

        // App1 launched 3 times
        dbHelper.insertUsageSession(app1, 1000L, 2000L)
        dbHelper.insertUsageSession(app1, 3000L, 4000L)
        dbHelper.insertUsageSession(app1, 5000L, 6000L)

        // App2 launched 2 times
        dbHelper.insertUsageSession(app2, 2000L, 3000L)
        dbHelper.insertUsageSession(app2, 7000L, 8000L)

        // Query launch counts
        val launchCounts = dbHelper.getAppLaunchCounts(0L, 10000L)

        assertEquals(2, launchCounts.size)
        assertEquals(3, launchCounts[app1])
        assertEquals(2, launchCounts[app2])
    }

    /**
     * Test Case 5: Query With Clipping - Clips to Interval
     * Scenario: Session spans beyond query interval
     * Expected: Duration clipped to interval boundaries
     */
    @Test
    fun testQueryUsageForInterval_ClipsToInterval() {
        val packageName = "com.example.app"

        // Session from 1000 to 10000 (9 sec total)
        dbHelper.insertUsageSession(packageName, 1000L, 10000L)

        // Query only 3000 to 7000 (4 sec within interval)
        val usage = dbHelper.queryUsageForInterval(3000L, 7000L)

        assertEquals(1, usage.size)
        assertEquals(4000L, usage[packageName]) // Clipped to [3000, 7000]
    }

    /**
     * Test Case 6: Query Outside Range - Returns Empty
     * Scenario: Query interval doesn't overlap with any sessions
     * Expected: Returns empty map
     */
    @Test
    fun testQueryUsageForInterval_OutsideRange() {
        val packageName = "com.example.app"

        // Session from 1000 to 5000
        dbHelper.insertUsageSession(packageName, 1000L, 5000L)

        // Query completely outside range
        val usage = dbHelper.queryUsageForInterval(10000L, 20000L)

        assertTrue(usage.isEmpty())
    }

    /**
     * Test Case 7: Invalid Session - Not Inserted
     * Scenario: End time before start time
     * Expected: Session not inserted, no errors
     */
    @Test
    fun testInsertInvalidSession_NotInserted() {
        val packageName = "com.example.app"

        // Try to insert invalid session (end < start)
        dbHelper.insertUsageSession(packageName, 5000L, 1000L)

        // Query should return nothing
        val usage = dbHelper.queryUsageForInterval(0L, 10000L)
        assertTrue(usage.isEmpty())
    }

    /**
     * Test Case 8: Launch Count Filtering - Time Range
     * Scenario: Launches outside time range should not be counted
     * Expected: Only launches within range counted
     */
    @Test
    fun testGetLaunchCounts_TimeRangeFiltering() {
        val packageName = "com.example.app"

        // Launches at different times
        dbHelper.insertUsageSession(packageName, 1000L, 2000L) // Before range
        dbHelper.insertUsageSession(packageName, 5000L, 6000L) // Inside range
        dbHelper.insertUsageSession(packageName, 7000L, 8000L) // Inside range
        dbHelper.insertUsageSession(packageName, 15000L, 16000L) // After range

        // Query only 5000 to 10000
        val launchCounts = dbHelper.getAppLaunchCounts(5000L, 10000L)

        assertEquals(1, launchCounts.size)
        assertEquals(2, launchCounts[packageName]) // Only 2 launches in range
    }

    /**
     * Test Case 9: Partial Overlap - Clips Correctly
     * Scenario: Session starts before interval but ends inside
     * Expected: Duration clipped to interval start
     */
    @Test
    fun testQueryUsageForInterval_PartialOverlapStart() {
        val packageName = "com.example.app"

        // Session from 1000 to 6000
        dbHelper.insertUsageSession(packageName, 1000L, 6000L)

        // Query from 4000 onward
        val usage = dbHelper.queryUsageForInterval(4000L, 10000L)

        assertEquals(1, usage.size)
        assertEquals(2000L, usage[packageName]) // Clipped to [4000, 6000]
    }

    /**
     * Test Case 10: Concurrent Sessions - Accurate Accumulation
     * Scenario: Multiple short sessions for same app
     * Expected: All durations accumulated correctly
     */
    @Test
    fun testConcurrentSessions_AccurateAccumulation() {
        val packageName = "com.example.app"

        // 10 short sessions of 1 second each
        for (i in 0 until 10) {
            val start = i * 1000L
            val end = start + 1000L
            dbHelper.insertUsageSession(packageName, start, end)
        }

        // Query all
        val usage = dbHelper.queryUsageForInterval(0L, 20000L)

        assertEquals(1, usage.size)
        assertEquals(10000L, usage[packageName]) // 10 sessions * 1000ms
    }
}
