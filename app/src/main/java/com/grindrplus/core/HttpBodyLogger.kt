package com.grindrplus.core

import com.grindrplus.GrindrPlus
import com.grindrplus.persistence.model.HttpBodyLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object HttpBodyLogger {

    private val scope = CoroutineScope(Dispatchers.IO)

    fun log(url: String, method: String, body: String?) {
        if (body.isNullOrEmpty() || !body.trim().startsWith("{")) return

        scope.launch {
            try {
                val logEntry = HttpBodyLogEntity(
                    timestamp = System.currentTimeMillis() / 1000,
                    url = url,
                    method = method,
                    response_body = body
                )
                GrindrPlus.database.httpBodyLogDao().insert(logEntry)
            } catch (e: Exception) {
                Logger.e("Failed to write to HTTP body log database: ${e.message}")
            }
        }
    }
}