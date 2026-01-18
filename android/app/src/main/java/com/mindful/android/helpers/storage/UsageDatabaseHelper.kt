package com.mindful.android.helpers.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.ConcurrentHashMap

/**
 * A lightweight database helper to store app usage events.
 * Used to replace System UsageStatsManager for a self-contained tracking solution.
 */
class UsageDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "usage_events.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_USAGE = "usage_events"
        const val COLUMN_ID = "_id"
        const val COLUMN_PACKAGE_NAME = "package_name"
        const val COLUMN_START_TIME = "start_time"
        const val COLUMN_END_TIME = "end_time"
        const val COLUMN_DURATION = "duration" // Cached duration to simplify queries

        // Singleton instance
        @Volatile
        private var INSTANCE: UsageDatabaseHelper? = null

        fun getInstance(context: Context): UsageDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UsageDatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_USAGE (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PACKAGE_NAME TEXT NOT NULL,
                $COLUMN_START_TIME INTEGER NOT NULL,
                $COLUMN_END_TIME INTEGER,
                $COLUMN_DURATION INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createTable)

        // Index for faster queries on time range and package
        db.execSQL("CREATE INDEX idx_time ON $TABLE_USAGE ($COLUMN_START_TIME, $COLUMN_END_TIME)")
        db.execSQL("CREATE INDEX idx_package ON $TABLE_USAGE ($COLUMN_PACKAGE_NAME)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Drop and recreate for now (Beta strategy)
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USAGE")
        onCreate(db)
    }

    /**
     * Inserts a completed usage session.
     */
    fun insertUsageSession(packageName: String, startTime: Long, endTime: Long) {
        if (endTime <= startTime) return

        val values = ContentValues().apply {
            put(COLUMN_PACKAGE_NAME, packageName)
            put(COLUMN_START_TIME, startTime)
            put(COLUMN_END_TIME, endTime)
            put(COLUMN_DURATION, endTime - startTime)
        }
        writableDatabase.insert(TABLE_USAGE, null, values)
    }

    /**
     * Queries aggregated usage duration per package for a given time interval.
     * Logic mimics UsageStatsManager.queryUsageStats aggregation.
     *
     * @return Map<PackageName, DurationInMillis>
     */
    fun queryUsageForInterval(startTime: Long, endTime: Long): Map<String, Long> {
        val usageMap = ConcurrentHashMap<String, Long>()
        val db = readableDatabase

        // Select events that overlap with the interval [startTime, endTime]
        // Overlap logic: event_start < interval_end AND event_end > interval_start
        // We clip the duration to the requested interval.
        val selection = "$COLUMN_START_TIME < ? AND $COLUMN_END_TIME > ?"
        val selectionArgs = arrayOf(endTime.toString(), startTime.toString())

        val cursor = db.query(
            TABLE_USAGE,
            arrayOf(COLUMN_PACKAGE_NAME, COLUMN_START_TIME, COLUMN_END_TIME),
            selection,
            selectionArgs,
            null,
            null,
            null
        )

        cursor.use {
            if (it.moveToFirst()) {
                val pkgIndex = it.getColumnIndex(COLUMN_PACKAGE_NAME)
                val startIndex = it.getColumnIndex(COLUMN_START_TIME)
                val endIndex = it.getColumnIndex(COLUMN_END_TIME)

                do {
                    val pkg = it.getString(pkgIndex)
                    val s = it.getLong(startIndex)
                    val e = it.getLong(endIndex)

                    // Clip start and end to the requested interval
                    val clippedStart = maxOf(s, startTime)
                    val clippedEnd = minOf(e, endTime)
                    val duration = maxOf(0L, clippedEnd - clippedStart)

                    usageMap[pkg] = usageMap.getOrDefault(pkg, 0L) + duration
                } while (it.moveToNext())
            }
        }

        return usageMap
    }
}
