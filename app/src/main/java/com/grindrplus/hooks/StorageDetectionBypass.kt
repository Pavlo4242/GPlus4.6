package com.grindrplus.hooks

import android.os.Build
import android.os.StatFs
import com.grindrplus.core.Config
import com.grindrplus.core.Logger
import com.grindrplus.core.logi
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import java.io.File

class StorageDetectionBypass : Hook(
    "Storage Detection Bypass",
    "Prevents detection through storage and I/O pattern analysis"
) {
    override fun init() {
        // Hook StatFs to return realistic values
        // Emulators often have suspiciously round numbers or limited storage
        hookStatFs()

        // Hook storage path checks
        hookStoragePaths()

        // Hook I/O timing to add realistic variance
        hookIoTiming()

        logi("Storage detection bypass initialized")
    }

    private fun hookStatFs() {
        try {
            findClass("android.os.StatFs").apply {
                // Hook block count to return realistic values
                hook("getBlockCountLong", HookStage.AFTER) { param ->
                    val original = param.getResult() as? Long
                    if (original != null && isRoundNumber(original)) {
                        // Add some variance to make it look more realistic
                        val variance = (original * 0.02).toLong() // 2% variance
                        param.setResult(original + variance)
                    }
                }

                hook("getAvailableBlocksLong", HookStage.AFTER) { param ->
                    val original = param.getResult() as? Long
                    if (original != null && isRoundNumber(original)) {
                        val variance = (original * 0.03).toLong() // 3% variance
                        param.setResult(original - variance)
                    }
                }

                hook("getFreeBlocksLong", HookStage.AFTER) { param ->
                    val original = param.getResult() as? Long
                    if (original != null && isRoundNumber(original)) {
                        val variance = (original * 0.025).toLong()
                        param.setResult(original - variance)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to hook StatFs: ${e.message}")
        }
    }

    private fun hookStoragePaths() {
        try {
            // Hook File path methods that might expose emulator paths
            val suspiciousPaths = setOf(
                "/mnt/sdcard",
                "/mnt/shared",
                "/data/media/0"
            )

            findClass("java.io.File")
                .hook("getAbsolutePath", HookStage.AFTER) { param ->
                    val path = param.getResult() as? String
                    if (path != null && path.contains("GrindrPlus_Archive")) {
                        // Ensure our archive path looks legitimate
                        // Already using /sdcard/Pictures which is standard
                    }
                }
        } catch (e: Exception) {
            Logger.e("Failed to hook storage paths: ${e.message}")
        }
    }

    private fun hookIoTiming() {
        // Emulators often have unrealistically fast or slow I/O
        // We add realistic timing variance
        try {
            findClass("java.io.FileOutputStream")
                .hook("write", HookStage.BEFORE) { param ->
                    // Add tiny random delay to make I/O patterns more realistic
                    if (Config.get("add_io_variance", true) as Boolean) {
                        val delayNs = (Math.random() * 1000).toLong() // 0-1 microsecond
                        Thread.sleep(0, delayNs.toInt())
                    }
                }
        } catch (e: Exception) {
            // Don't log this as it might be called too frequently
        }
    }

    private fun isRoundNumber(value: Long): Boolean {
        // Check if number is suspiciously round (like exactly 8GB, 16GB, etc.)
        val gb = 1024L * 1024L * 1024L
        return when {
            value % (16 * gb) == 0L -> true
            value % (8 * gb) == 0L -> true
            value % (4 * gb) == 0L -> true
            value % (2 * gb) == 0L -> true
            else -> false
        }
    }
}