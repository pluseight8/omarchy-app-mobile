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
- [x] Verify folder creation, app membership, and persistence after restart in the [Android emulator smoke job](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/39610588557).
- [x] Verify CI TLS pairing, theme/geometry/wallpaper sync, live update, reconnect, and revocation in [emulator run 44](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/39610588557).
- [ ] Repeat install/launcher/pairing checks on GrapheneOS or another physical Android device.

## Secure Omarchy pairing and live theme sync

- [x] Keep the HTTPS helper and QR encoder inside the one Omarchy plugin package.
- [x] Add short-lived QR pairing, certificate pinning, two-sided approval, per-device tokens, encrypted Android storage, and revocation.
- [x] Add token-authenticated re-pairing after the computer's local address changes.
- [x] Read current Omarchy theme files and Hyprland values through `hyprctl -j getoption`.
- [x] Sync supported colors, active border gradient, wallpaper, border width, rounding, rounding power, and spacing; retain the last snapshot offline.
- [x] Add HTTPS protocol tests for approval, rejection, expiry, reconnect, limits, theme parsing, authentication, and revocation.
- [x] Run all available local repository checks after this change; Python tests, plugin validation, shell syntax, QML lint, Python compilation, and whitespace checks pass.
- [x] Verify desktop, arm64 build, and x86_64 Android emulator jobs in [GitHub Actions run 44](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/39610588557).
- [ ] Test real phone ↔ Omarchy exchange and observe theme refresh after changing a theme.

## Remaining product work

- [ ] Port Omalauncher home pages, configurable gestures, OWL widgets, editor, shortcuts, and settings, keeping required notices.
- [ ] Add visual comparisons for the Hyprland shader's gradients, transforms, and color handling.
- [ ] Add permissioned clipboard, file, media, notification, workspace, terminal, desktop, and command features in reviewed slices.
- [ ] Configure the owner's release-signing secrets; publish and verify the first signed Pre-release and update-over-install.

## Android AppWidgetHost (separate technical stage)

- [x] Add the first product `AppWidgetHost` integration inside OmaApp's existing `QtActivity`; keep the Android provider fixture out of the product manifest.
- [x] Use the system widget picker, run provider configuration activities, and release cancelled or removed widget IDs.
- [x] Bridge live Home slot coordinates and QML viewport clipping to interactive `AppWidgetHostView` instances.
- [x] Persist provider IDs and ordered first-page placement in the app's private storage; restore IDs after Activity/process recreation and discard stale providers.
- [x] Keep the existing offline OWL widget list available below native Android widgets.
- [x] Pass the CI integration that binds, clicks, process-restores, and removes a real test-only RemoteViews provider on OpenGL and Vulkan in [run #270](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/40212669660).
- [ ] Add drag/reorder, resize handles, per-page placement, layout backup/restore, and lifecycle/performance measurements in follow-up slices.
- [ ] Validate touch, accessibility, gestures, insets, and GrapheneOS behavior on a physical device.
- [ ] Measure memory and frame performance across widget sizes, orientation changes, and available display refresh rates.

## Environment limits at this checkpoint

- This workspace has `adb`, `ninja`, and `qmllint`, but no CMake, JDK, Android SDK, emulator, or attached device. Android builds and provider selection must be verified in GitHub Actions.
- Release signing secrets cannot be read through the available repository connector. Do not claim that a signed release is available until the release workflow succeeds.

## Candidate secure connection stages

- [ ] Android notification forwarding with the system's explicit Notification Access grant plus a separate per-device Omarchy permission and content-redaction policy.
- [ ] Multiple trusted computers with versioned Keystore entries and independent TLS pins, tokens, permissions, status, reconnect, and revocation.
- [ ] File-transfer queue with bounded storage, progress, cancel, and explicit completion/error states.
- [ ] A fuller MPRIS page backed by the existing permissioned player API.
- [ ] Quick Actions that call user-configured command IDs only; never accept arbitrary command strings.
- [ ] Phone battery status with a new host grant, rate limit, bounded schema, and expiry on disconnect.

Remote desktop and terminal access remain deferred until the simpler features have independent integration tests and security review.
