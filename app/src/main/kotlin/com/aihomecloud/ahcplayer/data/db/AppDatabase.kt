package com.aihomecloud.ahcplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aihomecloud.ahcplayer.BuildConfig

@Database(
    entities = [SourceEntity::class, WatchHistoryEntity::class, MediaMetadataEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun mediaMetadataDao(): MediaMetadataDao

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

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "ahcplayer.db"
            )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .apply { if (BuildConfig.DEBUG) fallbackToDestructiveMigration() }
                .build().also { INSTANCE = it }
        }
    }
}
