package com.grindrplus.utils

import android.graphics.BitmapFactory
import android.os.Environment
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Logger
import com.grindrplus.persistence.model.PermanentMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object MediaDehasher {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Permanent storage directory (outside app data)
    private val permanentStorageDir: File by lazy {
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        File(publicDir, "GrindrPlus_Archive").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Download and permanently store media with metadata
     */
    suspend fun archiveMedia(
        originalUrl: String,
        profileId: String? = null,
        albumId: Long? = null,
        mediaType: String = "photo"
    ): Result<PermanentMedia> = withContext(Dispatchers.IO) {
        runCatching {
            // Download media
            val mediaData = downloadMedia(originalUrl)
            
            // Calculate hash
            val hash = calculateHash(mediaData)
            
            // Check if already archived
            val dao = GrindrPlus.database.permanentMediaDao()
            dao.findByHash(hash)?.let { return@runCatching it }
            
            // Determine file extension
            val extension = getExtensionFromUrl(originalUrl) ?: "jpg"
            
            // Create organized directory structure
            val typeDir = File(permanentStorageDir, mediaType)
            if (!typeDir.exists()) typeDir.mkdirs()
            
            val profileDir = if (profileId != null) {
                File(typeDir, profileId).apply { if (!exists()) mkdirs() }
            } else {
                typeDir
            }
            
            // Save file
            val filename = "${hash.take(16)}_${System.currentTimeMillis()}.$extension"
            val file = File(profileDir, filename)
            FileOutputStream(file).use { it.write(mediaData) }
            
            // Get image dimensions if it's a photo
            var width: Int? = null
            var height: Int? = null
            if (mediaType == "photo") {
                try {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    width = options.outWidth
                    height = options.outHeight
                } catch (e: Exception) {
                    Logger.e("Failed to get image dimensions: ${e.message}")
                }
            }
            
            // Create database entry
            val permanentMedia = PermanentMedia(
                mediaHash = hash,
                originalUrl = originalUrl,
                localPath = file.absolutePath,
                mediaType = mediaType,
                profileId = profileId,
                albumId = albumId,
                width = width,
                height = height,
                fileSize = mediaData.size.toLong(),
                accountPackage = GrindrPlus.packageName
            )
            
            dao.insertMedia(permanentMedia)
            Logger.i("Archived $mediaType to: ${file.absolutePath}")
            
            permanentMedia
        }
    }

    /**
     * Batch archive all photos from a profile
     */
    suspend fun archiveProfilePhotos(profileId: String): Result<List<PermanentMedia>> = 
        withContext(Dispatchers.IO) {
            runCatching {
                val photos = mutableListOf<PermanentMedia>()
                
                // Get all photos for this profile from Grindr's database
                val profilePhotos = com.grindrplus.core.DatabaseHelper.query(
                    "SELECT photo_url, photo_hash FROM profile_photo WHERE profile_id = ?",
                    arrayOf(profileId)
                )
                
                profilePhotos.forEach { row ->
                    val photoUrl = row["photo_url"] as? String
                    val photoHash = row["photo_hash"] as? String
                    
                    if (photoUrl != null) {
                        try {
                            val archived = archiveMedia(
                                originalUrl = photoUrl,
                                profileId = profileId,
                                mediaType = "photo"
                            ).getOrNull()
                            
                            if (archived != null) {
                                photos.add(archived)
                                
                                // Update photo history with local path
                                updatePhotoHistoryPath(profileId, photoHash ?: "", archived.localPath)
                            }
                        } catch (e: Exception) {
                            Logger.e("Failed to archive photo from $photoUrl: ${e.message}")
                        }
                    }
                }
                
                photos
            }
        }

    /**
     * Archive an entire album
     */
    suspend fun archiveAlbum(albumId: Long): Result<List<PermanentMedia>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val media = mutableListOf<PermanentMedia>()
                
                val album = GrindrPlus.database.albumDao().getAlbum(albumId)
                val content = GrindrPlus.database.albumDao().getAlbumContent(albumId)
                
                content.forEach { contentItem ->
                    val url = contentItem.url
                    if (!url.isNullOrEmpty() && url.startsWith("http")) {
                        try {
                            val mediaType = if (contentItem.contentType?.contains("video") == true) {
                                "video"
                            } else {
                                "photo"
                            }
                            
                            val archived = archiveMedia(
                                originalUrl = url,
                                profileId = album?.profileId?.toString(),
                                albumId = albumId,
                                mediaType = mediaType
                            ).getOrNull()
                            
                            if (archived != null) {
                                media.add(archived)
                            }
                        } catch (e: Exception) {
                            Logger.e("Failed to archive album content: ${e.message}")
                        }
                    }
                }
                
                media
            }
        }

    /**
     * Get archive statistics
     */
    suspend fun getArchiveStats(): ArchiveStats = withContext(Dispatchers.IO) {
        val dao = GrindrPlus.database.permanentMediaDao()
        ArchiveStats(
            totalFiles = dao.getTotalMediaCount(),
            totalSize = dao.getTotalStorageUsed() ?: 0L,
            storageLocation = permanentStorageDir.absolutePath
        )
    }

    private suspend fun downloadMedia(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Download failed: ${response.code}")
            }
            response.body?.bytes() ?: throw Exception("Empty response body")
        }
    }

    private fun calculateHash(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun getExtensionFromUrl(url: String): String? {
        return url.substringAfterLast('.', "")
            .substringBefore('?')
            .takeIf { it.length in 2..5 }
    }

    private suspend fun updatePhotoHistoryPath(profileId: String, photoHash: String, localPath: String) {
        try {
            // Update the profile_photos table with the permanent local path
            com.grindrplus.core.DatabaseHelper.execute(
                """
                UPDATE profile_photos 
                SET localPath = '$localPath' 
                WHERE profileId = '$profileId' AND photoHash = '$photoHash'
                """.trimIndent()
            )
        } catch (e: Exception) {
            Logger.e("Failed to update photo history path: ${e.message}")
        }
    }

    data class ArchiveStats(
        val totalFiles: Int,
        val totalSize: Long,
        val storageLocation: String
    )
}
