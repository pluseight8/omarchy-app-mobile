# OmaApp task list

## Android launcher

- [x] Expose Android launcher activities and launch the selected app through `PackageManager`.
- [x] Register OmaApp as a selectable Android HOME app.
- [x] Add the in-app QR scanner and runtime-only camera permission.
- [x] Keep the desktop preview honest: it does not invent an installed-app list.
- [x] Build an arm64 debug APK in GitHub Actions (`2630521`; artifact `OmaApp-ci-debug-arm64`).
- [x] Add an x86_64 emulator smoke check for APK install, app enumeration, launching an app, and selecting OmaApp as HOME.
- [x] Build the pairing/theme-sync implementation for arm64 and desktop in GitHub Actions (`2630521`).
- [x] Add lazy-loaded Android app icons, persistent pinned launcher activities, and refresh control; local C++ compile, QML lint/format, and plugin tests pass.
- [x] Review emulator run `39490160786`: APK installation, HOME selection, 8-app enumeration, icon caching, pin persistence across process restart, app launch, and return to HOME all passed before pairing smoke.
- [x] Add persisted app folders with create, rename, add/remove installed apps, and delete actions; local Qt model tests and QML lint pass.
- [ ] Verify folder creation, app membership, and folder persistence in the Android emulator smoke job.
- [ ] Verify the CI Android pairing harness completes TLS pairing, theme/geometry/wallpaper sync, reconnect, and revocation.
- [ ] Repeat install/launcher/pairing checks on GrapheneOS or another physical Android device.

## Secure Omarchy pairing and live theme sync

- [x] Keep the HTTPS helper and QR encoder inside the one Omarchy plugin package.
- [x] Add short-lived QR pairing, certificate pinning, two-sided approval, per-device tokens, encrypted Android storage, and revocation.
- [x] Add token-authenticated re-pairing after the computer's local address changes.
- [x] Read current Omarchy theme files and Hyprland values through `hyprctl -j getoption`.
- [x] Sync supported colors, active border gradient, wallpaper, border width, rounding, rounding power, and spacing; retain the last snapshot offline.
- [x] Add HTTPS protocol tests for approval, rejection, expiry, reconnect, limits, theme parsing, authentication, and revocation.
- [x] Run all available local repository checks after this change; Python tests, plugin validation, shell syntax, QML lint, Python compilation, and whitespace checks pass.
- [ ] Verify current desktop and Android jobs in GitHub Actions.
- [ ] Test real phone ↔ Omarchy exchange and observe theme refresh after changing a theme.

## Remaining product work

- [ ] Port Omalauncher home pages, configurable gestures, OWL widgets, editor, shortcuts, and settings, keeping required notices.
- [ ] Add visual comparisons for the Hyprland shader's gradients, transforms, and color handling.
- [ ] Add permissioned clipboard, file, media, notification, workspace, terminal, desktop, and command features in reviewed slices.
- [ ] Configure the owner's release-signing secrets; publish and verify the first signed Pre-release and update-over-install.

## Environment limits at this checkpoint

- This workspace has no CMake, JDK, Android SDK, Android emulator, or attached device; `adb`, Qt desktop libraries, QtTest, and `qmllint` are installed.
- Release signing secrets cannot be read through the available repository connector. Do not claim that a signed release is available until the release workflow succeeds.
