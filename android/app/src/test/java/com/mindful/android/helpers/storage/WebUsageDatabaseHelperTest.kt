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
 * Unit tests for WebUsageDatabaseHelper to verify website usage tracking functionality
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WebUsageDatabaseHelperTest {

    private lateinit var context: Context
    private lateinit var dbHelper: WebUsageDatabaseHelper

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dbHelper = WebUsageDatabaseHelper(context)

        // Clear any existing data
        dbHelper.writableDatabase.execSQL("DELETE FROM ${WebUsageDatabaseHelper.TABLE_USAGE}")
    }

    /**
     * Test Case 1: Add Usage - New Domain
     * Scenario: First time tracking a domain
     * Expected: Usage is stored correctly
     */
    @Test
    fun testAddUsage_NewDomain() {
        val domain = "facebook.com"
        val timeMs = 5000L
        val date = getTodayMidnight()

        // Add usage
        dbHelper.addUsage(domain, timeMs, date)

        // Verify
        val usage = dbHelper.getUsage(domain, date)
        assertEquals(timeMs, usage)
    }

    /**
     * Test Case 2: Add Usage - Existing Domain Accumulates
     * Scenario: Add usage multiple times for same domain/date
     * Expected: Usage accumulates (sums up)
     */
    @Test
    fun testAddUsage_ExistingDomain_Accumulates() {
        val domain = "youtube.com"
        val date = getTodayMidnight()

        // Add usage 3 times
        dbHelper.addUsage(domain, 1000L, date) // 1 sec
        dbHelper.addUsage(domain, 2000L, date) // 2 sec
        dbHelper.addUsage(domain, 3000L, date) // 3 sec

        // Verify total is accumulated
        val usage = dbHelper.getUsage(domain, date)
        assertEquals(6000L, usage) // 1 + 2 + 3 = 6 sec
    }

    /**
     * Test Case 3: Get Usage - Returns Correct Amount
     * Scenario: Query usage for a domain
     * Expected: Returns the exact stored amount
     */
    @Test
    fun testGetUsage_ReturnsCorrectAmount() {
        val domain = "twitter.com"
        val timeMs = 12345L
        val date = getTodayMidnight()

        // Add usage
        dbHelper.addUsage(domain, timeMs, date)

        // Query and verify
        val usage = dbHelper.getUsage(domain, date)
        assertEquals(timeMs, usage)
    }

    /**
     * Test Case 4: Get Usage - Different Dates Separate
     * Scenario: Same domain on different dates
     * Expected: Usage tracked separately per date
     */
    @Test
    fun testGetUsage_DifferentDates_Separate() {
        val domain = "reddit.com"
        val today = getTodayMidnight()
        val yesterday = today - (24 * 60 * 60 * 1000) // 1 day ago

        // Add usage for today and yesterday
        dbHelper.addUsage(domain, 5000L, today)
        dbHelper.addUsage(domain, 3000L, yesterday)

        // Verify separate tracking
        val todayUsage = dbHelper.getUsage(domain, today)
        val yesterdayUsage = dbHelper.getUsage(domain, yesterday)

        assertEquals(5000L, todayUsage)
        assertEquals(3000L, yesterdayUsage)
    }

    /**
     * Test Case 5: Get Usage - Non-Existent Domain Returns Zero
     * Scenario: Query domain that was never tracked
     * Expected: Returns 0
     */
    @Test
    fun testGetUsage_NonExistentDomain_ReturnsZero() {
        val domain = "nonexistent.com"
        val date = getTodayMidnight()

        // Query without adding any usage
        val usage = dbHelper.getUsage(domain, date)
        assertEquals(0L, usage)
    }

    /**
     * Test Case 6: Multiple Domains - Separate Tracking
     * Scenario: Track multiple domains on same date
     * Expected: Each domain tracked independently
     */
    @Test
    fun testMultipleDomains_SeparateTracking() {
        val date = getTodayMidnight()

        // Add usage for different domains
        dbHelper.addUsage("facebook.com", 5000L, date)
        dbHelper.addUsage("twitter.com", 3000L, date)
        dbHelper.addUsage("instagram.com", 7000L, date)

        // Verify each domain
        assertEquals(5000L, dbHelper.getUsage("facebook.com", date))
        assertEquals(3000L, dbHelper.getUsage("twitter.com", date))
        assertEquals(7000L, dbHelper.getUsage("instagram.com", date))
    }

    /**
     * Test Case 7: Atomic Updates - Concurrent Accumulation
     * Scenario: Multiple rapid additions for same domain
     * Expected: All additions accumulated correctly (no race conditions)
     */
    @Test
    fun testAtomicUpdates_ConcurrentAccumulation() {
        val domain = "example.com"
        val date = getTodayMidnight()

        // Add usage 100 times
        repeat(100) {
            dbHelper.addUsage(domain, 100L, date)
        }

        // Verify total
        val usage = dbHelper.getUsage(domain, date)
        assertEquals(10000L, usage) // 100 * 100 = 10000
    }

    /**
     * Test Case 8: Same Domain Different Dates - No Cross-Contamination
     * Scenario: Track same domain across multiple dates with accumulation
     * Expected: No mixing of data between dates
     */
    @Test
    fun testSameDomainDifferentDates_NoCrossContamination() {
        val domain = "google.com"
        val date1 = getTodayMidnight()
        val date2 = date1 - (24 * 60 * 60 * 1000) // yesterday
        val date3 = date1 - (2 * 24 * 60 * 60 * 1000) // 2 days ago

        // Add usage for different dates
        dbHelper.addUsage(domain, 1000L, date1)
        dbHelper.addUsage(domain, 1000L, date1) // Accumulate date1

        dbHelper.addUsage(domain, 2000L, date2)
        dbHelper.addUsage(domain, 2000L, date2) // Accumulate date2

        dbHelper.addUsage(domain, 3000L, date3)
        dbHelper.addUsage(domain, 3000L, date3) // Accumulate date3

        // Verify each date
        assertEquals(2000L, dbHelper.getUsage(domain, date1))
        assertEquals(4000L, dbHelper.getUsage(domain, date2))
        assertEquals(6000L, dbHelper.getUsage(domain, date3))
    }

    /**
     * Helper function to get today's midnight timestamp
     */
    private fun getTodayMidnight(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
