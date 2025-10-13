package com.grindrplus.manager.installation.steps

import android.content.Context
import android.util.Log
import com.grindrplus.manager.installation.BaseStep
import com.grindrplus.manager.installation.Print
import com.grindrplus.manager.utils.SessionInstaller
import java.io.File
import java.io.IOException

class InstallApkStep(
    private val outputDir: File
) : BaseStep() {
    override val name = "Installing Grindr APK"

    private companion object {
        const val TAG = "InstallApkStep"
    }

    override suspend fun doExecute(context: Context, print: Print) {
        print("Starting APK installation process...")

        val patchedFiles = outputDir.listFiles()?.toList() ?: emptyList()
        if (patchedFiles.isEmpty()) {
            throw IOException("No patched APK files found for installation in ${outputDir.absolutePath}")
        }

        print("Found ${patchedFiles.size} files in output directory:")
        patchedFiles.forEach { file ->
            print("  - ${file.name} (${file.length()} bytes)")
        }

        val filteredApks = patchedFiles.filter {
            it.name.endsWith(".apk") && it.exists() && it.length() > 0
        }

        if (filteredApks.isEmpty()) {
            throw IOException("No valid APK files found for installation. Checked ${patchedFiles.size} files.")
        }

        print("Starting installation of ${filteredApks.size} APK files")
        filteredApks.forEachIndexed { index, file ->
            print("  Installing (${index + 1}/${filteredApks.size}): ${file.name} (${file.length()} bytes)")

            // Verify each APK before installation
            if (!file.exists()) {
                throw IOException("APK file disappeared: ${file.absolutePath}")
            }
            if (file.length() == 0L) {
                throw IOException("APK file is empty: ${file.absolutePath}")
            }
        }

        print("Launching installer...")

        var installationSuccess = false
        var installationError: String? = null

        val success = SessionInstaller().installApks(
            context,
            filteredApks,
            false,
            log = { message ->
                print("INSTALLER: $message")
                Log.i(TAG, "Installer: $message")
            },
            callback = { success, message ->
                installationSuccess = success
                installationError = message
                if (success) {
                    print("✅ APK installation completed successfully")
                    Log.i(TAG, "Installation completed successfully")
                } else {
                    print("❌ APK installation failed: $message")
                    Log.e(TAG, "Installation failed: $message")
                }
            }
        )

        if (!success) {
            val errorMsg = installationError ?: "Unknown installation error"
            throw IOException("Installation failed: $errorMsg")
        }

        if (!installationSuccess) {
            val errorMsg = installationError ?: "Installation callback reported failure"
            throw IOException("Installation failed: $errorMsg")
        }

        print("🎉 APK installation completed successfully!")
        print("Installed ${filteredApks.size} APK files:")
        filteredApks.forEach { file ->
            print("  ✅ ${file.name}")
        }
    }
}