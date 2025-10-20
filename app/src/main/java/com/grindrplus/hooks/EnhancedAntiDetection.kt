package com.grindrplus.hooks

import android.os.Build
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import com.grindrplus.utils.hookConstructor


class EnhancedAntiDetection : Hook(
    "Enhanced Anti Detection",
    "Enhanced protection against emulator and environment detection"
){
    private val grindrMiscClass = "mg.n"
    private val devicePropertiesCollector = "siftscience.android.DevicePropertiesCollector"
    private val commonUtils = "com.google.firebase.crashlytics.internal.common.CommonUtils"
    private val osData = "com.google.firebase.crashlytics.internal.model.AutoValue_StaticSessionData_OsData"
    private val buildClass = "android.os.Build"

    override fun init() {
        // Hook the main emulator detection
        findClass(grindrMiscClass)
            .hook("O", HookStage.AFTER) { param ->
                param.setResult(false)
            }

        // Firebase Crashlytics checks
        findClass(commonUtils)
            .hook("isRooted", HookStage.BEFORE) { param ->
                param.setResult(false)
            }

        findClass(commonUtils)
            .hook("isEmulator", HookStage.BEFORE) { param ->
                param.setResult(false)
            }

        findClass(commonUtils)
            .hook("isAppDebuggable", HookStage.BEFORE) { param ->
                param.setResult(false)
            }

        // Sift Science device properties
        findClass(devicePropertiesCollector)
            .hook("existingRWPaths", HookStage.BEFORE) { param ->
                param.setResult(emptyList<String>())
            }

        findClass(devicePropertiesCollector)
            .hook("existingRootFiles", HookStage.BEFORE) { param ->
                param.setResult(emptyList<String>())
            }

        findClass(devicePropertiesCollector)
            .hook("existingRootPackages", HookStage.BEFORE) { param ->
                param.setResult(emptyList<String>())
            }

        findClass(devicePropertiesCollector)
            .hook("existingDangerousProperties", HookStage.BEFORE) { param ->
                param.setResult(emptyList<String>())
            }

        // Hook Build properties that might expose emulator
        try {
            findClass(buildClass).apply {
                hook("getRadioVersion", HookStage.AFTER) { param ->
                    val result = param.getResult() as? String
                    if (result.isNullOrEmpty() || result == "unknown") {
                        param.setResult("1.0.0.0")
                    }
                }
            }
        } catch (e: Exception) {
            // Build.getRadioVersion might not exist on all Android versions
        }

        // Hook OS data constructor to hide root status
        findClass(osData)
            .hookConstructor(HookStage.BEFORE) { param ->
                param.setArg(2, false) // isRooted parameter
            }

        // Additional hardware checks
        hookHardwareChecks()

        // File system checks
        hookFileSystemChecks()
    }

    private fun hookHardwareChecks() {
        // Hook Build.HARDWARE checks
        val emulatorHardware = setOf(
            "goldfish", "ranchu", "vbox86", "nox", "ttVM_x86"
        )

        // These checks look for emulator-specific hardware identifiers
        try {
            // Some apps check Build.HARDWARE directly via reflection
            // We can't modify the actual Build fields, but we can hook methods that read them
        } catch (e: Exception) {
            // Silently fail if hooks don't exist
        }
    }

    private fun hookFileSystemChecks() {
        // Hook file existence checks for common emulator/root indicators
        val suspiciousFiles = setOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/system/app/SuperSU",
            "/system/xbin/daemonsu"
        )

        try {
            findClass("java.io.File")
                .hook("exists", HookStage.BEFORE) { param ->
                    val file = param.thisObject() as? java.io.File
                    if (file != null && suspiciousFiles.contains(file.absolutePath)) {
                        param.setResult(false)
                    }
                }
        } catch (e: Exception) {
            // File hook might be too broad, skip if it causes issues
        }
    }
}