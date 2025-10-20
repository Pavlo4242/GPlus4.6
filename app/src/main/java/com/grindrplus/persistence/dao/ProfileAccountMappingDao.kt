package com.grindrplus.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.grindrplus.persistence.model.PermanentMedia
import com.grindrplus.persistence.model.ProfileAccountMapping
import com.grindrplus.persistence.model.SavedPhraseEntity

@Dao
interface ProfileAccountMappingDao {
    /**
     * Record that an account has seen a profile
     */
    @Transaction
    suspend fun recordProfileSighting(profileId: String, accountPackage: String) {
        val existing = getMapping(profileId, accountPackage)
        if (existing != null) {
            updateLastSeen(profileId, accountPackage, System.currentTimeMillis())
            incrementInteractionCount(profileId, accountPackage)
        } else {
            insertMapping(
                ProfileAccountMapping(
                    profileId = profileId,
                    accountPackage = accountPackage,
                    interactionCount = 1
                )
            )
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMapping(mapping: ProfileAccountMapping)

    @Query("SELECT * FROM profile_account_mapping WHERE profileId = :profileId AND accountPackage = :accountPackage")
    suspend fun getMapping(profileId: String, accountPackage: String): ProfileAccountMapping?

    @Query("UPDATE profile_account_mapping SET lastSeen = :timestamp WHERE profileId = :profileId AND accountPackage = :accountPackage")
    suspend fun updateLastSeen(profileId: String, accountPackage: String, timestamp: Long)

    @Query("UPDATE profile_account_mapping SET interactionCount = interactionCount + 1 WHERE profileId = :profileId AND accountPackage = :accountPackage")
    suspend fun incrementInteractionCount(profileId: String, accountPackage: String)

    /**
     * Get all accounts that have seen a profile
     */
    @Query("SELECT * FROM profile_account_mapping WHERE profileId = :profileId")
    suspend fun getAccountsForProfile(profileId: String): List<ProfileAccountMapping>

    /**
     * Get all profiles seen by an account
     */
    @Query("SELECT * FROM profile_account_mapping WHERE accountPackage = :accountPackage ORDER BY lastSeen DESC")
    suspend fun getProfilesForAccount(accountPackage: String): List<ProfileAccountMapping>
}