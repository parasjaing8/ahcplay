package com.aihomecloud.ahcplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SourceEntity::class, WatchHistoryEntity::class, MediaMetadataEntity::class],
    version = 5,
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

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "ahcplayer.db"
            )
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
        }
    }
}
