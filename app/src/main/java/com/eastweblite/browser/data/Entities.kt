package com.eastweblite.browser.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks", indices = [Index(value = ["url"], unique = true)])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "history_items", indices = [Index(value = ["url"]), Index(value = ["timestamp"])])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "passwords", indices = [Index(value = ["site"]), Index(value = ["url"])])
data class PasswordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val site: String,
    val url: String,
    val username: String,
    val encryptedPass: String,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads", indices = [Index(value = ["status"]), Index(value = ["timestamp"])])
data class DownloadEntity(
    @PrimaryKey val id: String, // dl1, dl2, etc.
    val url: String,
    val filename: String,
    val status: String, // downloading, paused, completed, failed, cancelled
    val receivedBytes: Long,
    val totalSize: Long,
    val downloadSpeed: Double, // bytes per second
    val etaSeconds: Long,
    val progressPercent: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val isTurbo: Boolean = false
)

@Entity(tableName = "user_settings")
data class UserSettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "site_permissions", indices = [Index(value = ["origin", "permission"], unique = true)])
data class SitePermissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val origin: String,
    val permission: String,
    val isAllowed: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
