package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "profile_account_mapping",
    indices = [Index("profileId"), Index("accountPackage")]
)
data class ProfileAccountMapping(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val accountPackage: String,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val interactionCount: Int = 0
)
