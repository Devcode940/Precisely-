# Precisly Privacy Policy

_Last updated: 2026-07-11_

Precisly is an experimental Android WebView browser. This policy describes the data handled by the app.

## Data Stored Locally

The app may store the following on your device:

- Bookmarks
- Browsing history, except for incognito tabs
- Download records and files saved to the app-private downloads directory
- App settings
- Password-vault entries encrypted locally with a master password-derived key
- Cached PDF files opened in the built-in PDF viewer

## Password Vault

Saved vault passwords are encrypted locally using AES-GCM. The master password itself is not stored. A salted PBKDF2-derived verifier is stored to validate unlock attempts.

Important: because this is experimental software, do not use the vault as your only password manager until the app has undergone independent security review.

## Incognito Tabs

Incognito tabs avoid writing app-level browsing history and attempt to clear tab WebView cache/history/form data and session cookies when the tab is closed. Android WebView may still share some process-level/browser-engine state depending on OS and WebView implementation.

## Network Activity

When you browse, download files, or open a PDF URL, your device connects directly to the websites you visit. Those websites, your network provider, DNS resolver, and Android System WebView may process network metadata.

## Third-Party Services

The app includes shortcuts to external AI/chat/search websites. Opening those websites is subject to their privacy policies.

## Backups

Android app backup is disabled in the manifest to reduce risk of sensitive browser/vault data being copied to cloud backup.

## Crash / Analytics

No crash reporting or analytics SDK is currently included. If added later, this policy must be updated before release.

## Contact

For privacy or security issues, open a private security report on the project repository or contact the maintainer listed in `SECURITY.md`.
