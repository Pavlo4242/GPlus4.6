package com.grindrplus.utils

import com.grindrplus.core.Config
import com.grindrplus.core.Logger
import com.grindrplus.hooks.AllowScreenshots
import com.grindrplus.hooks.AntiBlock
import com.grindrplus.hooks.AntiDetection
import com.grindrplus.hooks.BanManagement
import com.grindrplus.hooks.ChatBackupHook
import com.grindrplus.hooks.ChatIndicators
import com.grindrplus.hooks.ChatTerminal
import com.grindrplus.hooks.ComprehensiveAntiDetection
//import com.grindrplus.hooks.ConversationTracker
import com.grindrplus.hooks.DatabasePatternMasking
import com.grindrplus.hooks.DetectionDiagnostics
import com.grindrplus.hooks.DisableAnalytics
import com.grindrplus.hooks.DisableBoosting
import com.grindrplus.hooks.DisableShuffle
import com.grindrplus.hooks.DisableUpdates
import com.grindrplus.hooks.EmptyCalls
import com.grindrplus.hooks.EnableUnlimited
import com.grindrplus.hooks.EnhancedAntiDetection
import com.grindrplus.hooks.ExpiringMedia
import com.grindrplus.hooks.Favorites
import com.grindrplus.hooks.FeatureGranting
import com.grindrplus.hooks.LocalSavedPhrases
import com.grindrplus.hooks.LocationSpoofer
import com.grindrplus.hooks.NetworkPatternNormalizer
import com.grindrplus.hooks.NotificationAlerts
import com.grindrplus.hooks.OnlineIndicator
import com.grindrplus.hooks.PreventChatDeletion
import com.grindrplus.hooks.ProfileChangeTracker
import com.grindrplus.hooks.ProfileDetails
import com.grindrplus.hooks.ProfileViews
import com.grindrplus.hooks.QuickBlock
import com.grindrplus.hooks.StatusDialog
import com.grindrplus.hooks.StorageDetectionBypass
import com.grindrplus.hooks.TimberLogging
import com.grindrplus.hooks.UnlimitedAlbums
import com.grindrplus.hooks.UnlimitedProfiles
import com.grindrplus.hooks.WebSocketAlive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

class HookManager {
    private var hooks = mutableMapOf<KClass<out Hook>, Hook>()

    fun registerHooks(init: Boolean = true) {
        runBlocking(Dispatchers.IO) {
            val hookList = listOf(
                // PRIORITY 1: Anti-detection (MUST BE FIRST)
                ComprehensiveAntiDetection(),
                AntiDetection(),

                // PRIORITY 2: Diagnostic (now toggleable!)
                DetectionDiagnostics(), // UNCOMMENT/ADD THIS LINE

                // PRIORITY 3: Network and communication
                WebSocketAlive(),
                TimberLogging(),

                // Existing Hooks (in original order)
                BanManagement(),
                FeatureGranting(),
                EnableUnlimited(),
                StatusDialog(),
                AntiBlock(),
                NotificationAlerts(),
                DisableUpdates(),
                DisableBoosting(),
                DisableShuffle(),
                AllowScreenshots(),
                ChatIndicators(),
                ChatTerminal(),
                DisableAnalytics(),
                ExpiringMedia(),
                Favorites(),
                LocalSavedPhrases(),
                LocationSpoofer(),
                OnlineIndicator(),
                UnlimitedProfiles(),
                ProfileDetails(),
                ProfileViews(),
                QuickBlock(),
                EmptyCalls(),
                UnlimitedAlbums(),
                PreventChatDeletion(),
                ChatBackupHook()
            )

            // Initialize hook settings in config
            hookList.forEach { hook ->
                Config.initHookSettings(
                    hook.hookName,
                    hook.hookDesc,
                    // Detection Diagnostics should be OFF by default (for production)
                    // Anti-detection hooks should be ON by default
                    state = when (hook.hookName) {
                        "Detection Diagnostics" -> false // OFF by default
                        "Comprehensive Anti Detection", "Anti Detection" -> true // ON by default
                        else -> true // Other hooks ON by default
                    }
                )
            }

            if (!init) return@runBlocking

            hooks = hookList.associateBy { it::class }.toMutableMap()

            // Initialize hooks in order (critical anti-detection first)
            hooks.values.forEachIndexed { index, hook ->
                if (Config.isHookEnabled(hook.hookName)) {
                    try {
                        hook.init()
                        Logger.s("[$index] Initialized hook: ${hook.hookName}")
                    } catch (e: Exception) {
                        Logger.e("[$index] Failed to initialize ${hook.hookName}: ${e.message}")
                        Logger.writeRaw(e.stackTraceToString())
                    }
                } else {
                    Logger.i("[$index] Hook ${hook.hookName} is disabled.")
                }
            }

            // Initialize stealth mode on first run
            val firstRun = Config.get("stealth_initialized", false) as Boolean
            if (!firstRun) {
                StealthConfig.initializeStealthMode()
                StealthConfig.applySafetySettings()
                Config.put("stealth_initialized", true)
                Logger.s("Stealth mode initialized on first run")
            }
        }
    }

    fun reloadHooks() {
        runBlocking(Dispatchers.IO) {
            hooks.values.forEach { hook -> hook.cleanup() }
            hooks.clear()
            registerHooks()
            Logger.s("Reloaded hooks")
        }
    }

    fun init() {
        registerHooks()
    }
}