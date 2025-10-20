package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversation_metadata",
    indices = [Index("conversationId"), Index("profileId"), Index("accountPackage")]
)
data class ConversationMetadata(
    @PrimaryKey val conversationId: String,
    val profileId: String,
    val displayName: String?,
    val lastMessageTimestamp: Long?,
    val firstMessageTimestamp: Long?,
    val messageCount: Int = 0,
    val isFavorite: Boolean = false,
    val isBlocked: Boolean = false,
    val accountPackage: String
)