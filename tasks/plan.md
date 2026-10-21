# OmaApp implementation plan

## Product shape

OmaApp has two installable components: an Android APK and one Omarchy plugin. The Android host uses Qt Quick, C++, and QML. The plugin runs inside Omarchy's Quickshell process and owns its Python HTTPS helper. The app does not include a separate companion service, a Linux guest, PRoot, or a Wayland compositor.

The Android launcher and arm64 debug packaging already exist. Direct QR pairing and live theme sync are implemented; desktop and arm64 builds pass in CI. The launcher has lazy Android app icons, persistent pins, and local app folders. Source implementation, CI build, emulator smoke test, and physical-device test are separate checkpoints; a completed item below does not imply device validation unless it says so.

## Current pairing architecture

- The plugin shows a three-minute, one-use QR code with the computer URL and certificate fingerprint.
- OmaApp scans the QR, pins the certificate, and requests pairing over HTTPS.
- The user approves the phone in the widget. The host stores a hash of a random per-device token; Android stores the token encrypted with an Android Keystore key.
- An already trusted phone can scan a fresh QR to reconnect if the router changes the computer's address. A different certificate identity requires forgetting the old pairing and confirming a new one.
- The service reads the active Omarchy theme files and Hyprland options. Android polls every four seconds and caches the last successful theme and wallpaper.
- The protocol has no remote command, clipboard, file-transfer, notification, media, terminal, or desktop-control endpoint.

The pairing implementation is committed, and focused Python protocol tests pass locally. [GitHub Actions run 44](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/39610588557) passed desktop checks, the arm64 build, and the x86_64 emulator smoke. It verified app install and HOME selection, app enumeration and launch, persisted pins and folders, TLS-pinned pairing, host approval, theme colors/spacing/geometry/wallpaper, live theme update, reconnect, and token revocation. This machine has `adb` but no CMake, Android SDK, emulator, or attached device, so a physical phone-to-Omarchy session and GrapheneOS compatibility remain unverified.

## Work sequence

### 1. Finish pairing/theme-sync verification

- [x] Implement TLS-pinned pairing, device approval, reconnect, revocation, and private credential storage.
- [x] Implement current Omarchy color/shell reads, Hyprland geometry reads, wallpaper transfer, four-second refresh, and offline cache.
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
- [ ] Review the launcher grid and search on compact and large Android screens.
- [ ] Add home pages, configurable gestures, settings, shortcuts, and compatible Omalauncher features, preserving MIT notices.
- [ ] Port compatible OWL widgets and their editor after Android lifecycle and permission needs are defined.
- [ ] Keep theme and wallpaper controls consistent with Omarchy snapshots and accessible offline.

### 3. Improve Omarchy visual parity

- [x] Use Hyprland's superellipse rounding equation and smoothing band in the Qt shader.
- [x] Apply the active-border gradient angle and Hyprland rounding power range to the preview shader.
- [ ] Compare the shader against current Hyprland rendering for gradient stops, transforms, border behavior, and color management.
- [ ] Review QML components and portable style logic for reuse from Omarchy shell; do not port Quickshell's Linux compositor runtime into Android.

### 4. Add optional connection features

- [ ] Define a per-feature permission model and audit path before any remote operation is exposed.
- [ ] Implement clipboard, file, media, notification, and workspace features as separately authorized and tested slices.
- [ ] Consider terminal, desktop streaming, and remote commands only after the earlier transport and authorization model passes security review.

### 5. Release readiness

- [x] Build an arm64 debug APK in CI.
- [x] Configure a tag workflow that signs with the owner's existing key, verifies the signature, checksums assets, and publishes a Pre-release.
- [ ] Configure and verify the four owner-managed GitHub signing secrets.
- [ ] Publish `OmaApp-vX.Y.Z-arm64-v8a.apk` as a Pre-release, install it, and verify update-over-install with the same signing key.
- [ ] Complete license-notice, accessibility, security, and compatibility reviews before calling the release ready.

## Constraints

- The release key must remain owner-controlled and outside the repository. CI must fail closed if signing secrets are missing.
- Quickshell is a desktop Wayland shell with Linux-specific services; the Android app uses Qt Quick, and the plugin uses Quickshell.
- Any future user-data operation must be explicit, permissioned, bounded, and revocable. Do not add remote command execution to the theme-sync listener.
