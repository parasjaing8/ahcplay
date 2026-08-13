package com.aihomecloud.ahcplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SourceEntity::class, WatchHistoryEntity::class, MediaMetadataEntity::class,
        BookmarkEntity::class, PlaylistEntity::class, PlaylistItemEntity::class,
        PendingPlaybackReportEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun mediaMetadataDao(): MediaMetadataDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun pendingPlaybackReportDao(): PendingPlaybackReportDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sources ADD COLUMN hasPin INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE media_metadata ADD COLUMN backdropUrl TEXT")
                database.execSQL("ALTER TABLE media_metadata ADD COLUMN genre TEXT")
                database.execSQL("ALTER TABLE media_metadata ADD COLUMN mediaType TEXT")
                database.execSQL("DELETE FROM media_metadata")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sources ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** Adds the bookmark and playlist tables. CREATE-only — no existing data touched. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmark` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`uri` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, `label` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`playlistId` INTEGER NOT NULL, `uri` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL)"
                )
            }
        }

        /** Rebuilds watch_history with a composite (uri, sourceId) key and adds sync bookkeeping;
         *  creates the pending playback report queue. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE watch_history RENAME TO watch_history_old")
                database.execSQL(
                    "CREATE TABLE watch_history (`uri` TEXT NOT NULL, `sourceId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                        "`entryId` INTEGER, `clientUpdatedAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`version` INTEGER NOT NULL DEFAULT 0, `dirty` INTEGER NOT NULL DEFAULT 0, " +
                        "`lastWatchedAt` INTEGER NOT NULL, PRIMARY KEY(`uri`, `sourceId`))"
                )
                database.execSQL(
                    "INSERT INTO watch_history (uri, sourceId, title, positionMs, durationMs, lastWatchedAt) " +
                        "SELECT uri, sourceId, title, positionMs, durationMs, lastWatchedAt FROM watch_history_old"
                )
                database.execSQL("DROP TABLE watch_history_old")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_playback_report` (`entryId` INTEGER NOT NULL, " +
                        "`sourceId` INTEGER NOT NULL, `uri` TEXT NOT NULL, `positionSeconds` REAL NOT NULL, " +
                        "`durationSeconds` REAL, `clientUpdatedAt` INTEGER NOT NULL, `attempts` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`entryId`, `sourceId`))"
                )
            }
        }

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "ahcplayer.db"
            )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                // No destructive fallback, not even on debug. It used to wipe the database
                // when a migration was missing or wrong, which meant the defect never failed
                // anywhere a developer would notice — and then reached release, where there
                // is no fallback and the crash is real. Failing here costs a reinstall and
                // surfaces the bug while it is still cheap.
                .build().also { INSTANCE = it }
        }
    }
}
