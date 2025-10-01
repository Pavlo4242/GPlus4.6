package com.grindrplus.hooks

import com.grindrplus.GrindrPlus
import com.grindrplus.core.DatabaseHelper
import com.grindrplus.core.loge
import com.grindrplus.core.logi
import com.grindrplus.persistence.model.ArchivedChatMessageEntity
import com.grindrplus.persistence.model.ArchivedConversationEntity
import com.grindrplus.utils.Hook
import kotlin.collections.mapNotNull

class ChatBackup : Hook(
    "Enable Chat Backup",
    "Automatically backs up Grindr chats to the GrindrPlus database."
) {
    override fun init() {
        if (!isHookEnabled()) return

        GrindrPlus.executeAsync {
            try {
                logi("Starting chat backup process...")
                val dao = GrindrPlus.database.chatBackupDao()

                // Backup conversations
                val conversations = DatabaseHelper.query("SELECT * FROM chat_conversations")
                val conversationEntities = conversations.mapNotNull {
                    try {
                        ArchivedConversationEntity(
                            conversationId = it["conversation_id"] as String,
                            name = it["name"] as? String,
                            lastMessageTimestamp = (it["last_message_timestamp"] as? Long)
                        )
                    } catch (e: Exception) {
                        loge("Failed to map conversation row: $it. Error: ${e.message}")
                        null
                    }
                }
                dao.upsertConversations(conversationEntities)
                logi("Backed up ${conversationEntities.size} conversations.")

                // Backup messages
                val messages = DatabaseHelper.query("SELECT * FROM chat_messages")
                val messageEntities = messages.mapNotNull {
                    try {
                        ArchivedChatMessageEntity(
                            messageId = it["message_id"] as String,
                            conversationId = it["conversation_id"] as String,
                            senderId = it["sender_id"] as? String,
                            timestamp = (it["timestamp"] as? Long),
                            body = it["body"] as? String
                        )
                    } catch (e: Exception) {
                        loge("Failed to map message row: $it. Error: ${e.message}")
                        null
                    }
                }
                dao.upsertMessages(messageEntities)
                logi("Backed up ${messageEntities.size} messages.")

                logi("Chat backup process completed successfully.")
            } catch (e: Exception) {
                loge("Chat backup process failed: ${e.message}")
            }
        }
    }
}