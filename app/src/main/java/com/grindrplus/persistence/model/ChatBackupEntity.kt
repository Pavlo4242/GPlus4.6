package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "archived_conversations", indices = [Index("conversationId")])
data class ArchivedConversationEntity(
    @PrimaryKey val conversationId: String,
    val name: String?,
    val lastMessageTimestamp: Long?
)

@Entity(tableName = "archived_chat_messages", indices = [Index("conversationId")])
data class ArchivedChatMessageEntity(
    @PrimaryKey val messageId: String,
    val conversationId: String,
    val senderId: String?,
    val timestamp: Long?,
    val body: String? // Storing the raw JSON body
)