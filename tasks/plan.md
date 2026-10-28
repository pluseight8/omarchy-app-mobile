# OmaApp implementation plan

## Product shape

OmaApp has one Android APK and one Omarchy plugin. The Android host uses Qt Quick, C++, QML, and its native Android bridge. The plugin runs inside Omarchy's Quickshell process and owns its Python HTTPS helper. The app does not include a separate companion app or service, a Linux guest, PRoot, or a Wayland compositor.

The Android launcher and arm64 debug packaging already exist. Direct QR pairing and live theme sync are implemented. The launcher has lazy Android app icons, app search, a pinned dock, local folders, adjustable grid columns, and swipe navigation. Source implementation, CI build, emulator smoke test, and physical-device test are separate checkpoints; a completed item below does not imply device validation unless it says so.

## Current pairing architecture

- The plugin shows a three-minute, one-use QR code with the computer URL and certificate fingerprint.
- OmaApp scans the QR, pins the certificate, and requests pairing over HTTPS.
- The user approves the phone in the widget. The host stores a hash of a random per-device token; Android stores the token encrypted with an Android Keystore key.
- An already trusted phone can scan a fresh QR to reconnect if the router changes the computer's address. A different certificate identity requires forgetting the old pairing and confirming a new one.
- The service reads active Omarchy theme files and Hyprland options/animations. Android applies authenticated theme/workspace events and uses a 30-second foreground refresh for missed events and system metrics.
- Clipboard, file-transfer, MPRIS, system metrics, Hyprland workspaces, and user-configured commands have independent, disabled-by-default per-device grants.

PR #16 was merged into `main` at `bf4baef832968383d1d1a3ac29abc61f0f15e6af`; its [CI #266](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/40210984247) passed all four jobs and its review threads were resolved. PR #17 remains Draft. Its latest remote [CI #311](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/40411514366) on `4685588` passed Desktop and arm64, while OpenGL and Vulkan emulator smoke failed. Local follow-ups add active-window navigation checks and preserve approved pairing handshakes across new QR sessions; they require a fresh four-job CI run after push. The local suite has 175 Python tests. This workspace has no CMake, Android SDK, emulator, or attached device; Pixel/GrapheneOS and live Android-to-Omarchy checks remain unverified here.

## Work sequence

### 1. Finish pairing/theme-sync verification

- [x] Implement TLS-pinned pairing, device approval, reconnect, revocation, and private credential storage.
- [x] Implement current Omarchy color/shell reads, Hyprland geometry reads, wallpaper transfer, four-second refresh, and offline cache.
- [x] Expand shell.toml, Hyprland animations, and event-driven theme/workspace sync with a slow fallback.
- [x] Install and restart the plugin inside Omarchy 4.0.4 / Quickshell 0.3.1. Move uninstall backups outside Quickshell's plugin scan to prevent duplicate plugin IDs and stale helper paths.
- [x] Add Linux integration tests for the helper process, TLS, saved grants, inotify events, and revocation after restart.
- [x] Update plugin install checks, security documentation, and current task status.
- [x] Run Python protocol tests, shell syntax, manifest validation, and QML lint locally.
- [x] Build the arm64 Android app in CI and fix Java/QML/C++ issues found there.
- [x] Add an x86_64 emulator smoke test for install, launch, app enumeration, app launch, and HOME role selection.
- [x] Review the emulator launcher result: install, enumeration, HOME, icons, persisted pin, app launch, and HOME return all passed in CI.
- [x] Complete the CI pairing/theme-sync/reconnect/revoke smoke in the Android emulator.
- [ ] Verify phone-to-computer pairing and theme refresh on a physical Android device connected to an Omarchy session.

### 2. Improve daily Android launcher use

- [x] Add cached Android app icons, persistent pinned launcher activities, and refresh control.
- [x] Add persisted app folders with create, rename, app membership, and delete operations.
- [x] Verify icon loading and pinned-app persistence in emulator run `39490160786`.
- [x] Verify app-folder UI and persistence in the [Android emulator smoke job](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/39610588557).
- [x] Add a persisted 3–5 column grid setting, dock, and swipe navigation between the four app sections.
- [x] Add Android screenshot capture and pixel-diff regression checks for Apps, Devices, Style, and Settings.
- [ ] Review adaptive layout on compact and large physical Android screens.
- [ ] Port multiple app home pages, dynamic shortcuts, and the remaining compatible Omalauncher features, preserving MIT notices.
- [ ] Port compatible OWL widgets and their editor; the upstream language/runtime is a separate Kotlin interpreter and needs a scoped Qt/Android implementation without its Linux guest.
- [ ] Keep theme and wallpaper controls consistent with Omarchy snapshots and accessible offline.

### 3. Improve Omarchy visual parity

- [x] Implement and unit-test the Hyprland superellipse geometry reference in C++.
- [x] Synchronize Hyprland rounding, rounding_power, and border-gradient values to Android.
- [x] Replace the Android ShaderEffect surface after CI screenshots exposed diagonal artifacts; use native Qt Quick rectangles.
- [ ] Render the C++ superellipse path on Android without shader artifacts and restore arbitrary border-gradient angles.
- [ ] Compare Android and Omarchy surfaces for gradient stops, transforms, border behavior, and color management.
- [ ] Review QML components and portable style logic for reuse from Omarchy shell; do not port Quickshell's Linux compositor runtime into Android.

### 4. Add optional connection features

- [x] Define independent per-feature grants and an explicit host config for commands.
- [x] Implement clipboard, file, MPRIS, system metrics, Hyprland workspace, and configured-command APIs with input limits and Linux tests.
- [ ] Complete end-to-end Android emulator tests for each optional feature; the current emulator workflow still focuses on pairing, theme sync, and launcher persistence.
- [ ] Consider terminal, desktop streaming, and remote commands only after the earlier transport and authorization model passes security review.

### 5. Release readiness

- [x] Build an arm64 debug APK in CI.
- [x] Configure the existing tag workflow to sign with the owner's key, verify the signature, exercise install/launch/update on an emulator, checksum assets, and publish a Pre-release.
- [ ] Configure and verify the four owner-managed GitHub signing secrets.
- [ ] Publish `OmaApp-vX.Y.Z-arm64-v8a.apk` as a Pre-release, install it, and verify update-over-install with the same signing key.
- [ ] Complete license-notice, accessibility, security, and compatibility reviews before calling the release ready.

### 6. Android AppWidgetHost integration

- [x] Keep the first native Android widget path inside the existing OmaApp APK and Quickshell plugin architecture.
- [x] Launch Android's system picker and provider configuration flow; do not bind providers without the user's picker choice.
- [x] Restore persisted provider IDs through one stable `AppWidgetHost` and release cancelled, stale, and deleted IDs.
- [x] Align interactive `AppWidgetHostView` children with first-page Qt Quick Home slots, convert dp bounds, and clip them to the scroll/page viewport.
- [x] Save provider identity, local Android ID, page/order, 24 dp snapped x/y, minimum size and selected size in a versioned schema; migrate the former array and schema-2 records.
- [x] Add per-page grid placement, drag-to-position/page, overlap checks, accessible movement controls, and width/height adjustment controls without disabling RemoteViews interaction outside the resize grip.
- [x] Add an Android-native corner resize grip that updates provider size options.
- [x] Add ID-free backup export, placeholder restore, provider-matched system-picker rebind, and safe remove ordering with metadata rollback.
- [ ] Run the updated real-provider emulator smoke for grid placement, native handle resizing, options updates, ID-free backup, placeholder restore and rebind after pushing the grid follow-up.
- [ ] Unify the Android widget, app-icon, and OWL widget placement grids; these areas are currently separate.
- [ ] Measure Home performance and check gestures, accessibility, orientation/insets, and GrapheneOS with a physical device.
- [ ] Measure widget memory and frame cost across display densities, rotation, and the emulator's available refresh rates.

### 7. Safe connection feature follow-ups

Keep each capability independently permissioned on the Omarchy plugin, bounded in the protocol, revocable per paired phone, and covered by host integration plus Android end-to-end tests.

- [ ] Android-to-Omarchy notification forwarding: require the user's Android Notification Access grant and an independent per-device host permission; define app/category filtering and redact notification text from logs.
- [ ] Multiple trusted computers: replace the single saved credential record with versioned per-host Keystore entries and a device picker; preserve separate tokens, TLS pins, permissions, reconnect queues, and revocation per host.
- [ ] File transfer queue: build on the existing picker-based transfers with a bounded queue, byte progress, cancellation, resumability policy, and explicit destination/result states.
- [ ] MPRIS controls: improve the existing feature UI with player selection and truthful action/metadata state; keep its existing independent permission and protocol allowlist.
- [ ] Quick Actions: expose user-selected IDs from the existing allowlisted command catalog; do not accept command text or shell syntax from the phone, and retain a separate command grant.
- [ ] Phone battery status on Omarchy: add an explicit phone-to-host status permission, a rate-limited schema with coarse charge/charging state, and clear deletion/expiry behavior when the phone disconnects.

Remote desktop and terminal access are deferred until these smaller capabilities and their security reviews are complete.

## Constraints

- The release key must remain owner-controlled and outside the repository. CI must fail closed if signing secrets are missing.
- Quickshell is a desktop Wayland shell with Linux-specific services; the Android app uses Qt Quick, and the plugin uses Quickshell.
- Any future user-data operation must be explicit, permissioned, bounded, and revocable. Do not add remote command execution to the theme-sync listener.
