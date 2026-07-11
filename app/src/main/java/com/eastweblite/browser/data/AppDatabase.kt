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
        UserSettingEntity::class,
        SitePermissionEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun passwordDao(): PasswordDao
    abstract fun downloadDao(): DownloadDao
    abstract fun settingDao(): SettingDao
    abstract fun sitePermissionDao(): SitePermissionDao

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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `site_permissions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `origin` TEXT NOT NULL,
                        `permission` TEXT NOT NULL,
                        `isAllowed` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_site_permissions_origin_permission` ON `site_permissions` (`origin`, `permission`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chromium_lite_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
