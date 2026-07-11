package com.eastweblite.browser.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        PasswordEntity::class,
        DownloadEntity::class,
        UserSettingEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun passwordDao(): PasswordDao
    abstract fun downloadDao(): DownloadDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM bookmarks WHERE id NOT IN (SELECT MIN(id) FROM bookmarks GROUP BY url)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bookmarks_url ON bookmarks(url)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_history_items_url ON history_items(url)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_history_items_timestamp ON history_items(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_passwords_site ON passwords(site)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_passwords_url ON passwords(url)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_status ON downloads(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_timestamp ON downloads(timestamp)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chromium_lite_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
