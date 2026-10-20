# OmaApp implementation plan

## Goal

Build the two requested deliverables: one Android launcher APK and one Omarchy plugin. The first implementation checkpoint is a real Qt Quick host that can build on desktop, exposes installed Android apps through a small Java bridge, and renders rounded surfaces with a Qt ShaderEffect using Hyprland's rounding mask equation. Android packaging and the paired-device protocol follow as separate, verifiable slices.

## Architecture decisions

- Use Qt 6, C++, and QML for the Android app. Keep Android-only APIs behind a Java bridge shipped inside that APK.
- Keep Omarchy integration in one third-party Quickshell plugin. Any helper process must ship inside that plugin and follow its lifecycle.
- Keep shared protocol and geometry code under shared/. Do not introduce a separate OmaLink product.
- Reuse portable Omarchy style concepts and document every adaptation. Do not claim Quickshell code is embedded until its source is actually built into the app.
- Keep remote operations unavailable until pairing, device authorization, revocation, and audit behavior are implemented.
- Do not publish a stable APK until an owner-controlled release key is configured and the package is signed and verified.

## Work plan

### Phase 0 — Investigation and licensing

- [x] Inspect the target repository and source Omalauncher.
- [x] Inspect current Omarchy plugin contracts, Quickshell build requirements, and Hyprland shape shaders.
- [x] Record compatibility limits and licensing obligations.
- [x] Pin the Qt Android, SDK, and NDK versions in CI.

### Phase 1 — Qt Quick prototype

- [x] Create the Qt Quick app host and CMake target.
- [x] Add Android launcher intent filters and package visibility for launcher activities.
- [x] Enumerate and launch installed apps through a Java bridge.
- [x] Port Hyprland's rounded-corner mask equation and smoothing band into a reusable Qt ShaderEffect with inner/outer masks and gradient borders.
- [ ] Match compositor transforms, multi-stop OkLab gradients, ICC color management, and remaining border paths.
- [x] Add focused geometry and manifest validation checks.
- [ ] Build and run an Android APK in CI; install and launch it on an emulator or device.

### Phase 2 — Omalauncher migration

- [ ] Port the launcher’s existing home, search, theme, widget, settings, and localization behavior.
- [ ] Remove the Arch guest, PRoot, and embedded Wayland compositor from the mobile product.
- [ ] Preserve required notices and review every carried dependency and asset license.

### Phase 3 — Omarchy visual parity

- [ ] Load Omarchy theme colors and shell style values from a versioned snapshot.
- [ ] Match border widths, gradients, radius, rounding power, gaps, and opacity.
- [ ] Add wallpaper synchronization and inspect rendering on multiple pixel densities.
- [ ] Record which Quickshell and Omarchy files are reused, adapted, or excluded.

### Phase 4 — Omarchy plugin and pairing

- [ ] Package one manifest-valid Omarchy service plugin with an install/update guide.
- [ ] Define a versioned protocol with strict input limits and stable error responses.
- [ ] Implement pinned encrypted pairing, per-device authorization, revocation, and emergency disconnect.
- [ ] Keep the plugin’s helper process and lifecycle inside the plugin package.
- [ ] Add secure theme and wallpaper synchronization.

### Phase 5 — Desktop features

- [ ] Add clipboard, files, notifications, media, workspace, and system status features behind per-device permissions.
- [ ] Add remote terminal, desktop, and command features only with explicit confirmation and allowlisted operations.
- [ ] Add plugin compatibility and automation APIs after threat review.

### Phase 6 — Release readiness

- [ ] Complete security review, accessibility review, localization, documentation, and screenshots.
- [ ] Run unit, integration, lint, security, Android install, and launch checks.
- [ ] Configure owner-managed release signing secrets and test update compatibility.
- [ ] Publish a signed APK and compatible plugin archive with checksums and changelog.

## Risks and checkpoints

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Quickshell is built around desktop Wayland and private Qt APIs | Full Quickshell reuse in an Android APK may not be viable | Keep the Android host on Qt Quick; use Quickshell in the Omarchy plugin; test portable source modules before reuse |
| Qt for Android SDK, JDK, and Android SDK are absent locally | APK compilation cannot be verified in this workspace | Add a pinned CI toolchain and report local limitations clearly |
| Remote-control features increase device compromise impact | Critical | Ship no remote command surface before pairing, permissions, confirmation, revocation, and audit checks exist |
| Release signing key is not available in this workspace | No safe stable release can be published | Use repository secrets; never substitute a debug key for a release key |

## Verification checkpoint

The first checkpoint is complete only when desktop CMake build, QML lint, C++ geometry tests, Python manifest tests, and Omarchy manifest validation pass. Android packaging remains a separate checkpoint and must produce an installable, signed APK before a release is called ready.
