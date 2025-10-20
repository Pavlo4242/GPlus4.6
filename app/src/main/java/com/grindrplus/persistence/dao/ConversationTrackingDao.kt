package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.grindrplus.persistence.model.ConversationMetadata
import com.grindrplus.persistence.model.PermanentMedia
import com.grindrplus.persistence.model.SavedPhraseEntity

@Dao
interface ConversationTrackingDao {
    /**
     * Upsert conversation metadata
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationMetadata)

    /**
     * Get all conversations for an account
     */
    @Query("SELECT * FROM conversation_metadata WHERE accountPackage = :accountPackage ORDER BY lastMessageTimestamp DESC")
    suspend fun getConversations(accountPackage: String): List<ConversationMetadata>

    /**
     * Get conversation by ID
     */
    @Query("SELECT * FROM conversation_metadata WHERE conversationId = :conversationId")
    suspend fun getConversation(conversationId: String): ConversationMetadata?

    /**
     * Update message count
     */
    @Query("UPDATE conversation_metadata SET messageCount = messageCount + 1, lastMessageTimestamp = :timestamp WHERE conversationId = :conversationId")
    suspend fun incrementMessageCount(conversationId: String, timestamp: Long)

    /**
     * Get all conversations with a specific profile across all accounts
     */
    @Query("SELECT * FROM conversation_metadata WHERE profileId = :profileId")
    suspend fun getConversationsForProfile(profileId: String): List<ConversationMetadata>
}