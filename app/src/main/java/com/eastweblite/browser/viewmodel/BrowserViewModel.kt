package com.eastweblite.browser.viewmodel

import android.app.Application
import android.os.Environment
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eastweblite.browser.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class TabState(
    val id: String,
    val title: String,
    val url: String,
    val loading: Boolean = false,
    val navStack: List<String> = listOf("lite://newtab"),
    val navIndex: Int = 0,
    val progress: Int = 0,
    val isIncognito: Boolean = false
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: BrowserRepository
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val secureRandom = SecureRandom()

    private val _settingsMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val settingsMap: StateFlow<Map<String, String>> = _settingsMap.asStateFlow()

    private val _tabs = MutableStateFlow<List<TabState>>(emptyList())
    val tabs: StateFlow<List<TabState>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow("")
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val _vaultLocked = MutableStateFlow(true)
    val vaultLocked: StateFlow<Boolean> = _vaultLocked.asStateFlow()

    private val downloadJobs = mutableMapOf<String, Job>()
    private var vaultKey: SecretKeySpec? = null
    private var vaultAutoLockJob: Job? = null

    val bookmarks: StateFlow<List<BookmarkEntity>>
    val history: StateFlow<List<HistoryEntity>>
    val passwords: StateFlow<List<PasswordEntity>>
    val downloads: StateFlow<List<DownloadEntity>>

    init {
        val db = AppDatabase.getInstance(application)
        repository = BrowserRepository(db)

        bookmarks = repository.allBookmarks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        history = repository.allHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        passwords = repository.allPasswords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        downloads = repository.allDownloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        viewModelScope.launch {
            repository.allSettings.collect { settingsList ->
                val map = settingsList.associate { it.key to it.value }.toMutableMap()

                val defaults = mapOf(
                    "theme" to "dark",
                    "search_engine" to "duckduckgo",
                    "search_engine_pattern" to "https://duckduckgo.com/?q=%s",
                    "show_bookmarks_bar" to "true",
                    "auto_hide_toolbar" to "false",
                    "language" to "en",
                    "user_agent" to "chrome",
                    // v2 format: v2:<base64-salt>:<base64-verifier>
                    "vault_master_hash" to "",
                    "vault_auto_lock_seconds" to "300",
                    "dl_turbo" to "false"
                )

                for ((key, default) in defaults) {
                    if (!map.containsKey(key)) {
                        map[key] = default
                        repository.saveSetting(key, default)
                    }
                }

                _settingsMap.value = map

                if (_tabs.value.isEmpty()) {
                    createNewTab()
                }
            }
        }
    }

    fun updateSetting(key: String, value: String) {
        // Incognito is intentionally not persisted as a global privacy mode any more.
        if (key == "incognito") return
        viewModelScope.launch {
            repository.saveSetting(key, value)
            val updated = _settingsMap.value.toMutableMap()
            updated[key] = value
            _settingsMap.value = updated
            if (key == "vault_auto_lock_seconds" && !_vaultLocked.value) {
                scheduleVaultAutoLock()
            }
        }
    }

    fun createNewTab(url: String = "lite://newtab", isIncognito: Boolean = false) {
        val newId = UUID.randomUUID().toString()
        val newTab = TabState(
            id = newId,
            title = if (url == "lite://newtab") "New Tab" else getTitleFromUrl(url),
            url = url,
            navStack = listOf(url),
            navIndex = 0,
            isIncognito = isIncognito
        )
        val currentTabs = _tabs.value.toMutableList()
        currentTabs.add(newTab)
        _tabs.value = currentTabs
        _activeTabId.value = newId
    }

    fun closeTab(id: String) {
        val currentTabs = _tabs.value.toMutableList()
        val idx = currentTabs.indexOfFirst { it.id == id }
        if (idx == -1) return

        currentTabs.removeAt(idx)
        _tabs.value = currentTabs

        if (currentTabs.isEmpty()) {
            createNewTab()
        } else if (_activeTabId.value == id) {
            val nextActiveIndex = if (idx >= currentTabs.size) currentTabs.size - 1 else idx
            _activeTabId.value = currentTabs[nextActiveIndex].id
        }
    }

    fun switchTab(id: String) {
        _activeTabId.value = id
    }

    fun setTabIncognito(id: String, enabled: Boolean) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == id) tab.copy(isIncognito = enabled) else tab
        }
    }

    fun updateTabUrl(id: String, url: String, title: String? = null) {
        val tabBeforeUpdate = _tabs.value.find { it.id == id }
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == id) {
                val updatedStack = tab.navStack.subList(0, tab.navIndex + 1).toMutableList()
                if (updatedStack.lastOrNull() != url) {
                    updatedStack.add(url)
                }
                tab.copy(
                    url = url,
                    title = title ?: getTitleFromUrl(url),
                    navStack = updatedStack,
                    navIndex = updatedStack.size - 1
                )
            } else tab
        }

        if (!url.startsWith("lite://") && tabBeforeUpdate?.isIncognito != true) {
            viewModelScope.launch {
                repository.insertHistoryItem(
                    HistoryEntity(
                        title = title ?: getTitleFromUrl(url),
                        url = url
                    )
                )
            }
        }
    }

    fun updateTabLoadingAndProgress(id: String, loading: Boolean, progress: Int) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == id) tab.copy(loading = loading, progress = progress.coerceIn(0, 100)) else tab
        }
    }

    fun updateTabTitleOnly(id: String, title: String) {
        _tabs.value = _tabs.value.map { tab -> if (tab.id == id) tab.copy(title = title) else tab }
    }

    fun onWebViewNavigated(id: String, url: String, title: String? = null) {
        val current = _tabs.value.find { it.id == id } ?: return
        if (current.url != url) updateTabUrl(id, url, title)
    }

    fun navigateTabBack(id: String) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == id && tab.navIndex > 0) {
                val nextIdx = tab.navIndex - 1
                tab.copy(url = tab.navStack[nextIdx], navIndex = nextIdx, title = getTitleFromUrl(tab.navStack[nextIdx]))
            } else tab
        }
    }

    fun navigateTabForward(id: String) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == id && tab.navIndex < tab.navStack.size - 1) {
                val nextIdx = tab.navIndex + 1
                tab.copy(url = tab.navStack[nextIdx], navIndex = nextIdx, title = getTitleFromUrl(tab.navStack[nextIdx]))
            } else tab
        }
    }

    private fun getTitleFromUrl(url: String): String {
        return when {
            url == "lite://newtab" -> "New Tab"
            url.startsWith("lite://") -> url.removePrefix("lite://").replaceFirstChar { it.uppercase() }
            else -> try {
                val domain = url.substringAfter("://").substringBefore("/")
                domain.replace("www.", "").ifBlank { url }
            } catch (e: Exception) {
                url
            }
        }
    }

    fun toggleBookmark(url: String, title: String) {
        if (url.startsWith("lite://")) return
        viewModelScope.launch {
            val existing = bookmarks.value.find { it.url == url }
            if (existing != null) repository.deleteBookmark(existing) else repository.insertBookmark(BookmarkEntity(title = title, url = url))
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) = viewModelScope.launch { repository.deleteBookmark(bookmark) }
    fun deleteHistoryItem(item: HistoryEntity) = viewModelScope.launch { repository.deleteHistoryItem(item) }
    fun clearHistory() = viewModelScope.launch { repository.clearHistory() }


    fun clearBrowsingData(
        clearHistory: Boolean,
        clearCookiesAndSiteData: Boolean,
        clearDownloadRecords: Boolean,
        clearDownloadedFiles: Boolean,
        clearPdfCache: Boolean,
        clearSavedPasswords: Boolean
    ) {
        viewModelScope.launch {
            if (clearHistory) repository.clearHistory()
            if (clearDownloadRecords) repository.clearDownloads()
            if (clearSavedPasswords) {
                repository.clearPasswords()
                vaultKey = null
                _vaultLocked.value = true
            }
            if (clearCookiesAndSiteData) {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
            }
            withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                if (clearPdfCache) {
                    app.cacheDir.listFiles { file ->
                        file.isFile && file.name.startsWith("pdf_") && file.name.endsWith(".pdf")
                    }?.forEach { it.delete() }
                }
                if (clearDownloadedFiles) {
                    val downloadsDir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    downloadsDir?.listFiles()?.forEach { file ->
                        if (file.isFile) file.delete()
                    }
                }
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            downloadJobs.values.forEach { it.cancel() }
            downloadJobs.clear()
            vaultKey = null
            repository.clearAllBookmarks()
            repository.clearHistory()
            repository.clearPasswords()
            repository.clearDownloads()
            repository.clearAllSettings()
            _vaultLocked.value = true
            _tabs.value = emptyList()
            createNewTab()
        }
    }

    fun lockVault() {
        vaultAutoLockJob?.cancel()
        vaultAutoLockJob = null
        vaultKey = null
        _vaultLocked.value = true
    }

    fun handleAppBackgroundForVault() {
        if (_settingsMap.value["vault_auto_lock_seconds"] == "background") {
            lockVault()
        }
    }

    private fun scheduleVaultAutoLock() {
        vaultAutoLockJob?.cancel()
        val setting = _settingsMap.value["vault_auto_lock_seconds"] ?: "300"
        if (setting == "0" || setting == "background" || _vaultLocked.value) return
        val seconds = setting.toLongOrNull()?.coerceAtLeast(1L) ?: return
        vaultAutoLockJob = viewModelScope.launch {
            delay(seconds * 1000L)
            lockVault()
        }
    }

    fun unlockVault(pwd: String): Boolean {
        val stored = _settingsMap.value["vault_master_hash"] ?: ""
        if (stored.isEmpty()) return false

        if (stored.startsWith("v2:")) {
            val parts = stored.split(":")
            if (parts.size != 3) return false
            val salt = b64Decode(parts[1])
            val expectedVerifier = parts[2]
            val keyBytes = deriveVaultKey(pwd, salt)
            val actualVerifier = b64Encode(sha256("vault-verifier".toByteArray() + keyBytes))
            return if (constantTimeEquals(expectedVerifier, actualVerifier)) {
                vaultKey = SecretKeySpec(keyBytes, "AES")
                _vaultLocked.value = false
                scheduleVaultAutoLock()
                true
            } else {
                false
            }
        }

        // Unsupported legacy prototype vault format. Require resetting the master password/data.
        return false
    }

    fun setMasterPassword(pwd: String) {
        require(pwd.length >= 8) { "Master password must be at least 8 characters." }
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val keyBytes = deriveVaultKey(pwd, salt)
        val verifier = b64Encode(sha256("vault-verifier".toByteArray() + keyBytes))
        vaultKey = SecretKeySpec(keyBytes, "AES")
        updateSetting("vault_master_hash", "v2:${b64Encode(salt)}:$verifier")
        _vaultLocked.value = false
        scheduleVaultAutoLock()
    }

    fun addPasswordEntry(site: String, url: String, user: String, pass: String, notes: String) {
        if (_vaultLocked.value) return
        viewModelScope.launch {
            repository.insertPassword(
                PasswordEntity(
                    site = site,
                    url = url,
                    username = user,
                    encryptedPass = encryptPassword(pass),
                    notes = notes
                )
            )
        }
    }

    fun deletePassword(pwdEntry: PasswordEntity) = viewModelScope.launch { repository.deletePassword(pwdEntry) }

    fun encryptPassword(plain: String): String {
        val key = vaultKey ?: throw IllegalStateException("Vault is locked")
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return "v2:${b64Encode(iv)}:${b64Encode(cipherText)}"
    }

    fun decryptPassword(encrypted: String): String {
        if (!_vaultLocked.value) scheduleVaultAutoLock()
        return try {
            if (encrypted.startsWith("v2:")) {
                val key = vaultKey ?: return "*** locked ***"
                val parts = encrypted.split(":")
                if (parts.size != 3) return "***"
                val iv = b64Decode(parts[1])
                val cipherText = b64Decode(parts[2])
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                String(cipher.doFinal(cipherText), Charsets.UTF_8)
            } else {
                "*** unsupported legacy entry ***"
            }
        } catch (e: Exception) {
            "***"
        }
    }

    private fun deriveVaultKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 150_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun b64Encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun b64Decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
    private fun constantTimeEquals(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    fun generateRandomPassword(length: Int, upper: Boolean, lower: Boolean, numbers: Boolean, symbols: Boolean): String {
        val uChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lChars = "abcdefghijklmnopqrstuvwxyz"
        val numChars = "0123456789"
        val symChars = "!@#$%^&*()_+-=[]{}|;:<>?"

        var pool = ""
        if (upper) pool += uChars
        if (lower) pool += lChars
        if (numbers) pool += numChars
        if (symbols) pool += symChars
        if (pool.isEmpty()) pool = lChars + numChars

        return buildString {
            repeat(length.coerceIn(8, 128)) {
                append(pool[secureRandom.nextInt(pool.length)])
            }
        }
    }

    fun getPasswordStrength(pwd: String): Int {
        if (pwd.isEmpty()) return 0
        var score = 0
        if (pwd.length >= 8) score++
        if (pwd.length >= 12) score++
        if (pwd.any { it.isUpperCase() }) score++
        if (pwd.any { it.isDigit() }) score++
        if (pwd.any { !it.isLetterOrDigit() }) score++
        return score.coerceAtMost(4)
    }

    fun startDownload(url: String, filename: String? = null) {
        val dlId = "dl_${UUID.randomUUID()}"
        val finalName = sanitizeFilename(filename?.takeIf { it.isNotBlank() } ?: inferFilename(url))
        val isTurbo = _settingsMap.value["dl_turbo"] == "true"
        val initialEntity = DownloadEntity(
            id = dlId,
            url = url,
            filename = finalName,
            status = "downloading",
            receivedBytes = 0L,
            totalSize = 0L,
            downloadSpeed = 0.0,
            etaSeconds = 0L,
            progressPercent = 0.0f,
            isTurbo = isTurbo
        )

        val job = viewModelScope.launch(Dispatchers.IO) {
            repository.insertDownload(initialEntity)
            runDownload(initialEntity, resumeFromBytes = 0L)
        }
        downloadJobs[dlId] = job
    }

    private suspend fun runDownload(entity: DownloadEntity, resumeFromBytes: Long) {
        val downloadsDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: getApplication<Application>().filesDir
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val destination = uniqueFile(downloadsDir, entity.filename, resumeFromBytes > 0)

        try {
            if (entity.isTurbo && resumeFromBytes == 0L && tryChunkedDownload(entity, downloadsDir, destination)) {
                return
            }

            val requestBuilder = Request.Builder().url(entity.url)
            if (resumeFromBytes > 0 && destination.exists()) requestBuilder.header("Range", "bytes=$resumeFromBytes-")
            val request = requestBuilder.build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                val body = response.body ?: throw IllegalStateException("Empty response body")
                val responseLength = body.contentLength().coerceAtLeast(0L)
                val totalSize = if (resumeFromBytes > 0 && response.code == 206) resumeFromBytes + responseLength else responseLength
                var received = if (resumeFromBytes > 0 && response.code == 206) resumeFromBytes else 0L
                val started = System.currentTimeMillis()
                var lastUiUpdate = 0L

                FileOutputStream(destination, received > 0).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    val input = body.byteStream()
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        received += read
                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdate > 500) {
                            lastUiUpdate = now
                            repository.insertDownload(entity.copyProgress(received, totalSize, started, "downloading"))
                        }
                    }
                    output.flush()
                }
                repository.insertDownload(entity.copyProgress(received, totalSize, started, "completed"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            repository.insertDownload(entity.copy(status = "failed", downloadSpeed = 0.0, etaSeconds = 0L))
        }
    }

    private suspend fun tryChunkedDownload(entity: DownloadEntity, downloadsDir: File, destination: File): Boolean {
        val headRequest = Request.Builder().url(entity.url).head().build()
        val headResponse = try {
            httpClient.newCall(headRequest).execute()
        } catch (e: Exception) {
            return false
        }

        headResponse.use { response ->
            if (!response.isSuccessful) return false
            val totalSize = response.header("Content-Length")?.toLongOrNull() ?: return false
            val acceptsRanges = response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
            if (!acceptsRanges || totalSize < 2L * 1024L * 1024L) return false

            val started = System.currentTimeMillis()
            val received = AtomicLong(0L)
            val chunkCount = 4
            val chunkSize = (totalSize + chunkCount - 1) / chunkCount
            val partFiles = (0 until chunkCount).map { index -> File(downloadsDir, "${entity.id}.part$index") }

            repository.insertDownload(entity.copy(totalSize = totalSize, receivedBytes = 0L, progressPercent = 0f, status = "downloading"))

            coroutineScope {
                (0 until chunkCount).map { index ->
                    async(Dispatchers.IO) {
                        val start = index * chunkSize
                        val end = minOf(totalSize - 1, start + chunkSize - 1)
                        if (start > end) return@async

                        val request = Request.Builder()
                            .url(entity.url)
                            .header("Range", "bytes=$start-$end")
                            .build()

                        httpClient.newCall(request).execute().use { rangedResponse ->
                            if (rangedResponse.code != 206) throw IllegalStateException("Server did not honor range request")
                            val body = rangedResponse.body ?: throw IllegalStateException("Empty ranged response")
                            FileOutputStream(partFiles[index], false).use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                val input = body.byteStream()
                                var lastUpdate = 0L
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    output.write(buffer, 0, read)
                                    val totalReceived = received.addAndGet(read.toLong())
                                    val now = System.currentTimeMillis()
                                    if (now - lastUpdate > 500) {
                                        lastUpdate = now
                                        repository.insertDownload(entity.copyProgress(totalReceived, totalSize, started, "downloading"))
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()
            }

            FileOutputStream(destination, false).use { output ->
                partFiles.forEach { part ->
                    part.inputStream().use { input -> input.copyTo(output) }
                    part.delete()
                }
                output.flush()
            }
            repository.insertDownload(entity.copyProgress(totalSize, totalSize, started, "completed"))
            return true
        }
    }

    private fun DownloadEntity.copyProgress(received: Long, total: Long, started: Long, status: String): DownloadEntity {
        val elapsedSeconds = ((System.currentTimeMillis() - started) / 1000.0).coerceAtLeast(0.1)
        val speed = received / elapsedSeconds
        val eta = if (speed > 0 && total > 0) ((total - received) / speed).toLong().coerceAtLeast(0L) else 0L
        val percent = if (total > 0) ((received.toDouble() / total.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f) else 0f
        return copy(
            status = status,
            receivedBytes = received,
            totalSize = total,
            downloadSpeed = speed,
            etaSeconds = eta,
            progressPercent = percent
        )
    }

    fun pauseDownload(id: String) {
        downloadJobs[id]?.cancel()
        viewModelScope.launch {
            val entity = repository.getDownloadById(id)
            if (entity != null && entity.status == "downloading") {
                repository.insertDownload(entity.copy(status = "paused", downloadSpeed = 0.0, etaSeconds = 0))
            }
        }
    }

    fun resumeDownload(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = repository.getDownloadById(id) ?: return@launch
            if (entity.status == "paused" || entity.status == "failed") {
                val resumed = entity.copy(status = "downloading")
                repository.insertDownload(resumed)
                val job = viewModelScope.launch(Dispatchers.IO) { runDownload(resumed, resumeFromBytes = entity.receivedBytes) }
                downloadJobs[entity.id] = job
            }
        }
    }

    fun cancelDownload(id: String) {
        downloadJobs[id]?.cancel()
        viewModelScope.launch {
            val entity = repository.getDownloadById(id)
            if (entity != null) repository.insertDownload(entity.copy(status = "cancelled", downloadSpeed = 0.0, etaSeconds = 0))
        }
    }

    fun deleteDownload(entity: DownloadEntity) {
        downloadJobs[entity.id]?.cancel()
        viewModelScope.launch { repository.deleteDownload(entity) }
    }

    private fun inferFilename(url: String): String = try {
        url.substringAfterLast('/').substringBefore('?').ifBlank { "download" }
    } catch (e: Exception) { "download" }

    private fun sanitizeFilename(input: String): String = input
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .take(120)
        .ifBlank { "download" }

    private fun uniqueFile(dir: File, filename: String, allowExisting: Boolean): File {
        val base = filename.substringBeforeLast('.', filename)
        val ext = filename.substringAfterLast('.', "").let { if (it.isBlank() || it == filename) "" else ".$it" }
        var candidate = File(dir, "$base$ext")
        if (allowExisting || !candidate.exists()) return candidate
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base-$index$ext")
            index++
        }
        return candidate
    }
}
