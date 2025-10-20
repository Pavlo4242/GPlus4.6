package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "profile_photos",
    indices = [Index("profileId"), Index("photoHash"), Index("timestamp")]
)
data class ProfilePhotoHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val photoHash: String,
    val photoUrl: String?,
    val localPath: String?, // Permanent storage path
    val isPrimary: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val accountPackage: String
)
