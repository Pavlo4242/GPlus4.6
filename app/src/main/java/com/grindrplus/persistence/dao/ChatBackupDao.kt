package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Upsert
import com.grindrplus.persistence.model.ArchivedChatMessageEntity
import com.grindrplus.persistence.model.ArchivedConversationEntity

@Dao
interface ChatBackupDao {
    @Upsert
    suspend fun upsertConversations(conversations: List<ArchivedConversationEntity>)

    @Upsert
    suspend fun upsertMessages(messages: List<ArchivedChatMessageEntity>)
}