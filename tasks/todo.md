# OmaApp task list

## Current checkpoint — Qt Quick prototype

- [x] Read the attached product requirements.
- [x] Inspect upstream Omalauncher, Omarchy shell plugins, Quickshell, and Hyprland shader sources.
- [x] Record platform and license findings.
- [x] Implement the Qt Quick launcher shell and Android app-list bridge.
- [x] Port the Hyprland rounding mask and smoothing equation into a Qt ShaderEffect with scalable inner and outer masks.
- [ ] Add visual regression captures against Hyprland for gradients, transform paths, and color management.
- [x] Add Android package template and HOME/launcher intent filters.
- [x] Add the first Omarchy plugin manifest, installer, and uninstaller.
- [x] Add CI for desktop verification and Android arm64 packaging.
- [x] Run available local checks and review the source changes.
- [ ] Build the CMake/QSB app target and verify Android APK packaging in CI.
- [ ] Install and launch on GrapheneOS or an Android emulator.
- [ ] Push the initial implementation to the target repository.

## Next checkpoints

- [ ] Migrate Omalauncher features and assets after the Qt prototype is verified.
- [ ] Implement the secure paired-device protocol and theme synchronization.
- [ ] Implement remote features in permission-gated slices.
- [ ] Configure signing secrets, publish a signed APK, and verify upgrade installation.
