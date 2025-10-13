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
import com.grindrplus.persistence.dao.AlbumDao
import com.grindrplus.persistence.dao.MediaItemDao
import com.grindrplus.persistence.dao.SavedPhraseDao
import com.grindrplus.persistence.dao.TeleportLocationDao
import com.grindrplus.persistence.dao.ViewedProfileDao
import com.grindrplus.persistence.dao.ViewedSummaryDao
import com.grindrplus.persistence.model.AlbumContentEntity
import com.grindrplus.persistence.model.AlbumEntity
import com.grindrplus.persistence.model.MediaItem
import com.grindrplus.persistence.model.SavedPhraseEntity
import com.grindrplus.persistence.model.TeleportLocationEntity
import com.grindrplus.persistence.model.ViewedProfile
import com.grindrplus.persistence.model.ViewedSummary
import com.grindrplus.persistence.dao.ChatBackupDao
import com.grindrplus.persistence.model.ArchivedChatMessageEntity
import com.grindrplus.persistence.model.ArchivedConversationEntity
import com.grindrplus.persistence.model.ChatBackup
import com.grindrplus.persistence.model.ConversationBackup
import com.grindrplus.persistence.model.ParticipantBackup

@Database(
    entities = [
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
        ConversationBackup::class,  // Add this
        ParticipantBackup::class    // Add this
    ],
    version = 8, // Increment version
    exportSchema = false
)
@TypeConverters(DateConverter::class, ListConverter::class)
abstract class GPDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun teleportLocationDao(): TeleportLocationDao
    abstract fun savedPhraseDao(): SavedPhraseDao
    abstract fun chatBackupDao(): ChatBackupDao  // Add this

    companion object {
        private const val DATABASE_NAME = "grindrplus.db"

        @Volatile
        private var INSTANCE: GPDatabase? = null


      /*  fun create(context: Context): GPDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GPDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration() // <-- ADD THIS LINE
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
    }
        */



        fun create(context: Context): GPDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context,
                    GPDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                INSTANCE = instance
                instance
            }
        }


     private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create chat_backup table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_backup` (
                        `message_id` TEXT NOT NULL PRIMARY KEY,
                        `conversation_id` TEXT NOT NULL,
                        `sender` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `type` TEXT NOT NULL
                    )
                """
                )

                // Create conversation_backup table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `conversation_backup` (
                        `conversation_id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL DEFAULT '',
                        `last_message_timestamp` INTEGER NOT NULL,
                        `unread` INTEGER NOT NULL DEFAULT 0
                    )
                """
                )

                // Create participant_backup table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `participant_backup` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT,
                        `conversation_id` TEXT NOT NULL,
                        `profile_id` TEXT NOT NULL,
                        `last_online` INTEGER NOT NULL
                    )
                """
                )

                // Create indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_backup_conversation_id` ON `chat_backup` (`conversation_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_participant_backup_conversation_id` ON `participant_backup` (`conversation_id`)")
            }
        }


        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `viewed_summary` (
                        `id` INTEGER NOT NULL PRIMARY KEY,
                        `viewedCount` INTEGER NOT NULL,
                        `mostRecentProfileId` TEXT
                    )
                """
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `viewed_profiles` (
                        `profileId` TEXT NOT NULL PRIMARY KEY,
                        `viewCount` INTEGER NOT NULL,
                        `lastTimestamp` INTEGER NOT NULL,
                        `photoHashes` TEXT NOT NULL
                    )
                """
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_items` (
                        `imageHash` TEXT NOT NULL PRIMARY KEY,
                        `mediaId` INTEGER,
                        `url` TEXT NOT NULL,
                        `width` INTEGER NOT NULL,
                        `height` INTEGER NOT NULL,
                        `takenOnGrindr` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `imageType` INTEGER NOT NULL,
                        `tapType` INTEGER NOT NULL,
                        `localPath` TEXT
                    )
                """
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `archived_conversations` (
                        `conversationId` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT,
                        `lastMessageTimestamp` INTEGER
                    )
                """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_archived_conversations_conversationId` ON `archived_conversations` (`conversationId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `archived_chat_messages` (
                        `messageId` TEXT NOT NULL PRIMARY KEY,
                        `conversationId` TEXT NOT NULL,
                        `senderId` TEXT,
                        `timestamp` INTEGER,
                        `body` TEXT
                    )
                """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_archived_chat_messages_conversationId` ON `archived_chat_messages` (`conversationId`)")
            }
        }
    }
}