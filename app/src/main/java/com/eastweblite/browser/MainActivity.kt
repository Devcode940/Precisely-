package com.eastweblite.browser

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Build
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import kotlinx.coroutines.delay
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eastweblite.browser.ui.screens.*
import com.eastweblite.browser.ui.theme.ChromiumTheme
import com.eastweblite.browser.viewmodel.BrowserViewModel
import com.eastweblite.browser.viewmodel.TabState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WebView.startSafeBrowsing(this) { /* best-effort platform protection */ }
        }
        setContent {
            val viewModel: BrowserViewModel = viewModel()
            val settingsMap by viewModel.settingsMap.collectAsState()
            val themeMode = settingsMap["theme"] ?: "dark"
            val isIncognito = settingsMap["incognito"] == "true"

            val activeTheme = if (isIncognito) "incognito" else themeMode

            ChromiumTheme(themeMode = activeTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BrowserApp(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserApp(viewModel: BrowserViewModel) {
    val context = LocalContext.current
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val settingsMap by viewModel.settingsMap.collectAsState()

    val langCode = settingsMap["language"] ?: "en"
    val showBB = settingsMap["show_bookmarks_bar"] == "true"
    val autoHide = settingsMap["auto_hide_toolbar"] == "true"

    val activeTab = tabs.find { it.id == activeTabId }
    val isIncognito = activeTab?.isIncognito == true
    val lifecycleOwner = LocalLifecycleOwner.current


    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.handleAppBackgroundForVault()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Dropdown menu state
    var isMenuExpanded by remember { mutableStateOf(false) }
    var showAiHubDialog by remember { mutableStateOf(false) }

    // Find in page state
    var showFindInPage by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findActiveIndex by remember { mutableIntStateOf(-1) }
    var findMatchCount by remember { mutableIntStateOf(0) }

    // Toast manager state
    var toastText by remember { mutableStateOf("") }
    LaunchedEffect(toastText) {
        if (toastText.isNotEmpty()) {
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
            delay(2000)
            toastText = ""
        }
    }

    // Keep cache map of WebViews in memory to preserve tab navigation states cleanly!
    val webViewCache = remember { mutableMapOf<String, WebView>() }

    fun resetFindInPage(clearWebViewMatches: Boolean = true) {
        if (clearWebViewMatches) activeTab?.let { webViewCache[it.id]?.clearMatches() }
        showFindInPage = false
        findQuery = ""
        findActiveIndex = -1
        findMatchCount = 0
    }

    fun runFindInPage(query: String) {
        findQuery = query
        val webView = activeTab?.let { webViewCache[it.id] }
        if (query.isBlank()) {
            webView?.clearMatches()
            findActiveIndex = -1
            findMatchCount = 0
        } else {
            webView?.findAllAsync(query)
        }
    }

    fun openFindInPage() {
        val tab = activeTab
        val canFind = tab != null && !tab.url.startsWith("lite://") && webViewCache[tab.id] != null
        if (canFind) {
            showFindInPage = true
        } else {
            toastText = "Find in Page is available after a web page loads"
        }
    }

    LaunchedEffect(activeTabId) {
        resetFindInPage(clearWebViewMatches = false)
    }

    fun destroyTabWebView(tab: TabState) {
        webViewCache.remove(tab.id)?.let { webView ->
            webView.clearMatches()
            webView.stopLoading()
            if (tab.isIncognito) {
                webView.clearHistory()
                webView.clearCache(true)
                webView.clearFormData()
                CookieManager.getInstance().removeSessionCookies(null)
                CookieManager.getInstance().flush()
            }
            webView.destroy()
        }
    }

    // Clean up closed tab WebViews to prevent memory leaks.
    LaunchedEffect(tabs) {
        val tabIds = tabs.map { it.id }.toSet()
        val keysToRemove = webViewCache.keys.filter { it !in tabIds }
        keysToRemove.forEach { key ->
            webViewCache.remove(key)?.let { webView ->
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewCache.values.forEach { webView ->
                webView.stopLoading()
                webView.destroy()
            }
            webViewCache.clear()
        }
    }

    if (activeTab != null) {
        // Back press support inside WebView tabs!
        BackHandler {
            val activeWebView = webViewCache[activeTab.id]
            if (activeWebView != null && activeWebView.canGoBack()) {
                activeWebView.goBack()
            } else if (activeTab.navIndex > 0) {
                viewModel.navigateTabBack(activeTab.id)
            } else if (tabs.size > 1) {
                destroyTabWebView(activeTab)
                viewModel.closeTab(activeTab.id)
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column {
                    // 1. MAC OSX WINDOW TITLE BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mac window red, yellow, green control dots
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFFFF5F57), CircleShape))
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFFFEBC2E), CircleShape))
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFF28C840), CircleShape))
                        }

                        // Centered browser title
                        Text(
                            text = "${activeTab.title} — Chromium Lite",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.width(36.dp)) // Equalizer space for control dots
                    }

                    // 2. HORIZONTAL TAB SWITCHER BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                            .padding(top = 4.dp, bottom = 0.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            tabs.forEach { tab ->
                                val isActive = tab.id == activeTabId
                                TabCard(
                                    tab = tab,
                                    isActive = isActive,
                                    onSelect = { viewModel.switchTab(tab.id) },
                                    onClose = {
                                        destroyTabWebView(tab)
                                        viewModel.closeTab(tab.id)
                                    }
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.createNewTab(isIncognito = activeTab.isIncognito) },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("add_tab_button")
                                .padding(bottom = 2.dp, end = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "New Tab", modifier = Modifier.size(18.dp))
                        }
                    }

                    // 3. MAIN NAVIGATION TOOLBAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Navigation controls
                        IconButton(
                            onClick = {
                                val activeWebView = webViewCache[activeTab.id]
                                if (activeWebView != null && activeWebView.canGoBack()) {
                                    activeWebView.goBack()
                                } else {
                                    viewModel.navigateTabBack(activeTab.id)
                                }
                            },
                            enabled = activeTab.navIndex > 0 || (webViewCache[activeTab.id]?.canGoBack() ?: false),
                            modifier = Modifier.size(32.dp).testTag("nav_back")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = {
                                val activeWebView = webViewCache[activeTab.id]
                                if (activeWebView != null && activeWebView.canGoForward()) {
                                    activeWebView.goForward()
                                } else {
                                    viewModel.navigateTabForward(activeTab.id)
                                }
                            },
                            enabled = activeTab.navIndex < activeTab.navStack.size - 1 || (webViewCache[activeTab.id]?.canGoForward() ?: false),
                            modifier = Modifier.size(32.dp).testTag("nav_forward")
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = {
                                val web = webViewCache[activeTab.id]
                                if (web != null && !activeTab.url.startsWith("lite://")) {
                                    web.reload()
                                } else {
                                    viewModel.createNewTab(activeTab.url, isIncognito = activeTab.isIncognito)
                                }
                            },
                            modifier = Modifier.size(32.dp).testTag("nav_reload")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = { viewModel.updateTabUrl(activeTab.id, "lite://newtab") },
                            modifier = Modifier.size(32.dp).testTag("nav_home")
                        ) {
                            Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(16.dp))
                        }

                        // Address Bar input
                        var addressInput by remember(activeTab.url) {
                            mutableStateOf(if (activeTab.url.startsWith("lite://")) "" else activeTab.url)
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(18.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (activeTab.url.startsWith("https://")) Icons.Default.Lock else Icons.Default.Public,
                                contentDescription = "Security Indicator",
                                tint = if (activeTab.url.startsWith("https://")) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            BasicTextField(
                                value = addressInput,
                                onValueChange = { addressInput = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("address_bar_text_field"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = {
                                    if (addressInput.trim().isNotEmpty()) {
                                        val pattern = settingsMap["search_engine_pattern"] ?: "https://duckduckgo.com/?q=%s"
                                        val destination = formatBrowserUrl(addressInput.trim(), pattern)
                                        viewModel.updateTabUrl(activeTab.id, destination)
                                    }
                                }),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp
                                )
                            )

                            // Bookmark trigger star
                            val isBookmarked = viewModel.bookmarks.collectAsState().value.any { it.url == activeTab.url }
                            IconButton(
                                onClick = { viewModel.toggleBookmark(activeTab.url, activeTab.title) },
                                modifier = Modifier.size(24.dp).testTag("bookmark_star")
                            ) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Default.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "Bookmark",
                                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Downloads Shortcut Badge
                        val activeDownloadsCount = viewModel.downloads.collectAsState().value.count { it.status == "downloading" }
                        IconButton(
                            onClick = { viewModel.updateTabUrl(activeTab.id, "lite://downloads") },
                            modifier = Modifier.size(32.dp).testTag("downloads_badge")
                        ) {
                            Box {
                                Icon(Icons.Default.FileDownload, contentDescription = "Downloads", modifier = Modifier.size(16.dp))
                                if (activeDownloadsCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            .align(Alignment.TopEnd),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            activeDownloadsCount.toString(),
                                            color = Color.Black,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Passwords key
                        IconButton(
                            onClick = { viewModel.updateTabUrl(activeTab.id, "lite://passwords") },
                            modifier = Modifier.size(32.dp).testTag("passwords_shortcut")
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = "Passwords Vault", modifier = Modifier.size(16.dp))
                        }

                        // Incognito mask icon
                        IconButton(
                            onClick = {
                                val next = !activeTab.isIncognito
                                viewModel.setTabIncognito(activeTab.id, next)
                                webViewCache[activeTab.id]?.let { web ->
                                    if (next) {
                                        web.clearHistory()
                                        web.clearCache(true)
                                        web.clearFormData()
                                    }
                                }
                                toastText = if (next) "Incognito on for this tab — history won't be saved" else "Incognito off for this tab"
                            },
                            modifier = Modifier.size(32.dp).testTag("incognito_shortcut")
                        ) {
                            Icon(
                                imageVector = if (isIncognito) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Incognito Mode",
                                tint = if (isIncognito) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // AI Chat Hub Button
                        IconButton(
                            onClick = { showAiHubDialog = true },
                            modifier = Modifier.size(32.dp).testTag("ai_chat_hub_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Chat Hub",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Menu Expand
                        IconButton(
                            onClick = { isMenuExpanded = true },
                            modifier = Modifier.size(32.dp).testTag("menu_burger")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu Options", modifier = Modifier.size(18.dp))
                        }

                        // Hamburger dropdown options
                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("AI Chat Hub ✨", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    showAiHubDialog = true
                                    isMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Find in Page", fontSize = 12.sp) },
                                onClick = {
                                    openFindInPage()
                                    isMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("New Tab", fontSize = 12.sp) },
                                onClick = {
                                    viewModel.createNewTab(isIncognito = activeTab.isIncognito)
                                    isMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Bookmarks", fontSize = 12.sp) },
                                onClick = {
                                    viewModel.updateTabUrl(activeTab.id, "lite://bookmarks")
                                    isMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("History", fontSize = 12.sp) },
                                onClick = {
                                    viewModel.updateTabUrl(activeTab.id, "lite://history")
                                    isMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Vault Passwords", fontSize = 12.sp) },
                                onClick = {
                                    viewModel.updateTabUrl(activeTab.id, "lite://passwords")
                                    isMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings", fontSize = 12.sp) },
                                onClick = {
                                    viewModel.updateTabUrl(activeTab.id, "lite://settings")
                                    isMenuExpanded = false
                                }
                            )
                        }
                    }

                    // 4. PORTABLE BOOKMARKS BAR
                    if (showBB) {
                        val bookList by viewModel.bookmarks.collectAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            bookList.take(6).forEach { b ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { viewModel.updateTabUrl(activeTab.id, b.url) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        b.title,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // 5. SECURE LOADING PROGRESS BAR
                    if (activeTab.loading) {
                        LinearProgressIndicator(
                            progress = { activeTab.progress / 100f },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(Color.Transparent)
                        )
                    }

                    if (showFindInPage && !activeTab.url.startsWith("lite://")) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = findQuery,
                                onValueChange = { runFindInPage(it) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("find_in_page_input"),
                                placeholder = { Text("Find in page", fontSize = 12.sp) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { runFindInPage(findQuery) })
                            )

                            Text(
                                text = if (findQuery.isBlank()) "—" else if (findMatchCount > 0) "${(findActiveIndex + 1).coerceAtLeast(1)} / $findMatchCount" else "0 / 0",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace
                            )

                            IconButton(
                                enabled = findMatchCount > 0,
                                onClick = { webViewCache[activeTab.id]?.findNext(false) },
                                modifier = Modifier.size(34.dp).testTag("find_previous")
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match", modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                enabled = findMatchCount > 0,
                                onClick = { webViewCache[activeTab.id]?.findNext(true) },
                                modifier = Modifier.size(34.dp).testTag("find_next")
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match", modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = { resetFindInPage() },
                                modifier = Modifier.size(34.dp).testTag("find_close")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close find in page", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // CONTENT ROUTING
                when {
                    activeTab.url == "lite://newtab" -> {
                        NewTabScreen(viewModel, langCode) { targetUrl ->
                            val pattern = settingsMap["search_engine_pattern"] ?: "https://duckduckgo.com/?q=%s"
                            val destination = formatBrowserUrl(targetUrl, pattern)
                            viewModel.updateTabUrl(activeTab.id, destination)
                        }
                    }
                    activeTab.url == "lite://bookmarks" -> {
                        BookmarksScreen(viewModel, langCode) { targetUrl ->
                            viewModel.updateTabUrl(activeTab.id, targetUrl)
                        }
                    }
                    activeTab.url == "lite://history" -> {
                        HistoryScreen(viewModel, langCode) { targetUrl ->
                            viewModel.updateTabUrl(activeTab.id, targetUrl)
                        }
                    }
                    activeTab.url == "lite://settings" -> {
                        SettingsScreen(viewModel, langCode) { text ->
                            toastText = text
                        }
                    }
                    activeTab.url == "lite://passwords" -> {
                        PasswordVaultScreen(viewModel, langCode) { text ->
                            toastText = text
                        }
                    }
                    activeTab.url == "lite://downloads" -> {
                        DownloadsScreen(viewModel, langCode) { text ->
                            toastText = text
                        }
                    }
                    activeTab.url.startsWith("lite://pdf") -> {
                        PDFViewerScreen(activeTab.url.substringAfter("url="), langCode) { text ->
                            toastText = text
                        }
                    }
                    else -> {
                        // NATIVE ANDROID WEBVIEW RENDERING FOR INTERNET ADDRESSES
                        key(activeTab.id) {
                            AndroidView(
                            factory = { ctx ->
                                webViewCache.getOrPut(activeTab.id) {
                                    WebView(ctx).apply {
                                        layoutParams = android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                        )

                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            databaseEnabled = false
                                            allowFileAccess = false
                                            allowContentAccess = false
                                            allowFileAccessFromFileURLs = false
                                            allowUniversalAccessFromFileURLs = false
                                            javaScriptCanOpenWindowsAutomatically = false
                                            setSupportMultipleWindows(false)
                                            mediaPlaybackRequiresUserGesture = true
                                            cacheMode = if (activeTab.isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                safeBrowsingEnabled = true
                                            }

                                            // Apply selected custom user-agent simulation string!
                                            val selectedUA = settingsMap["user_agent"] ?: "chrome"
                                            userAgentString = userAgentFor(selectedUA)
                                        }

                                        CookieManager.getInstance().setAcceptCookie(!activeTab.isIncognito)
                                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                                        if (activeTab.isIncognito) {
                                            clearCache(true)
                                            clearHistory()
                                            clearFormData()
                                        }

                                        webViewClient = object : WebViewClient() {
                                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                                url?.let {
                                                    viewModel.onWebViewNavigated(activeTab.id, it)
                                                    viewModel.updateTabLoadingAndProgress(activeTab.id, true, 20)
                                                }
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                url?.let {
                                                    viewModel.updateTabLoadingAndProgress(activeTab.id, false, 100)
                                                    view?.title?.let { t ->
                                                        viewModel.updateTabTitleOnly(activeTab.id, t)
                                                    }
                                                    if (showFindInPage && findQuery.isNotBlank()) {
                                                        view?.findAllAsync(findQuery)
                                                    }
                                                }
                                            }

                                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                                handler?.cancel()
                                                toastText = "Blocked insecure SSL certificate"
                                            }
                                        }

                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                viewModel.updateTabLoadingAndProgress(activeTab.id, newProgress < 100, newProgress)
                                            }

                                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                                title?.let { viewModel.updateTabTitleOnly(activeTab.id, it) }
                                            }
                                        }

                                        setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                                            findActiveIndex = activeMatchOrdinal
                                            findMatchCount = numberOfMatches
                                        }

                                        setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                                            viewModel.startDownload(url)
                                            toastText = "Download started safely"
                                            viewModel.updateTabUrl(activeTab.id, "lite://downloads")
                                        }

                                        loadUrl(activeTab.url)
                                    }
                                }
                            },
                            update = { webView ->
                                webView.settings.userAgentString = userAgentFor(settingsMap["user_agent"] ?: "chrome")
                                webView.settings.cacheMode = if (activeTab.isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                                CookieManager.getInstance().setAcceptCookie(!activeTab.isIncognito)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
                                if (webView.url != activeTab.url) {
                                    webView.loadUrl(activeTab.url)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAiHubDialog) {
        AIChatHubDialog(
            onDismiss = { showAiHubDialog = false },
            onOpenUrl = { url, inNewTab ->
                showAiHubDialog = false
                if (inNewTab) {
                    viewModel.createNewTab(url, isIncognito = activeTab?.isIncognito == true)
                } else if (activeTab != null) {
                    viewModel.updateTabUrl(activeTab.id, url)
                } else {
                    viewModel.createNewTab(url, isIncognito = activeTab?.isIncognito == true)
                }
            }
        )
    }
}

@Composable
fun TabCard(
    tab: TabState,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier
            .width(140.dp)
            .height(34.dp)
            .testTag("tab_${tab.id}"),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        tab.url == "lite://newtab" -> Icons.Default.Add
                        tab.url == "lite://bookmarks" -> Icons.Default.Star
                        tab.url == "lite://history" -> Icons.Default.History
                        tab.url == "lite://passwords" -> Icons.Default.VpnKey
                        tab.url == "lite://downloads" -> Icons.Default.FileDownload
                        tab.url == "lite://settings" -> Icons.Default.Settings
                        tab.url.startsWith("lite://pdf") -> Icons.Default.PictureAsPdf
                        else -> Icons.Default.Public
                    },
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = tab.title,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(10.dp))
            }
        }
    }
}

fun userAgentFor(selectedUA: String): String = when (selectedUA) {
    "firefox" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"
    "safari" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_2) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15"
    "edge" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"
    "mobile" -> "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    else -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}

fun formatBrowserUrl(input: String, pattern: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return "lite://newtab"
    if (trimmed.startsWith("lite://")) return trimmed

    val lower = trimmed.lowercase()
    val blockedSchemes = listOf("javascript:", "file:", "content:", "data:")
    if (blockedSchemes.any { lower.startsWith(it) }) {
        return safeSearchUrl(trimmed, pattern)
    }

    if (lower.startsWith("http://")) {
        return "https://" + trimmed.substringAfter("://")
    }
    if (lower.startsWith("https://")) return trimmed

    if ((trimmed.contains(".") || trimmed.startsWith("localhost")) && !trimmed.contains(" ") && trimmed.length > 3) {
        return "https://$trimmed"
    }

    return safeSearchUrl(trimmed, pattern)
}

private fun safeSearchUrl(query: String, pattern: String): String {
    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
    val candidate = if (pattern.contains("%s")) pattern.replace("%s", encodedQuery) else "https://duckduckgo.com/?q=$encodedQuery"
    return if (candidate.startsWith("https://")) candidate else "https://duckduckgo.com/?q=$encodedQuery"
}

// Low-level helper block for older jetpack compose dependency mappings
@Composable
fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = textStyle,
        decorationBox = { innerTextField ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.fillMaxHeight()
            ) {
                innerTextField()
            }
        }
    )
}

// AI Chat Provider class & data
data class AIChatHubProvider(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val brandColor: Color,
    val category: String,
    val badge: String,
    val note: String
)

val aiChatHubProvidersList = listOf(
    AIChatHubProvider(
        id = "gemini",
        name = "Google Gemini",
        description = "Advanced multimodal models by Google for reasoning, writing, and direct workspace connectivity.",
        url = "https://gemini.google.com",
        brandColor = Color(0xFF1E88E5),
        category = "Multimodal",
        badge = "Google",
        note = "Seamlessly integrates with Google services & Search."
    ),
    AIChatHubProvider(
        id = "chatgpt",
        name = "OpenAI ChatGPT",
        description = "World's leading conversational AI with custom GPTs, rich voice mode, and memory capabilities.",
        url = "https://chatgpt.com",
        brandColor = Color(0xFF10A37F),
        category = "General",
        badge = "OpenAI",
        note = "Excellent for broad creative work & reasoning."
    ),
    AIChatHubProvider(
        id = "claude",
        name = "Anthropic Claude",
        description = "A deeply intelligent, articulate assistant built with security and long-context processing.",
        url = "https://claude.ai",
        brandColor = Color(0xFFD97706),
        category = "General",
        badge = "Anthropic",
        note = "Industry-standard for precision writing & coding."
    ),
    AIChatHubProvider(
        id = "deepseek",
        name = "DeepSeek Chat",
        description = "Advanced reasoning, engineering & coding chat powered by DeepSeek-R1 logic engines.",
        url = "https://chat.deepseek.com",
        brandColor = Color(0xFF2563EB),
        category = "Logic & Code",
        badge = "DeepSeek",
        note = "Incredibly fast and competitive reasoning benchmark."
    ),
    AIChatHubProvider(
        id = "copilot",
        name = "Microsoft Copilot",
        description = "Intelligent companion integrated with Microsoft 365, web sourcing, & DALL-E image generation.",
        url = "https://copilot.microsoft.com",
        brandColor = Color(0xFF0078D4),
        category = "Productivity",
        badge = "Microsoft",
        note = "Perfect for finding structured web citations."
    ),
    AIChatHubProvider(
        id = "perplexity",
        name = "Perplexity AI",
        description = "Conversational search model compiling verified web search resources with direct citations.",
        url = "https://perplexity.ai",
        brandColor = Color(0xFF0D9488),
        category = "Research",
        badge = "Search",
        note = "Saves hours of browsing search engine link lists."
    ),
    AIChatHubProvider(
        id = "huggingchat",
        name = "HuggingChat",
        description = "Leading open-source conversational chat with a curated directory of open-weights community models.",
        url = "https://huggingface.co/chat",
        brandColor = Color(0xFFFBBF24),
        category = "Open Source",
        badge = "Community",
        note = "Fully customizable models including Llama, Command-R, etc."
    )
)

@Composable
fun AIChatHubDialog(
    onDismiss: () -> Unit,
    onOpenUrl: (String, Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Multimodal", "General", "Logic & Code", "Productivity", "Research", "Open Source")

    val filteredProviders = aiChatHubProvidersList.filter { provider ->
        val matchesSearch = provider.name.contains(searchQuery, ignoreCase = true) ||
                provider.description.contains(searchQuery, ignoreCase = true) ||
                provider.badge.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedCategory == "All" || provider.category == selectedCategory
        matchesSearch && matchesCategory
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Title and Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Chats Explorer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Dialog",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search virtual assistants...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("ai_hub_search_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        focusedContainerColor = MaterialTheme.colorScheme.background
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable category filters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // List of filtered assistants
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (filteredProviders.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No matching AI chats found",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(filteredProviders) { provider ->
                            AIChatCardItem(
                                provider = provider,
                                onOpen = { openInNewTab ->
                                    onOpenUrl(provider.url, openInNewTab)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                // Footer note
                Text(
                    text = "Tip: Access AI Chat Hub instantly from the main toolbar sparkle icon at any time.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun AIChatCardItem(
    provider: AIChatHubProvider,
    onOpen: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_provider_card_${provider.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Icon + Name + Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(provider.brandColor.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = provider.name.take(1).uppercase(),
                            color = provider.brandColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = provider.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = provider.url,
                            fontSize = 10.sp,
                            color = provider.brandColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Custom Group Badge
                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = provider.badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body Description
            Text(
                text = provider.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            if (provider.note.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = provider.note,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive Actions Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Open in Current Tab Action
                TextButton(
                    onClick = { onOpen(false) },
                    modifier = Modifier
                        .height(36.dp)
                        .padding(end = 6.dp)
                        .testTag("ai_provider_card_open_current_${provider.id}")
                ) {
                    Text(
                        "Open Here",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Open in New Tab Action
                Button(
                    onClick = { onOpen(true) },
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("ai_provider_card_open_new_${provider.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = provider.brandColor
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "New Tab",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
