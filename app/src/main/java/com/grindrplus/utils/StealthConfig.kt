package com.grindrplus.utils

import com.grindrplus.core.Config
import com.grindrplus.core.LogSource
import com.grindrplus.core.logi
import com.grindrplus.core.logw
import com.grindrplus.core.Logger


object StealthConfig {

    /**
     * Initialize stealth mode settings to minimize detection
     */
    fun initializeStealthMode() {
        // Add realistic I/O variance
        Config.put("add_io_variance", true)

        // Throttle database operations
        Config.put("throttle_db_operations", true)

        // Add network request delays
        Config.put("realistic_network_timing", true)

        // Limit archive batch sizes
        Config.put("max_archive_batch_size", 5)

        // Add delays between archives
        Config.put("archive_delay_ms", 2000)

        // Limit concurrent operations
        Config.put("max_concurrent_operations", 3)

        Logger.i("Stealth mode initialized", LogSource.MANAGER)
    }

    /**
     * Get recommended archive settings
     */
    fun getArchiveSettings(): ArchiveSettings {
        return ArchiveSettings(
            maxBatchSize = Config.get("max_archive_batch_size", 5) as Int,
            delayBetweenDownloads = Config.get("archive_delay_ms", 2000) as Int,
            maxConcurrent = Config.get("max_concurrent_operations", 3) as Int,
            addRandomDelay = true,
            minRandomDelayMs = 500,
            maxRandomDelayMs = 3000
        )
    }

    /**
     * Check if current environment might trigger detection
     */
    fun performSafetyCheck(): SafetyCheckResult {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Check database size
        try {
            val dbFile = java.io.File("/data/data/com.grindrapp.android/databases/grindrplus.db")
            if (dbFile.exists()) {
                val sizeMb = dbFile.length() / (1024.0 * 1024.0)
                if (sizeMb > 500) {
                    warnings.add("Database is large (${sizeMb.toInt()}MB). Consider exporting and cleaning old data.")
                }
            }
        } catch (e: Exception) {
            // Silently fail
        }

        // Check storage location
        val archiveDir = java.io.File("/sdcard/Pictures/GrindrPlus_Archive")
        if (archiveDir.exists()) {
            val fileCount = archiveDir.walkTopDown().filter { it.isFile }.count()
            if (fileCount > 10000) {
                warnings.add("Archive contains $fileCount files. Consider organizing or moving some.")
            }
        }

        // Check if too many hooks are enabled
        val enabledHooks = Config.getHooksSettings().filter { it.value.second }.size
        if (enabledHooks > 35) {
            warnings.add("Many hooks enabled ($enabledHooks). Consider disabling unused hooks.")
        }

        return SafetyCheckResult(
            isSafe = issues.isEmpty(),
            issues = issues,
            warnings = warnings
        )
    }

    /**
     * Apply recommended safety settings
     */
    fun applySafetySettings() {
        // Disable potentially risky features
        Config.put("debug_mode", false)

        // Enable all anti-detection hooks
        Config.setHookEnabled("Enhanced Anti Detection", true)
        Config.setHookEnabled("Storage Detection Bypass", true)
        Config.setHookEnabled("Database Pattern Masking", true)
        Config.setHookEnabled("Network Pattern Normalizer", true)
        Config.setHookEnabled("Anti Detection", true)

        // Reduce aggressive features
        Config.put("always_online_interval", 10 * 60 * 1000) // 10 minutes

        Logger.i("Safety settings applied", LogSource.MANAGER)
    }

    data class ArchiveSettings(
        val maxBatchSize: Int,
        val delayBetweenDownloads: Int,
        val maxConcurrent: Int,
        val addRandomDelay: Boolean,
        val minRandomDelayMs: Int,
        val maxRandomDelayMs: Int
    )

    data class SafetyCheckResult(
        val isSafe: Boolean,
        val issues: List<String>,
        val warnings: List<String>
    ) {
        fun printReport() {
            if (isSafe) {
                Logger.i("✓ Safety check passed", LogSource.MANAGER)
            } else {
                Logger.w("⚠ Safety check found issues:", LogSource.MANAGER)
                issues.forEach { Logger.w("  - $it", LogSource.MANAGER) }
            }

            if (warnings.isNotEmpty()) {
                Logger.w("Warnings:", LogSource.MANAGER)
                warnings.forEach { Logger.w("  - $it", LogSource.MANAGER) }
            }
        }
    }
}