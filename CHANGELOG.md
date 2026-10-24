# Changelog

## Unreleased

- Added English and Russian Qt Linguist catalogs, system-language detection, an in-app language selector, and a CI check for missing translations.
- Refresh launcher activities after protected Android package install, replace, and removal events; exercise the behavior by installing and removing an Android launcher fixture in emulator CI.
- Add a CI-only proof of concept for binding and hosting a real Android AppWidget over the Qt activity. User-managed widgets are not yet part of persisted Home layouts.
- Add DNS-SD discovery for the already QR-trusted Omarchy plugin. Endpoint changes require the pinned TLS certificate and the saved device token; retry remains available when multicast discovery is blocked.
- Extend plugin packet tests for DNS-SD records, malformed queries, record expiry, and the absence of pairing credentials in advertisements.
- Add optional Android haptic feedback for Home app launches and editing actions. It uses `View.performHapticFeedback`, is disabled by default, follows system touch-feedback settings, and is persisted in launcher settings and layout backups.

Android emulator proof of concept and UI smoke checks require the current GitHub Actions run. Physical Pixel/GrapheneOS testing remains outstanding. The full local Android build is not available in this workspace because it lacks the Android SDK/JDK and Qt ShaderTools package.
