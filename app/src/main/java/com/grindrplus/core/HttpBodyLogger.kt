package com.grindrplus.core

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.os.Handler
import android.os.Looper
import java.io.File

object HttpBodyLogger {

    private val dbFile = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "HttpBodyLogs.db"
    )

    private fun getDatabase(): SQLiteDatabase {
        return SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    }

    // Public entry point with optional delay
    fun initialize(delayMs: Long = 0L) {
        if (delayMs > 0) {
            Logger.d("Scheduling HttpBodyLogger.initialize() after ${delayMs}ms", LogSource.HTTP)
            Handler(Looper.getMainLooper()).postDelayed({
                initializeInternal()
            }, delayMs)
        } else {
            initializeInternal()
        }
    }

    // Actual setup logic
    private fun initializeInternal() {
        val db = getDatabase()
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    url TEXT NOT NULL,
                    method TEXT NOT NULL,
                    response_body TEXT
                )
                """.trimIndent()
            )
        } finally {
            db.close()
        }
    }

    fun log(url: String, method: String, body: String?) {
        if (body.isNullOrEmpty() || !body.trim().startsWith("{")) {
            return // Don't log non-JSON or empty bodies
        }

        val db = getDatabase()
        try {
            val values = ContentValues().apply {
                put("timestamp", System.currentTimeMillis() / 1000)
                put("url", url)
                put("method", method)
                put("response_body", body)
            }
            db.insert("logs", null, values)
        } catch (e: Exception) {
            Logger.e("Failed to write to HTTP body log database: ${e.message}")
        } finally {
            db.close()
        }
    }
}