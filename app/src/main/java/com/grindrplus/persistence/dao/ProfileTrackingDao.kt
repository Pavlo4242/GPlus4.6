package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grindrplus.persistence.model.ProfilePhotoHistory
import com.grindrplus.persistence.model.ProfileSnapshot


@Dao
interface ProfileTrackingDao {
    /**
     * Insert a profile snapshot
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: ProfileSnapshot): Long

    /**
     * Get all snapshots for a profile
     */
    @Query("SELECT * FROM profile_snapshots WHERE profileId = :profileId ORDER BY timestamp DESC")
    suspend fun getProfileHistory(profileId: String): List<ProfileSnapshot>

    /**
     * Get latest snapshot for a profile
     */
    @Query("SELECT * FROM profile_snapshots WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSnapshot(profileId: String): ProfileSnapshot?

    /**
     * Detect profile changes between snapshots
     */
    @Query("""
        SELECT * FROM profile_snapshots 
        WHERE profileId = :profileId 
        AND timestamp > :sinceTimestamp 
        ORDER BY timestamp ASC
    """)
    suspend fun getChanges(profileId: String, sinceTimestamp: Long): List<ProfileSnapshot>

    /**
     * Insert photo history entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotoHistory(photo: ProfilePhotoHistory): Long

    /**
     * Get photo history for a profile
     */
    @Query("SELECT * FROM profile_photos WHERE profileId = :profileId ORDER BY timestamp DESC")
    suspend fun getPhotoHistory(profileId: String): List<ProfilePhotoHistory>

    /**
     * Find profiles that have used a specific photo
     */
    @Query("SELECT DISTINCT profileId FROM profile_photos WHERE photoHash = :photoHash")
    suspend fun findProfilesWithPhoto(photoHash: String): List<String>
}