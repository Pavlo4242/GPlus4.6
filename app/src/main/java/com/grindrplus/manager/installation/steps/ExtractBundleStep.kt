package com.grindrplus.manager.installation.steps

import android.content.Context
import com.grindrplus.manager.installation.BaseStep
import com.grindrplus.manager.installation.Print
import com.grindrplus.manager.utils.unzip
import java.io.File
import java.io.IOException

// 3rd
class ExtractBundleStep(
private val bundleFile: File,
private val unzipFolder: File
) : BaseStep() {
    override val name = "Extracting Bundle"

    override suspend fun doExecute(context: Context, print: Print) {
        try {
            print("Cleaning extraction directory...")
            unzipFolder.deleteRecursively() // Completely clean
            unzipFolder.mkdirs()

            print("Extracting from: ${bundleFile.absolutePath}")
            print("Bundle file exists: ${bundleFile.exists()}")
            print("Bundle file size: ${bundleFile.length()} bytes")

            if (!bundleFile.exists() || bundleFile.length() == 0L) {
                throw IOException("Bundle file is missing or empty")
            }

            bundleFile.unzip(unzipFolder)

            // Debug extracted contents
            val extractedFiles = unzipFolder.walk().toList()
            print("Extracted ${extractedFiles.size} files/folders:")
            extractedFiles.forEach { file ->
                val type = if (file.isDirectory) "DIR" else "FILE"
                print("  $type: ${file.relativeTo(unzipFolder)} (${file.length()} bytes)")
            }

            val apkFiles = unzipFolder.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }
                ?: emptyList()

            if (apkFiles.isEmpty()) {
                throw IOException("No APK files found after extraction")
            }

            print("Successfully extracted ${apkFiles.size} APK files")
            apkFiles.forEachIndexed { index, file ->
                print("  ${index + 1}. ${file.name} (${file.length() / 1024}KB)")
            }
        } catch (e: Exception) {
            throw IOException("Failed to extract bundle: ${e.message}", e)
        }
    }
}