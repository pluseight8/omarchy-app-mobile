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
- [x] Persist a versioned model with provider ID, page, order, 24 dp snapped x/y position, minimum and selected size; migrate unversioned/schema-2 records and restore widget IDs after process death.
- [x] Add per-page grid placement, cross-page drag/drop, overlap rejection, accessible position controls, and provider-constrained width/height adjustment in Home.
- [x] Add a native Android corner resize grip that changes dimensions without disabling RemoteViews touch outside the grip.
- [x] Export provider/layout metadata without Android binding IDs; restore placeholders and require system-picker rebind with provider identity matching.
- [x] Persist removal before releasing a widget ID and roll back the record if release fails.
- [x] Keep the existing offline OWL widget list available below native Android widgets.
- [x] Extend real-provider emulator instrumentation to verify cross-page grid coordinates, off-grid/bounds rejection, native resize-grip drag, Android size options, ID-free backup, placeholder restore, picker rebind, and fresh binding IDs. The updated implementation still needs a new integrated CI run.
- [ ] Share one placement grid between Android widgets, app icons, and OWL widgets; they currently occupy separate Home areas.
- [ ] Validate touch, accessibility, gestures, insets, and GrapheneOS behavior on a physical device.
- [ ] Measure memory and frame performance across widget sizes, orientation changes, and available display refresh rates.

## Environment limits at this checkpoint

- This workspace has no CMake, Android SDK, emulator, or attached device. Android builds and provider selection must be verified in GitHub Actions.
- Release signing secrets cannot be read through the available repository connector. Do not claim that a signed release is available until the release workflow succeeds.

## Candidate secure connection stages

- [ ] Android notification forwarding with the system's explicit Notification Access grant plus a separate per-device Omarchy permission and content-redaction policy.
- [ ] Multiple trusted computers with versioned Keystore entries and independent TLS pins, tokens, permissions, status, reconnect, and revocation.
- [ ] File-transfer queue with bounded storage, progress, cancel, and explicit completion/error states.
- [ ] A fuller MPRIS page backed by the existing permissioned player API.
- [ ] Quick Actions that call user-configured command IDs only; never accept arbitrary command strings.
- [ ] Phone battery status with a new host grant, rate limit, bounded schema, and expiry on disconnect.

Remote desktop and terminal access remain deferred until the simpler features have independent integration tests and security review.
