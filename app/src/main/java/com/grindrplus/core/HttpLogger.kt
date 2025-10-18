/*
package com.grindrplus.core

import android.os.Environment
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HttpLogger {
    private val logFile = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "GrindrPlus_HttpLogs.txt"
    )

    private fun formatHeaders(headers: okhttp3.Headers): String {
        return headers.toMultimap().entries.joinToString("\n") { (key, values) ->
            "     $key: ${values.joinToString(", ")}"
        }
    }

    fun log(request: Request, response: Response) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logMessage = buildString {
                append("[$timestamp]\n")
                append("--- REQUEST -->\n")
                append("  ${request.method} ${request.url}\n")
                append("  Headers:\n")
                append(formatHeaders(request.headers))
                append("\n\n")
                append("<-- RESPONSE ---\n")
                append("  ${response.code} ${response.message}\n")
                append("  Headers:\n")
                append(formatHeaders(response.headers))
                append("\n----------------------------------------\n\n")
            }

            logFile.appendText(logMessage)
        } catch (e: Exception) {
            Logger.e("Failed to write to HTTP log file: ${e.message}")
        }
    }
}*/
