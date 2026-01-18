package com.mindful.android.helpers.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class WebUsageDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "web_usage.db"
        private const val DATABASE_VERSION = 1
        const val TABLE_USAGE = "web_usage"
        const val COLUMN_DOMAIN = "domain"
        const val COLUMN_DATE = "date"
        const val COLUMN_USAGE_TIME = "usage_time"

        @Volatile
        private var INSTANCE: WebUsageDatabaseHelper? = null

        fun getInstance(context: Context): WebUsageDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebUsageDatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_USAGE (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_DOMAIN TEXT NOT NULL,
                $COLUMN_DATE INTEGER NOT NULL,
                $COLUMN_USAGE_TIME INTEGER DEFAULT 0,
                UNIQUE($COLUMN_DOMAIN, $COLUMN_DATE)
            )
        """.trimIndent()
        db.execSQL(createTable)
        db.execSQL("CREATE INDEX idx_domain_date ON $TABLE_USAGE ($COLUMN_DOMAIN, $COLUMN_DATE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USAGE")
        onCreate(db)
    }

    fun addUsage(domain: String, timeMs: Long, date: Long) {
        val db = writableDatabase
        // Atomic update
        val sql = "UPDATE $TABLE_USAGE SET $COLUMN_USAGE_TIME = $COLUMN_USAGE_TIME + ? WHERE $COLUMN_DOMAIN = ? AND $COLUMN_DATE = ?"
        val statement = db.compileStatement(sql)
        statement.bindLong(1, timeMs)
        statement.bindString(2, domain)
        statement.bindLong(3, date)
        
        val updated = statement.executeUpdateDelete()

        if (updated == 0) {
            val values = ContentValues().apply {
                put(COLUMN_DOMAIN, domain)
                put(COLUMN_DATE, date)
                put(COLUMN_USAGE_TIME, timeMs)
            }
            db.insert(TABLE_USAGE, null, values)
        }
    }

    fun getUsage(domain: String, date: Long): Long {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_USAGE,
            arrayOf(COLUMN_USAGE_TIME),
            "$COLUMN_DOMAIN = ? AND $COLUMN_DATE = ?",
            arrayOf(domain, date.toString()),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }
}
