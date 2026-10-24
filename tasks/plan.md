# OmaApp development status and next steps

## Product architecture

OmaApp remains one Android APK plus one Omarchy plugin. The Android UI uses Qt Quick, C++, QML, and a small Java bridge. The plugin runs inside Quickshell and owns its Python HTTPS helper. There is no extra Android app, desktop daemon, Linux guest, PRoot, or remote shell.

## Implemented launcher and localization

- Offline Home with multiple pages, drag and drop, dock, Android app icons/search, folders, dynamic shortcuts, configurable grid and gestures, OWL store/editor, and local backup/restore.
- Optional Android haptics for Home app launches and editing actions are off by default, follow system touch-feedback settings, and are included in launcher settings and layout backups.
- Launcher app enumeration refreshes after package lifecycle broadcasts and Activity resume. Emulator CI installs and removes a real launcher fixture.
- English and Russian use Qt Linguist `.ts`/`.qm` catalogs. Android locale selects the default; Settings persists `system`, `en`, or `ru`. CI checks catalog completeness and smoke-tests language switching, native Android messages, process restart persistence, and Russian UI accessibility.
- Phone-width OWL titles and actions wrap or stack. Russian bottom-tab labels are compact while their accessibility names remain complete.
- Ordinary Android App Widgets are not integrated into saved Home layouts. CI binds a real test provider and exercises its `RemoteViews` in an `AppWidgetHostView` overlay over QtActivity; this confirms the platform APIs work but does not provide user-managed widget placement or persistence.

## Pairing, sync, and local rediscovery

- First trust requires QR certificate review and approval on the computer. Android stores its per-device token and pinned certificate using Android Keystore encryption; the plugin stores token hashes.
- Foreground Android DNS-SD discovers `_omaapp._tcp` after pairing. The plugin advertises only its private address, port, protocol, and public TLS fingerprint. OmaApp accepts an endpoint update only after checking the saved certificate pin and authenticating HTTPS with the stored token.
- Retry and fresh-QR reconnect remain available when multicast is blocked. Discovery stops when the launcher Activity pauses. DNS-SD has plugin packet tests and Android trust-gate coverage, but Android NSD has not been tested end to end against a live Omarchy host.
- The plugin syncs Omarchy colors, `shell.toml`, wallpaper, Hyprland geometry and animations. Theme/workspace updates use file/socket events with a slower foreground refresh for missed events and metrics; cached theme remains local and usable offline.
- Clipboard, file transfer, MPRIS, metrics, workspace, and configured command operations retain separate per-device grants and bounded input validation.
- A cancelled TLS client response is treated as a disconnected peer. A regression test covers response writes, and the event long-poll integration test checks that a client RST does not log a traceback.
- OWL renderer roots and descendants both wait for non-null model bindings before QML instantiation. Android smoke fails on `OwlNode.qml` null-binding errors and retains a failure screenshot/logcat if Home is inaccessible.
- OWL nodes now guard renderer method calls during QML Loader initialization and use local fallbacks until the owning widget renderer is available. The Android error-log gate remains enabled to catch any remaining binding errors.
- OWL widget views also refresh their document when the model arrives and when the Home page makes the card visible again. CI #220 showed the installed Canvas sampler card could remain blank; the #221 emulator run checks this lifecycle fix on OpenGL and Vulkan.
- Locale and haptic persistence checks move OmaApp to Android Settings before force-stopping it, verify the process exits, and then verify saved state after relaunch. CI emits cold-start, PSS, and idle CPU data only when the cold launch and PSS samples parse successfully.

## Current verification

- Local suite: 113 Python tests pass. The Russian catalog checker validates 291 source strings; changed OWL components pass `qmllint`; shell syntax checks and `git diff --check` pass.
- CI run #218 checked a prior revision: desktop and arm64 succeeded; both emulators failed during a post-restart theme read after the test-only NSD/TLS endpoint had been stopped. The smoke now waits for that authenticated read while the fixture remains alive. CI #220 then passed desktop and arm64 but both Android renderers found a blank Canvas sampler; [CI #221](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/40131445004) checks the lifecycle refresh change.
- Earlier CI #201 exercised launcher interactions, English/Russian UI, OWL store/editor and backup, pairing reconnect after plugin restart, permissions, and revocation. Its renderer screenshots remain checked in under `docs/screenshots/`.
- The plugin was installed and restarted earlier in a real Omarchy 4.0.4 / Quickshell 0.3.1 session. This does not verify the Android DNS-SD change against a live Omarchy host.
- This workspace lacks CMake, Qt ShaderTools, and the Android/JDK toolchain. Full local Android/desktop builds and rendering are therefore not claimed. No Pixel 10 / GrapheneOS device is attached.

## Remaining work

1. Exercise Android NSD reconnect, IP changes, and wrong-fingerprint rejection end to end against a live Omarchy host when a suitable network fixture is available.
2. Design and test a supported Qt-to-native placement/lifecycle bridge before integrating ordinary Android App Widgets into persistent Home layouts.
3. Run the physical Pixel 10 / GrapheneOS checklist in `docs/GRAPHENEOS.md`; report installation, HOME, camera, discovery, TLS, sync, battery, and performance only after device testing.
4. Review and merge through the PR. Publish a signed pre-release only through the existing workflow with the owner's permanent GitHub signing secrets.

## Release and safety constraints

- Never add or commit a production signing key. The release workflow must stop without the owner's permanent signing secrets.
- Preserve Simon Schubert's Omalauncher attribution and all Qt, OWL, and Hyprland notices.
- Offline OWL does not execute Linux commands. The pairing protocol does not accept arbitrary shell commands.
