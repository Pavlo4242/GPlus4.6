package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "permanent_media",
    indices = [Index("mediaHash"), Index("profileId"), Index("mediaType")]
)
data class PermanentMedia(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaHash: String, // MD5/SHA of content
    val originalUrl: String?,
    val localPath: String, // Permanent storage location
    val mediaType: String, // "photo", "album", "video"
    val profileId: String?,
    val albumId: Long?,
    val width: Int?,
    val height: Int?,
    val fileSize: Long,
    val savedTimestamp: Long = System.currentTimeMillis(),
    val accountPackage: String
)
