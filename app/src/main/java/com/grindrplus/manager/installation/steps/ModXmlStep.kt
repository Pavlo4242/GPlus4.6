package com.grindrplus.manager.installation.steps

import android.Manifest
import android.content.Context
import android.util.Log
import com.grindrplus.manager.installation.BaseStep
import com.grindrplus.manager.installation.Print
import pxb.android.axml.AxmlReader
import pxb.android.axml.AxmlVisitor
import pxb.android.axml.AxmlWriter
import pxb.android.axml.NodeVisitor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val TYPE_STRING = 3
private const val TYPE_INT_BOOLEAN = 18

class ModXMLStep(
    private val unzipFolder: File,
    private val outputDir: File
) : BaseStep() {
    override val name = "Modifying APK XML Resources (AXML)"

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val DEBUGGABLE = "debuggable"
        const val USES_CLEARTEXT_TRAFFIC = "usesCleartextTraffic"
        const val REQUEST_LEGACY_EXTERNAL_STORAGE = "requestLegacyExternalStorage"
        const val FILE_PROVIDER_PATHS_RESOURCE_NAME = "file_paths_grindrplus"
        const val TAG = "ModXMLStep"
    }

    private class PassThroughVisitor(nv: NodeVisitor) : NodeVisitor(nv) {
        override fun child(ns: String?, name: String?): NodeVisitor {
            return PassThroughVisitor(super.child(ns, name))
        }
    }

    override suspend fun doExecute(context: Context, print: Print) {
        print("Starting targeted XML resource modification for base.apk using AXML framework...")

        outputDir.listFiles()?.forEach {
            if (it.isFile) {
                print("Deleting existing file: ${it.name}")
                it.delete()
            }
        }

        val apkFilesToProcess = unzipFolder.listFiles()?.filter { it.name.endsWith(".apk") && it.exists() && it.length() > 0 }
        if (apkFilesToProcess.isNullOrEmpty()) {
            throw IOException("No APK files found in unzip directory to modify.")
        }

        print("Found ${apkFilesToProcess.size} APK files to process")

        val baseApk = findBaseApkWithResources(apkFilesToProcess, print)
        print("Selected ${baseApk.name} for XML patching (${baseApk.length()} bytes)")

        val outputFile = File(outputDir, baseApk.name)

        try {
            modifyApk(baseApk, outputFile, print)
            print("✅ XML modification completed successfully")
            print("Output file: ${outputFile.name} (${outputFile.length()} bytes)")
        } catch (e: Exception) {
            print("❌ XML modification failed: ${e.message}")
            Log.e(TAG, "XML modification failed", e)
            throw e
        }
    }

    private fun findBaseApkWithResources(apkFiles: List<File>, print: Print): File {
        val baseApk = apkFiles.find { it.name == "base.apk" }
        if (baseApk != null && containsManifestAndResources(baseApk)) {
            print("Found base.apk with resources.")
            return baseApk
        }

        return apkFiles.firstOrNull { containsManifestAndResources(it) }
            ?: apkFiles.firstOrNull { containsManifest(it) }
            ?: throw IOException("No suitable APK with AndroidManifest.xml found.")
    }

    private fun containsManifest(apkFile: File): Boolean {
        try {
            ZipInputStream(apkFile.inputStream().buffered()).use { zis ->
                return generateSequence { zis.nextEntry }.any { it.name == "AndroidManifest.xml" }
            }
        } catch (e: Exception) { return false }
    }

    private fun containsManifestAndResources(apkFile: File): Boolean {
        try {
            var hasManifest = false
            var hasResources = false
            ZipInputStream(apkFile.inputStream().buffered()).use { zis ->
                generateSequence { zis.nextEntry }.forEach { entry ->
                    when {
                        entry.name == "AndroidManifest.xml" -> hasManifest = true
                        entry.name.startsWith("res/xml/") -> hasResources = true
                    }
                }
            }
            return hasManifest && hasResources
        } catch (e: Exception) { return false }
    }

    private fun modifyApk(inputFile: File, outputFile: File, print: Print) {
        var manifestBytes: ByteArray? = null
        print("Step 1: Extracting AndroidManifest.xml from ${inputFile.name}")
        ZipInputStream(inputFile.inputStream().buffered()).use { zis ->
            generateSequence { zis.nextEntry }.find { it.name == "AndroidManifest.xml" }?.let {
                print("  Found AndroidManifest.xml, extracting...")
                val baos = ByteArrayOutputStream()
                zis.copyTo(baos)
                manifestBytes = baos.toByteArray()
                print("  Extracted ${manifestBytes!!.size} bytes")
            }
        }

        if (manifestBytes == null) throw IOException("AndroidManifest.xml not found in ${inputFile.name}")

        val packageName = getPackageName(manifestBytes!!)
        print("Step 2: Found package name: $packageName")

        print("Step 3: Patching AndroidManifest.xml")
        val patchedManifestBytes = patchAndroidManifest(manifestBytes!!, packageName, print)
        print("  Patched manifest: ${patchedManifestBytes.size} bytes")

        val broadAccessPathsXml = """<?xml version="1.0" encoding="utf-8"?><paths><root-path name="root" path="."/><files-path name="all_files" path="/"/><cache-path name="all_cache" path="/"/><external-files-path name="all_external_files" path="/"/><external-cache-path name="all_external_cache" path="/"/><external-path name="all_external" path="/"/></paths>""".toByteArray()
        val filesToOverwriteOrAdd = mapOf(
            "res/xml/library_file_paths.xml" to broadAccessPathsXml,
            "res/xml/provider_paths.xml" to broadAccessPathsXml,
            "res/xml/$FILE_PROVIDER_PATHS_RESOURCE_NAME.xml" to broadAccessPathsXml
        )

        print("Step 4: Creating modified APK...")
        ZipOutputStream(outputFile.outputStream().buffered()).use { zos ->
            val processedEntries = mutableSetOf<String>()

            ZipInputStream(inputFile.inputStream().buffered()).use { zis ->
                generateSequence { zis.nextEntry }.forEach { entry ->
                    val entryName = entry.name
                    processedEntries.add(entryName)

                    when {
                        entryName == "AndroidManifest.xml" -> {
                            zos.putNextEntry(ZipEntry(entryName))
                            zos.write(patchedManifestBytes)
                        }
                        filesToOverwriteOrAdd.containsKey(entryName) -> {
                            zos.putNextEntry(ZipEntry(entryName))
                            zos.write(filesToOverwriteOrAdd[entryName]!!)
                        }
                        else -> {
                            zos.putNextEntry(ZipEntry(entry.name))
                            zis.copyTo(zos)
                        }
                    }
                    zos.closeEntry()
                }
            }

            filesToOverwriteOrAdd.forEach { (path, bytes) ->
                if (!processedEntries.contains(path)) {
                    print("  Adding new file: $path (${bytes.size} bytes)")
                    zos.putNextEntry(ZipEntry(path))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }
    }

    private fun getPackageName(manifestBytes: ByteArray): String {
        var packageName = ""
        AxmlReader(manifestBytes).accept(object : AxmlVisitor() {
            override fun child(ns: String?, name: String?) = if (name == "manifest") object : NodeVisitor() {
                override fun attr(ns: String?, name: String?, resourceId: Int, type: Int, value: Any?) {
                    if (name == "package") packageName = value as String
                }
            } else super.child(ns, name)
        })
        return packageName
    }

    private fun patchAndroidManifest(manifestBytes: ByteArray, packageName: String, print: Print): ByteArray {
        val writer = AxmlWriter()
        val modifications = mutableListOf<String>()

        AxmlReader(manifestBytes).accept(object : AxmlVisitor(writer) {
            override fun child(ns: String?, name: String?): NodeVisitor {
                val manifestNode = super.child(ns, name)
                return if (name == "manifest") {
                    object : NodeVisitor(manifestNode) {
                        override fun child(ns: String?, name: String): NodeVisitor {
                            val childVisitor = super.child(ns, name)
                            return when (name) {
                                "application" -> createApplicationNodeVisitor(childVisitor, packageName, modifications)
                                else -> PassThroughVisitor(childVisitor)
                            }
                        }

                        override fun end() {
                            val permVisitor = super.child(null, "uses-permission")
                            permVisitor.attr(ANDROID_NAMESPACE, "name", -1, TYPE_STRING, Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                            permVisitor.end()
                            modifications.add("Added MANAGE_EXTERNAL_STORAGE permission")
                            super.end()
                        }
                    }
                } else {
                    manifestNode
                }
            }
        })

        print("Manifest modifications applied:")
        modifications.distinct().forEach { mod -> print("  - $mod") }
        return writer.toByteArray()
    }

    private fun createApplicationNodeVisitor(parentVisitor: NodeVisitor, packageName: String, modifications: MutableList<String>): NodeVisitor {
        return object : NodeVisitor(parentVisitor) {
            override fun child(ns: String?, name: String): NodeVisitor {
                val childNv = super.child(ns, name)
                if (name == "provider") {
                    return createProviderNodeVisitor(childNv, packageName, modifications)
                }
                return childNv
            }

            override fun end() {
                attr(ANDROID_NAMESPACE, DEBUGGABLE, -1, TYPE_INT_BOOLEAN, 1)
                attr(ANDROID_NAMESPACE, USES_CLEARTEXT_TRAFFIC, -1, TYPE_INT_BOOLEAN, 1)
                attr(ANDROID_NAMESPACE, REQUEST_LEGACY_EXTERNAL_STORAGE, -1, TYPE_INT_BOOLEAN, 1)
                modifications.add("Set application attributes: debuggable, usesCleartextTraffic, requestLegacyExternalStorage to true")
                super.end()
            }
        }
    }

    private fun createProviderNodeVisitor(parentVisitor: NodeVisitor, packageName: String, modifications: MutableList<String>): NodeVisitor {
        return object : NodeVisitor(parentVisitor) {
            var providerName: String? = null
            var isTargetProvider = false

            override fun attr(ns: String?, name: String, resourceId: Int, type: Int, value: Any?) {
                if (name == "name") {
                    providerName = value as? String
                    isTargetProvider = providerName in listOf("androidx.core.content.FileProvider", "com.canhub.cropper.CropFileProvider")
                }

                val finalValue = if (name == "authorities" && isTargetProvider) {
                    val newValue = (value as String).replace(packageName, "$packageName.grindrplus")
                    modifications.add("Updated provider authorities for $providerName: $newValue")
                    newValue
                } else {
                    value
                }
                super.attr(ns, name, resourceId, type, finalValue)
            }

            override fun child(ns: String?, name: String): NodeVisitor {
                val metaNv = super.child(ns, name)
                if (name == "meta-data" && isTargetProvider) {
                    return createMetaDataNodeVisitor(metaNv, modifications)
                }
                return metaNv
            }

            override fun end() {
                if (isTargetProvider) {
                    attr(ANDROID_NAMESPACE, "exported", -1, TYPE_INT_BOOLEAN, 0)
                    attr(ANDROID_NAMESPACE, "grantUriPermissions", -1, TYPE_INT_BOOLEAN, 1)
                    modifications.add("Ensured $providerName is not exported and grants URI permissions")
                }
                super.end()
            }
        }
    }

    private fun createMetaDataNodeVisitor(parentVisitor: NodeVisitor, modifications: MutableList<String>): NodeVisitor {
        return object : NodeVisitor(parentVisitor) {
            val attributes = mutableListOf<Triple<Triple<String?, String, Int>, Int, Any?>>()
            var isFilePathsTag = false

            override fun attr(ns: String?, name: String, resourceId: Int, type: Int, value: Any?) {
                attributes.add(Triple(Triple(ns, name, resourceId), type, value))
                if (name == "name" && value == "android.support.FILE_PROVIDER_PATHS") {
                    isFilePathsTag = true
                }
            }

            override fun end() {
                for (attr in attributes) {
                    val (attrInfo, type, value) = attr
                    val (ns, name, resId) = attrInfo

                    // *** FIX IS HERE ***
                    // If this is the resource attribute we want to change, modify its value and type before writing it.
                    if (isFilePathsTag && name == "resource") {
                        super.attr(ns, name, resId, TYPE_STRING, "@xml/$FILE_PROVIDER_PATHS_RESOURCE_NAME")
                        modifications.add("Updated FILE_PROVIDER_PATHS resource")
                    } else {
                        // Otherwise, write the attribute as it was.
                        super.attr(ns, name, resId, type, value)
                    }
                }
                super.end()
            }
        }
    }
}