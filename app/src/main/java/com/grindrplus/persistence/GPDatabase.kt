package com.grindrplus.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.grindrplus.persistence.converters.DateConverter
import com.grindrplus.persistence.converters.ListConverter
import com.grindrplus.persistence.dao.*
import com.grindrplus.persistence.model.*

@Database(
    entities = [
        // Existing entities
        AlbumEntity::class,
        AlbumContentEntity::class,
        TeleportLocationEntity::class,
        SavedPhraseEntity::class,
        ViewedSummary::class,
        ViewedProfile::class,
        MediaItem::class,
        ArchivedConversationEntity::class,
        ArchivedChatMessageEntity::class,
        ChatBackup::class,
        ConversationBackup::class,
        ParticipantBackup::class,
        HttpBodyLogEntity::class,
        // New tracking entities
        ProfileSnapshot::class,
        ProfilePhotoHistory::class,
        ConversationMetadata::class,
        ProfileAccountMapping::class,
        PermanentMedia::class
    ],
    version = 3, // Increment version
    exportSchema = false
)
@TypeConverters(DateConverter::class, ListConverter::class)
abstract class GPDatabase : RoomDatabase() {
    // Existing DAOs
    abstract fun albumDao(): AlbumDao
    abstract fun teleportLocationDao(): TeleportLocationDao
    abstract fun savedPhraseDao(): SavedPhraseDao
    abstract fun chatBackupDao(): ChatBackupDao
    abstract fun httpBodyLogDao(): HttpBodyLogDao

    // New tracking DAOs
    abstract fun profileTrackingDao(): ProfileTrackingDao
    abstract fun conversationTrackingDao(): ConversationTrackingDao
    abstract fun profileAccountMappingDao(): ProfileAccountMappingDao
    abstract fun permanentMediaDao(): PermanentMediaDao

    companion object {
        private const val DATABASE_NAME = "grindrplus.db"

        @Volatile
        private var INSTANCE: GPDatabase? = null

        fun create(context: Context): GPDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context,
                    GPDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `http_body_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `url` TEXT NOT NULL,
                        `method` TEXT NOT NULL,
                        `response_body` TEXT
                    )
                    """
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Profile snapshots
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `profile_snapshots` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `profileId` TEXT NOT NULL,
                        `displayName` TEXT,
                        `aboutMe` TEXT,
                        `headline` TEXT,
                        `age` INTEGER,
                        `height` TEXT,
                        `weight` TEXT,
                        `bodyType` TEXT,
                        `ethnicity` TEXT,
                        `tribes` TEXT,
                        `lookingFor` TEXT,
                        `timestamp` INTEGER NOT NULL,
                        `accountPackage` TEXT NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_profile_snapshots_profileId` ON `profile_snapshots` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_profile_snapshots_timestamp` ON `profile_snapshots` (`timestamp`)")

                // Profile photo history
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `profile_photos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `profileId` TEXT NOT NULL,
                        `photoHash` TEXT NOT NULL,
                        `photoUrl` TEXT,
                        `localPath` TEXT,
                        `isPrimary` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `accountPackage` TEXT NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_profile_photos_profileId` ON `profile_photos` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_profile_photos_photoHash` ON `profile_photos` (`photoHash`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_profile_photos_timestamp` ON `profile_photos` (`timestamp`)")

                // Conversation metadata
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `conversation_metadata` (
                        `conversationId` TEXT PRIMARY KEY NOT NULL,
                        `profileId` TEXT NOT NULL,
                        `displayName` TEXT,
                        `lastMessageTimestamp` INTEGER,
                        `firstMessageTimestamp` INTEGER,
                        `messageCount` INTEGER NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `isBlocked` INTEGER NOT NULL,
                        `accountPackage` TEXT NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_metadata_conversationId` ON `conversation_metadata` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_metadata_profileId` ON `conversation_metadata` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_metadata_accountPackage` ON `conversation_metadata` (`accountPackage`)")

                // Profile account mapping
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `profile_account_mapping` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `profileId` TEXT NOT NULL,
                        `accountPackage` TEXT NOT NULL,
                        `firstSeen` INTEGER NOT NULL,
                        `lastSeen` INTEGER NOT NULL,
                        `interactionCount` INTEGER NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_profile_account_mapping_profileId` ON `profile_account_mapping` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_profile_account_mapping_accountPackage` ON `profile_account_mapping` (`accountPackage`)")

                // Permanent media
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `permanent_media` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `mediaHash` TEXT NOT NULL,
                        `originalUrl` TEXT,
                        `localPath` TEXT NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `profileId` TEXT,
                        `albumId` INTEGER,
                        `width` INTEGER,
                        `height` INTEGER,
                        `fileSize` INTEGER NOT NULL,
                        `savedTimestamp` INTEGER NOT NULL,
                        `accountPackage` TEXT NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_permanent_media_mediaHash` ON `permanent_media` (`mediaHash`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_permanent_media_profileId` ON `permanent_media` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_permanent_media_mediaType` ON `permanent_media` (`mediaType`)")
            }
        }
    }
}
