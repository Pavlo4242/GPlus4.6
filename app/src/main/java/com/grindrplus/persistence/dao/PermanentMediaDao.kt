package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.grindrplus.persistence.model.PermanentMedia
import com.grindrplus.persistence.model.SavedPhraseEntity

@Dao
interface PermanentMediaDao {
    /**
     * Save media permanently
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: PermanentMedia): Long

    /**
     * Check if media already exists
     */
    @Query("SELECT * FROM permanent_media WHERE mediaHash = :hash")
    suspend fun findByHash(hash: String): PermanentMedia?

    /**
     * Get all media for a profile
     */
    @Query("SELECT * FROM permanent_media WHERE profileId = :profileId ORDER BY savedTimestamp DESC")
    suspend fun getProfileMedia(profileId: String): List<PermanentMedia>

    /**
     * Get all media for an album
     */
    @Query("SELECT * FROM permanent_media WHERE albumId = :albumId ORDER BY savedTimestamp DESC")
    suspend fun getAlbumMedia(albumId: Long): List<PermanentMedia>

    /**
     * Get media by type
     */
    @Query("SELECT * FROM permanent_media WHERE mediaType = :type ORDER BY savedTimestamp DESC")
    suspend fun getMediaByType(type: String): List<PermanentMedia>

    /**
     * Get storage statistics
     */
    @Query("SELECT SUM(fileSize) FROM permanent_media")
    suspend fun getTotalStorageUsed(): Long?

    @Query("SELECT COUNT(*) FROM permanent_media")
    suspend fun getTotalMediaCount(): Int
}