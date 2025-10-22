package com.grindrplus.manager.installation.steps

import android.content.Context
import com.grindrplus.manager.installation.BaseStep
import com.grindrplus.manager.installation.Print
import com.reandroid.apk.ApkModule
import com.reandroid.xml.StyleDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.patch.LSPatch
import org.lsposed.patch.util.Logger
import java.io.File
import java.io.IOException


// 5th
class PatchApkStep(
    private val unzipFolder: File,
    private val outputDir: File,
    private val modFile: File,
    private val keyStore: File,
    private val customMapsApiKey: String?,
    private val embedLSPatch: Boolean = true
) : BaseStep() {
    override val name = "Patching Grindr APK"

    private companion object {
        const val MAPS_API_KEY_NAME = "com.google.android.geo.API_KEY"
        const val WRITE_PERMISSION = "android.permission.WRITE_EXTERNAL_STORAGE"
        const val MANAGE_PERMISSION = "android.permission.MANAGE_EXTERNAL_STORAGE"
    }

    var anyApkModified = false

    override suspend fun doExecute(context: Context, print: Print) {
        print("Cleaning output directory...")
        outputDir.listFiles()?.forEach { it.delete() }

        val hostApks = unzipFolder.listFiles()
            ?.filter { it.name.endsWith(".apk") && it.exists() && it.length() > 0 }

        if (hostApks.isNullOrEmpty()) {
            throw IOException("No valid host APK files found to patch")
        }

        // --- List of ALL APKs that need cleanup/modification ---
        // This includes the host APKs AND the mod file (which is also an APK)
        val allApksToModify = hostApks + modFile

        val coroutinesServiceFiles = listOf(
            "META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler",
            "META-INF/services/kotlinx.coroutines.internal.MainDispatcherFactory"
        )

        var anyApkModified = false

        try {
            val baseApkFile = allApksToModify.find { // Find base APK within ALL files
                it.name == "base.apk" || it.name.startsWith("base.apk-")
            } ?: hostApks.first() // Fallback to the first host APK

            print("Starting APK modification process on ${allApksToModify.size} files (Host APKs + Mod File)...")

            // --- Iterate through ALL APKs for cleanup and host app modifications ---
            for (apkFile in allApksToModify) {
                var apkModified = false
                ApkModule.loadApkFile(apkFile).use { apkModule ->
                    print("Processing APK: ${apkFile.name}")

                    // A. Coroutine Service File Cleanup (Apply to ALL APKs: Host and Module)
                    coroutinesServiceFiles.forEach { servicePath ->
                        try {
                            // getResFile for reading arbitrary resources like META-INF/services
                            val resFile = apkModule.getResFile(servicePath)
                            if (resFile != null) {
                                print("CRITICAL FIX: Removing conflicting coroutine service file from ${apkFile.name}: $servicePath")
                                // Delete the file from the APK module
                                resFile.delete()
                                apkModified = true
                            }
                        } catch (e: Exception) {
                            // Suppress exceptions here, typically expected if file isn't present
                            print("Note: Service file not found or failed removal for $servicePath in ${apkFile.name}. Error: ${e.message}")
                        }
                    }

                    // B. Host APK Specific Modifications (Permissions and Maps API Key - Apply ONLY to baseApk)
                    if (apkFile == baseApkFile) {
                        val manifest = apkModule.androidManifest

                        // Permissions logic
                        val permissionToRemove = manifest.getUsesPermission(WRITE_PERMISSION)
                        if (permissionToRemove != null) {
                            manifest.manifestElement.remove(permissionToRemove)
                            print("Removed permission: $WRITE_PERMISSION")
                            apkModified = true
                        } else {
                            print("Permission not found: $WRITE_PERMISSION")
                        }
                        manifest.addUsesPermission(WRITE_PERMISSION)
                        manifest.addUsesPermission(MANAGE_PERMISSION)
                        print("Added permission: $WRITE_PERMISSION")
                        print("Added permission: $MANAGE_PERMISSION")
                        apkModified = true // Permissions are always modified on baseApk

                        // Maps API key logic
                        if (customMapsApiKey != null) {
                            print("Attempting to apply custom Maps API key...")
                            val metaElements =
                                manifest.applicationElement.getElements { element ->
                                    element.name == "meta-data"
                                }

                            var mapsApiKeyFound = false
                            while (metaElements.hasNext()) {
                                val element = metaElements.next()
                                val nameAttr = element.searchAttributeByName("name")

                                if (nameAttr != null && nameAttr.valueString == MAPS_API_KEY_NAME) {
                                    val valueAttr = element.searchAttributeByName("value")
                                    if (valueAttr != null) {
                                        print("Found Maps API key element, replacing with custom key")
                                        valueAttr.setValueAsString(
                                            StyleDocument.parseStyledString(
                                                customMapsApiKey
                                            )
                                        )
                                        mapsApiKeyFound = true
                                        apkModified = true
                                    }
                                }
                            }
                            if (!mapsApiKeyFound) {
                                print("Maps API key element not found in manifest, skipping replacement")
                            }
                        }
                    }

                    // C. Save Changes to APK File
                    if (apkModified) {
                        print("Saving modifications to ${apkFile.name}...")
                        apkModule.writeApk(apkFile)
                        print("Successfully wrote changes back to ${apkFile.name}")
                        anyApkModified = true
                    } else {
                        print("No modifications needed for ${apkFile.name}, skipping save.")
                    }
                }
            }

            if (!anyApkModified) {
                print("WARNING: No changes were successfully applied to any APK file.")
            }


        } catch (e: Exception) {
            print("Error during APK modification: ${e.message}")
            e.printStackTrace()
            throw e
        }

        // --- LSPATCH EXECUTION ---

        if (!embedLSPatch) {
            // ... (rest of embedLSPatch = false logic using hostApks)
            hostApks.forEach { apkFile ->
                val outputFile = File(outputDir, apkFile.name)
                apkFile.copyTo(outputFile, overwrite = true)
                print("Copied ${apkFile.name} to output directory")
            }

            val copiedFiles = outputDir.listFiles()
            if (copiedFiles.isNullOrEmpty()) {
                throw IOException("Copying APKs failed - no output files generated")
            }
            print("Copying completed successfully")
            print("Copied ${copiedFiles.size} files")
            copiedFiles.forEachIndexed { index, file ->
                print("  ${index + 1}. ${file.name} (${file.length() / 1024}KB)")
            }

            return
        }

        // LSPatch uses hostApks (from unzipFolder) as target and modFile as module (-m)
        print("Starting LSPatch process with ${hostApks.size} host APK files...")

        val apkFilePaths = hostApks.map { it.absolutePath }.toTypedArray()

        val logger = object : Logger() {
            override fun d(message: String?) {
                message?.let { print("DEBUG: $it") }
            }

            override fun i(message: String?) {
                message?.let { print("INFO: $it") }
            }

            override fun e(message: String?) {
                message?.let { print("ERROR: $it") }
            }
        }

        print("Using module file: ${modFile.absolutePath}")
        print("Using keystore: ${keyStore.absolutePath}")

        withContext(Dispatchers.IO) {
            LSPatch(
                logger,
                *apkFilePaths, // Host APKs (with cleanup applied)
                "-o", outputDir.absolutePath,
                "-l", "2",
                "-f",
                "-v",
                "-m", modFile.absolutePath, // Module APK (with cleanup applied)
                "-k", keyStore.absolutePath,
                "password",
                "alias",
                "password"
            ).doCommandLine()
        }

        val patchedFiles = outputDir.listFiles()
        if (patchedFiles.isNullOrEmpty()) {
            throw IOException("Patching failed - no output files generated")
        }

        print("Patching completed successfully")
        print("Generated ${patchedFiles.size} patched files")

        patchedFiles.forEachIndexed { index, file ->
            print("  ${index + 1}. ${file.name} (${file.length() / 1024}KB)")
        }
    }
}