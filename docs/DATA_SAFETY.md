# Data Safety Notes

This document helps prepare store listing disclosures. It is not legal advice.

## Data Collected by the App Developer

Currently none by default. The app does not include analytics or crash-reporting SDKs.

## Data Stored On Device

- Browsing history: local only, not stored for incognito tabs
- Bookmarks: local only
- Downloads: local app-private files and records
- Settings: local only
- Password vault: local encrypted entries
- PDF cache: local app cache

## Data Shared With Third Parties

The app itself does not intentionally share local browser/vault data with the developer. However, browsing naturally connects to third-party websites selected by the user. Those websites receive normal web request information such as IP address, user agent, cookies accepted by WebView, and request metadata.

## Encryption

Password vault entries are encrypted locally with AES-GCM. Transport security depends on websites; the app enforces HTTPS-first navigation and disables cleartext traffic by default.

## Deletion

Users can clear data through the app settings wipe option. Android app uninstall removes app-private data.
