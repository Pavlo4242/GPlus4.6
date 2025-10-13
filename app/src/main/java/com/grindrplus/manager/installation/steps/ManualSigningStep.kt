package com.grindrplus.manager.installation.steps

import android.content.Context
import android.widget.Toast
import com.grindrplus.manager.installation.BaseStep
import com.grindrplus.manager.installation.Print
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ManualSigningStep(
    private val inputDir: File,
    private val keyStore: File,
    private val keyAlias: String = "Alias",
    private val keyPassword: String = "password"

) : BaseStep() {
    override val name = "Manual APK Signing"

    override suspend fun doExecute(context: Context, print: Print) {
        val baseApk = File(inputDir, "base.apk")
        if (!baseApk.exists()) {
            throw IOException("base.apk not found for signing")
        }

        // Wait for user confirmation
        print("⏸️ PAUSED: Please sign the APK manually")
        print("APK location: ${baseApk.absolutePath}")
        print("Using keystore: ${keyStore.name}")
        print("Press OK when signing is complete...")

        // Wait for user to manually sign and confirm
        waitForUserConfirmation(context, baseApk.absolutePath)

        if (!baseApk.exists()) {
            throw IOException("APK was deleted during manual signing")
        }

        print("✅ APK ready for LSPatch - assuming signing completed")
    }

    private suspend fun waitForUserConfirmation(context: Context, apkPath: String) {
        val channel = Channel<Boolean>()

        withContext(Dispatchers.Main) {
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("Manual Signing Required")
                .setMessage("Please sign the APK using:\n\njarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore \"${keyStore.absolutePath}\" \"$apkPath\" \"$keyAlias\"\n\nOR use apksigner:\n\napksigner sign --ks \"${keyStore.absolutePath}\" --ks-key-alias \"$keyAlias\" \"$apkPath\"\n\nPress OK when done.")
                .setPositiveButton("OK") { _, _ ->
                    channel.trySend(true)
                }
                .setCancelable(false)
                .show()
        }

        channel.receive()
        channel.close()
    }
}