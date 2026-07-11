# Precisly Release Checklist

Use this checklist before any public release.

## Build Gates

- [ ] Use JDK 17 or newer
- [ ] `./gradlew clean`
- [ ] `./gradlew lint`
- [ ] `./gradlew testDebugUnitTest`
- [ ] `./gradlew assembleDebug`
- [ ] `./gradlew assembleRelease`
- [ ] Verify release signing uses environment variables only

## Manual QA

- [ ] Open HTTPS website
- [ ] HTTP input upgrades to HTTPS
- [ ] Dangerous schemes are blocked/searched: `javascript:`, `file:`, `content:`, `data:`
- [ ] Multiple tabs create/switch/close correctly
- [ ] Incognito tab does not write app history
- [ ] Incognito tab clears session data on close as far as WebView supports
- [ ] Bookmark add/remove works without duplicates
- [ ] History clear works
- [ ] Vault set/unlock/lock/add/delete works
- [ ] Vault rejects short master password
- [ ] Downloads work without turbo
- [ ] Downloads work with turbo on a range-supporting server
- [ ] Downloads fail gracefully on network loss
- [ ] PDF viewer renders real HTTPS PDFs
- [ ] PDF viewer rejects non-HTTPS URLs
- [ ] Rotation and background/foreground behavior tested

## Security Review

- [ ] No hardcoded secrets
- [ ] Backup disabled or sensitive files excluded
- [ ] Cleartext disabled
- [ ] WebView mixed content blocked
- [ ] No JavaScript bridge or reviewed bridge only
- [ ] Vault crypto reviewed
- [ ] Room migrations tested from previous version
- [ ] ProGuard/R8 release verified

## Play Store / Public Distribution

- [ ] Stable package name confirmed: `com.eastweblite.browser`
- [ ] Privacy policy URL available
- [ ] Data Safety answers match actual behavior
- [ ] App description does not overclaim incognito/password-manager security
- [ ] Screenshots match actual features
- [ ] Version code/name updated
- [ ] Closed beta feedback addressed
