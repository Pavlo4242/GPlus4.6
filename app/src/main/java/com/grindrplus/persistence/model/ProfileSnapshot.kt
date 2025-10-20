package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "profile_snapshots",
    indices = [Index("profileId"), Index("timestamp")]
)
data class ProfileSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val displayName: String?,
    val aboutMe: String?,
    val headline: String?,
    val age: Int?,
    val height: String?,
    val weight: String?,
    val bodyType: String?,
    val ethnicity: String?,
    val tribes: String?, // JSON array as string
    val lookingFor: String?, // JSON array as string
    val timestamp: Long = System.currentTimeMillis(),
    val accountPackage: String // Which account saw this
)