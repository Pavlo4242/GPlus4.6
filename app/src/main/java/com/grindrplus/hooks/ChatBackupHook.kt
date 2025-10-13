package com.grindrplus.hooks

import com.grindrplus.GrindrPlus
import com.grindrplus.core.LogSource
import com.grindrplus.core.Logger
import com.grindrplus.persistence.model.ChatBackup
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.getObjectField

class ChatBackupHook : Hook(
    "Chat Backup",
    "Backs up chat messages to the GrindrPlus database."
) {
    private val chatMessagesDao = "com.grindrapp.android.persistence.dao.ChatMessagesDao"

    override fun init() {
        findClass(chatMessagesDao).hook("insertMessage", HookStage.AFTER) { param ->
            if (!isHookEnabled()) return@hook

            val message = param.arg<Any>(0)
            if (message == null) {
                Logger.w("ChatMessage object was null, cannot backup.", LogSource.HOOK)
                return@hook
            }

            try {
                val message_id = getObjectField(message, "messageId") as String
                val conversation_id = getObjectField(message, "conversationId") as String
                val sender = getObjectField(message, "senderId").toString()
                val body = getObjectField(message, "body") as? String ?: ""
                val timestamp = getObjectField(message, "timestamp") as Long
                val type = getObjectField(message, "type") as String

                val chatBackup = ChatBackup(
                    message_id = message_id,
                    conversation_id = conversation_id,
                    sender = sender,
                    body = body,
                    timestamp = timestamp,
                    type = type
                )

                GrindrPlus.executeAsync {
                    try {
                        GrindrPlus.database.chatBackupDao().insert(chatBackup)
                        Logger.d("Successfully backed up message $message_id", LogSource.HOOK)
                    } catch (e: Exception) {
                        Logger.e("Database error backing up message: ${e.message}", LogSource.HOOK)
                    }
                }
            } catch (e: Exception) {
                Logger.e("Failed to extract chat message: ${e.message}", LogSource.HOOK)
            }
        }
    }
}