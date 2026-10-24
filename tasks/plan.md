# OmaApp development status and next steps

## Product architecture

OmaApp remains one Android APK plus one Omarchy plugin. The Android UI uses Qt Quick, C++, QML and a small Java bridge. The plugin runs inside Quickshell and owns its Python HTTPS helper. There is no extra Android app, desktop daemon, Linux guest, PRoot, or remote shell.

## Implemented launcher and localization

- Offline Home with multiple pages, drag and drop, dock, Android app icons/search, folders, dynamic shortcuts, configurable grid and gestures, OWL store/editor, and local backup/restore.
- Optional Android haptic feedback for Home app launches and editing actions is off by default, follows system touch-feedback settings, and is included in launcher settings and layout backups.
- Launcher app enumeration refreshes after package lifecycle broadcasts and Activity resume. Emulator CI installs and removes a real launcher fixture to exercise package discovery.
- English and Russian use a Qt Linguist `.ts`/`.qm` catalog. Android locale selects the default; Settings can persist `system`, `en`, or `ru`. CI verifies the source catalog and emulator smoke checks Russian UI, Android-native dialogs/messages, process restart persistence, and switching back to English.
- Ordinary Android App Widgets are not yet integrated into Home. CI's test-only `AppWidgetHost` proof binds a real widget provider, shows its live `RemoteViews` in an `AppWidgetHostView` above the Qt activity, taps it, and frees the allocated ID. The proof documents that this overlay does not participate in the Qt/QML layout tree.

## Pairing, sync, and local rediscovery

- The first trust relationship still requires QR certificate review and approval on the computer. Android stores the per-device token and pinned certificate using Android Keystore encryption; the plugin stores only token hashes.
- While a paired app is foregrounded, Android DNS-SD discovers `_omaapp._tcp`. The plugin advertises only its private address, port, protocol, and public certificate fingerprint. OmaApp accepts an endpoint update only after comparing the advertised fingerprint to its saved pin and completing authenticated HTTPS with the pinned certificate. mDNS is a location hint, never a trust grant.
- `Retry` and fresh-QR reconnect remain available if multicast is blocked. Discovery resources are stopped when the launcher Activity pauses.
- The plugin syncs Omarchy colors, `shell.toml`, wallpaper, Hyprland geometry and animations. Theme/workspace updates use file/socket events with a 30-second foreground fallback for missed events and metrics; cached theme remains local and usable offline.
- Optional clipboard, file transfer, MPRIS, metrics, workspace, and configured command operations retain separate per-device grants and bounded input validation.

## Current verification

- Local repository suite: 102 Python tests passed. The 286-entry Russian catalog check, shell/Python syntax checks, Android version-code regression test, and whitespace validation also pass.
- CI #195 passed desktop, arm64, OpenGL, and Vulkan. Both emulator artifacts show successful Android haptic API dispatch, the opt-in checkbox still enabled after Activity restart, Russian and English UI checks, launcher refresh, and the AppWidgetHost proof. The run also confirmed the deterministic Settings launch target and the fixed scrolling tap coordinates.
- CI #196 passed desktop and arm64, but both emulator jobs stopped at the new OWL metadata visibility check. Its Vulkan artifact confirmed that Russian translations reached the OWL action labels; the title and description Text controls lacked accessibility names, so the exact text was not exposed to the smoke test (or accessibility services). The follow-up now exposes each Text value through `Accessible.name` and retains exact Russian UI assertions.
- The plugin has previously been installed/restarted in Omarchy 4.0.4 / Quickshell 0.3.1. That does not substitute for a fresh test of this branch's mDNS change.
- This workspace has no JDK or Android SDK/NDK and lacks Qt ShaderTools development files, so the full build and emulator render cannot be verified locally. No Pixel 10 / GrapheneOS device is attached.

## Remaining work

1. Confirm CI for the OWL localization/accessibility follow-up; inspect the Russian OWL store screenshot and check that the prior English deletion message is cleared after switching languages.
2. Add runtime Android DNS-SD discovery/reconnect coverage with a controlled plugin fixture if the emulator network can carry multicast reliably. Exercise IP changes and wrong-fingerprint rejection with real TLS test traffic.
3. Design a supported native-to-QML widget placement bridge before exposing ordinary AppWidgets to users; persist IDs/positions, resize/configure, restore, drag and teardown all require lifecycle coverage.
4. Run the physical Pixel 10 GrapheneOS checklist in `docs/GRAPHENEOS.md`; report installation, HOME, camera, discovery, TLS, theme sync, battery, and performance observations only after the device is available.
5. Review and merge through the current PR. Keep signing credentials in owner-controlled GitHub secrets and use only the existing release workflow for a signed Pre-release.

## Release constraints

- Never add or commit a production signing key. The release workflow must stop without the owner's permanent signing secrets.
- Preserve Simon Schubert's Omalauncher attribution and all Qt, OWL, and Hyprland notices.
- No arbitrary Linux command execution from offline OWL or the pairing protocol.
