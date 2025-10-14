package com.grindrplus.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Handler
import android.os.Looper
import com.grindrplus.core.LogSource
import com.grindrplus.core.Logger
import java.io.File

object HttpBodyLogger {

    private lateinit var dbFile: File
    @Volatile private var initialized = false

    private fun getDatabase(): SQLiteDatabase {
        return SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    }

    // Public entry point with optional delay
    fun initialize(context: Context, delayMs: Long = 0L) {
        if (initialized) {
            Logger.d("HttpBodyLogger already initialized, skipping", LogSource.HTTP)
            return
        }

        dbFile = File(context.filesDir, "HttpBodyLogs.db")

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
        if (initialized) return

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
            initialized = true
            Logger.i("HttpBodyLogger initialized in private storage", LogSource.HTTP)
        } catch (e: Exception) {
            Logger.e("Failed to initialize HttpBodyLogger: ${e.message}", LogSource.HTTP)
        } finally {
            db.close()
        }
    }

    fun log(url: String, method: String, body: String?) {
        if (!initialized) {
            Logger.w("HttpBodyLogger not initialized, skipping log", LogSource.HTTP)
            return
        }

        if (body.isNullOrEmpty() || !body.trim().startsWith("{")) return

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