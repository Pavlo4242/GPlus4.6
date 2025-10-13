package com.grindrplus.manager.installation.steps

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
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
import java.util.zip.ZipFile

class PatchApkStep(
    private val inputDir: File,
    private val outputDir: File,
    private val modFile: File,
    private val keyStore: File,
    private val customMapsApiKey: String?,
    private val embedLSPatch: Boolean = true
) : BaseStep() {
    override val name = "Patching Grindr APK (Maps API Key & LSPatch)"

    private companion object {
        const val MAPS_API_KEY_NAME = "com.google.android.geo.API_KEY"
        const val TAG = "PatchApkStep"
    }

    override suspend fun doExecute(context: Context, print: Print) {
        val apkFiles = inputDir.listFiles()?.filter { it.name.endsWith(".apk") && it.exists() && it.length() > 0 }
        if (apkFiles.isNullOrEmpty()) {
            throw IOException("No APK files found in input directory to patch.")
        }

        print("Found ${apkFiles.size} APK files: ${apkFiles.joinToString { it.name }}")

        // Step 1: Comprehensive APK diagnostics before any processing
        print("=== APK DIAGNOSTICS ===")
        apkFiles.forEach { apkFile ->
            diagnoseApkFile(apkFile, context, print)
        }

        try {
            if (customMapsApiKey != null) {
                print("Attempting to apply custom Maps API key...")
                val baseApk = apkFiles.find { it.name == "base.apk" || it.name.startsWith("base.apk-") } ?: apkFiles.first()

                print("Using ${baseApk.name} for Maps API key modification")
                val apkModule = ApkModule.loadApkFile(baseApk)
                val applicationElement = apkModule.androidManifest.applicationElement
                val metaElements = applicationElement.getElements { it.name == "meta-data" }
                var foundMapsKey = false
                while (metaElements.hasNext() && !foundMapsKey) {
                    val element = metaElements.next()
                    val nameAttr = element.searchAttributeByName("name")
                    if (nameAttr != null && nameAttr.valueString == MAPS_API_KEY_NAME) {
                        val valueAttr = element.searchAttributeByName("value")
                        if (valueAttr != null) {
                            print("Found Maps API key element, replacing with custom key")
                            valueAttr.setValueAsString(StyleDocument.parseStyledString(customMapsApiKey))
                            foundMapsKey = true
                        }
                    }
                }
                if (foundMapsKey) {
                    print("Successfully applied Maps API key replacement. Saving APK...")
                    apkModule.writeApk(baseApk)
                    print("APK saved. Running post-modification diagnostics...")
                    diagnoseApkFile(baseApk, context, print)
                } else {
                    print("Maps API key element not found, skipping save.")
                }
            }
        } catch (e: Exception) {
            print("Error during Maps API key patching: ${e.message}")
            throw e
        }

        if (!embedLSPatch) {
            print("Skipping LSPatch. APKs are ready in output directory.")
            return
        }

        print("=== STARTING LSPATCH PROCESS ===")

        // Verify mod file exists and is valid
        if (!modFile.exists() || !modFile.canRead()) {
            throw IOException("Mod file not found or not readable: ${modFile.absolutePath}")
        }
        print("Mod file verified: ${modFile.name} (${modFile.length()} bytes)")

        // Verify keystore exists
        if (!keyStore.exists() || !keyStore.canRead()) {
            throw IOException("Keystore not found or not readable: ${keyStore.absolutePath}")
        }
        print("Keystore verified: ${keyStore.name}")

        // LSPatch needs a clean output directory
        val lspatchOutputDir = File(outputDir, "lspatch_temp").also {
            it.deleteRecursively()
            it.mkdirs()
        }

        val apkFilePaths = apkFiles.map { it.absolutePath }.toTypedArray()

        val logger = object : Logger() {
            override fun d(message: String?) {
                message?.let {
                    print("LSPATCH DEBUG: $it")
                    Log.d(TAG, "LSPATCH: $it")
                }
            }
            override fun i(message: String?) {
                message?.let {
                    print("LSPATCH INFO: $it")
                    Log.i(TAG, "LSPATCH: $it")
                }
            }
            override fun e(message: String?) {
                message?.let {
                    print("LSPATCH ERROR: $it")
                    Log.e(TAG, "LSPATCH: $it")
                }
            }
        }

        print("LSPatch command parameters:")
        print("  Input APKs: ${apkFilePaths.joinToString()}")
        print("  Output dir: ${lspatchOutputDir.absolutePath}")
        print("  Mod file: ${modFile.absolutePath}")
        print("  Keystore: ${keyStore.absolutePath}")

        try {
            withContext(Dispatchers.IO) {
                print("Executing LSPatch in background thread...")
                LSPatch(
                    logger,
                    *apkFilePaths,
                    "-o", lspatchOutputDir.absolutePath,
                    "-l", "2", "-f", "-v",
                    "-m", modFile.absolutePath,
                    "-k", keyStore.absolutePath, "password", "alias", "password"
                ).doCommandLine()
            }
        } catch (e: Exception) {
            print("=== LSPATCH FAILED ===")
            print("Exception type: ${e.javaClass.simpleName}")
            print("Exception message: ${e.message}")

            // Enhanced error diagnostics
            when {
                e.message?.contains("signature", ignoreCase = true) == true -> {
                    print("SIGNATURE-RELATED ERROR DETECTED")
                    print("This usually means LSPatch cannot extract or verify the APK signature")
                    diagnoseSignatureIssues(apkFiles, context, print)
                }
                e.message?.contains("original signature failed") == true -> {
                    print("ORIGINAL SIGNATURE EXTRACTION FAILED")
                    print("LSPatch cannot read the signature from one of the APK files")
                    diagnoseSignatureIssues(apkFiles, context, print)
                }
                else -> {
                    print("General LSPatch error - see logs for details")
                }
            }

            // Log stack trace for debugging
            Log.e(TAG, "LSPatch failed", e)
            e.stackTrace.take(10).forEach { stackElement ->
                Log.e(TAG, "  at $stackElement")
            }

            lspatchOutputDir.deleteRecursively()
            throw IOException("LSPatch failed: ${e.message}. Check logcat for full stack trace.")
        }

        val patchedFiles = lspatchOutputDir.listFiles()
        if (patchedFiles.isNullOrEmpty() || patchedFiles.none { it.name.endsWith(".apk") }) {
            throw IOException("LSPatch failed - no output APK files generated in ${lspatchOutputDir.absolutePath}")
        }

        print("LSPatch generated ${patchedFiles.size} files: ${patchedFiles.joinToString { it.name }}")

        // Move patched files from temp dir back to main output dir
        print("Moving final patched files to output directory...")
        patchedFiles.forEach { file ->
            val finalDest = File(outputDir, file.name)
            if (file.copyTo(finalDest, overwrite = true).exists()) {
                print("  Success: ${file.name} -> ${finalDest.name}")
            } else {
                print("  Warning: Failed to move ${file.name}")
            }
        }
        lspatchOutputDir.deleteRecursively()

        print("=== LSPATCH PROCESS COMPLETED SUCCESSFULLY ===")

        // Final verification of output files
        val finalApks = outputDir.listFiles()?.filter { it.name.endsWith(".apk") }
        if (finalApks.isNullOrEmpty()) {
            throw IOException("No APK files found in output directory after patching")
        }
        print("Final APK files: ${finalApks.joinToString { "${it.name} (${it.length()} bytes)" }}")
    }

    private fun diagnoseApkFile(apkFile: File, context: Context, print: Print) {
        print("Diagnosing APK: ${apkFile.name}")
        print("  File exists: ${apkFile.exists()}")
        print("  File size: ${apkFile.length()} bytes")
        print("  File readable: ${apkFile.canRead()}")
        print("  File writable: ${apkFile.canWrite()}")

        // Check APK structure
        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries().toList()
                val hasManifest = entries.any { it.name == "AndroidManifest.xml" }
                val hasClasses = entries.any { it.name.startsWith("classes") && it.name.endsWith(".dex") }
                val hasResources = entries.any { it.name == "resources.arsc" }

                print("  APK Structure:")
                print("    - AndroidManifest.xml: $hasManifest")
                print("    - classes.dex: $hasClasses")
                print("    - resources.arsc: $hasResources")
                print("    - Total entries: ${entries.size}")

                if (!hasManifest) {
                    print("  ❌ CRITICAL: Missing AndroidManifest.xml")
                }
                if (!hasClasses && !hasResources) {
                    print("  ⚠️ WARNING: No classes.dex or resources.arsc found")
                }
            }
        } catch (e: Exception) {
            print("  ❌ ERROR: Failed to analyze APK structure: ${e.message}")
        }

        // Check signature - FIXED: Use local variable to avoid smart cast issues
        try {
            val packageInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
            val signatures = packageInfo?.signatures // Store in local variable

            if (signatures != null && signatures.isNotEmpty()) {
                print("  Signatures: ${signatures.size} signature(s) found")
                signatures.forEachIndexed { index, signature ->
                    val sigString = signature.toCharsString()
                    print("    Signature $index: ${sigString.take(20)}... (length: ${sigString.length})")
                }
                print("  Package name: ${packageInfo.packageName ?: "Unknown"}")
            } else {
                print("  ❌ WARNING: No signatures found in APK")
            }
        } catch (e: Exception) {
            print("  ❌ ERROR: Failed to extract signature: ${e.message}")
        }
        print("---")
    }

    private fun diagnoseSignatureIssues(apkFiles: List<File>, context: Context, print: Print) {
        print("=== SIGNATURE DIAGNOSIS ===")
        print("Testing each APK file individually for signature issues:")

        apkFiles.forEach { apkFile ->
            print("Testing: ${apkFile.name}")
            try {
                // Try to get detailed package info
                val packageInfo = context.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.GET_SIGNATURES or PackageManager.GET_META_DATA
                )

                if (packageInfo == null) {
                    print("  ❌ FAILED: Could not parse APK at all")
                    return@forEach
                }

                // FIXED: Use local variable to avoid smart cast issues
                val signatures = packageInfo.signatures

                if (signatures == null || signatures.isEmpty()) {
                    print("  ❌ FAILED: No signatures array")
                } else if (signatures.size == 1 && signatures[0].toCharsString().isEmpty()) {
                    print("  ❌ FAILED: Empty signature string")
                } else {
                    print("  ✅ PASSED: ${signatures.size} valid signature(s)")
                    print("  Package: ${packageInfo.packageName ?: "Unknown"}")
                }
            } catch (e: Exception) {
                print("  ❌ ERROR: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        print("=== SIGNATURE DIAGNOSIS COMPLETE ===")
    }
}