# Feature Implementation Sequence

This document tracks feature additions in a controlled, professional order.

## Completed

### 1. Clear Browsing Data

Status: Implemented

Scope:
- Settings card for granular data clearing
- Dialog with explicit categories
- Clears history
- Clears cookies and WebView site storage
- Clears cached PDF files
- Clears download records
- Optionally deletes app-private downloaded files
- Optionally removes saved vault passwords
- Preserves bookmarks/settings unless full database wipe is selected

Files:
- `BrowserScreens.kt`
- `BrowserViewModel.kt`

### 2. Vault Auto-Lock

Status: Implemented

Scope:
- New `vault_auto_lock_seconds` setting
- Options: 30 seconds, 1 minute, 5 minutes, 15 minutes, when app backgrounds, never
- Auto-lock timer after vault unlock/master-password setup
- Timer refresh when decrypting vault entries
- Lifecycle hook to lock when app backgrounds if configured

Files:
- `BrowserScreens.kt`
- `BrowserViewModel.kt`
- `MainActivity.kt`

## Next Recommended Features

### 3. Find in Page

Status: Implemented

Scope:
- Browser menu action
- Find bar shown under toolbar
- Native `WebView.findAllAsync()` integration
- Previous/next match navigation
- Match count display
- Clears matches when closed or tabs change
- Re-runs search after page load when active

Files:
- `MainActivity.kt`

### 4. Site Info / Security Panel

Planned scope:
- Tap lock/public icon
- Show URL, HTTPS status, cookies, JavaScript, mixed-content policy
- Clear data for current site

### 5. Per-Site Permissions

Planned scope:
- Camera/microphone/location request handling
- User prompts
- Per-site allow/block list

### 6. Session Restore

Planned scope:
- Persist normal tabs
- Do not persist incognito tabs
- Restore active tab/order on launch

### 7. Bookmark Folders

Planned scope:
- Folder entity
- Assign bookmarks to folders
- Edit/move/search bookmarks
