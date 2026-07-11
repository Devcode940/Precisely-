# Security Policy

## Supported Status

Precisly is currently experimental / alpha software. It should not be treated as a fully audited secure browser or production password manager.

## Reporting a Vulnerability

Please report security issues privately using GitHub Security Advisories when available. If advisories are unavailable, contact the repository owner directly and avoid posting exploit details publicly until a fix is available.

Include:

- Affected version/commit
- Device and Android version
- Steps to reproduce
- Impact description
- Any proof-of-concept details

## Security Baseline

The project aims to maintain these controls:

- HTTPS-first URL handling
- Cleartext traffic disabled by default
- WebView mixed content blocked
- Dangerous URL schemes blocked from direct navigation
- WebView file/content access disabled
- App backup disabled
- Password vault encrypted with AES-GCM
- Master password key derivation with PBKDF2-HMAC-SHA256 and per-vault salt
- No hardcoded production secrets
- No JavaScript bridge unless explicitly reviewed

## Known Limitations

- Android WebView does not provide full Chrome-like per-tab incognito profile isolation on every platform version.
- The password vault has not undergone independent third-party cryptographic audit.
- The downloader and PDF renderer require broad device/network fuzz testing before production release.

## Disclosure Target

The project target is to acknowledge reports within 7 days and provide a remediation plan within 30 days for confirmed high-impact issues.
