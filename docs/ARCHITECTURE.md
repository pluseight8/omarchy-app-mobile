# Architecture

OmaApp has two intended installable components: one Android APK and one Omarchy plugin. Shared C++/QML files are source modules, not additional products.

## Current prototype

```text
Android Qt Quick host
  ├── QML launcher shell and local appearance preview
  ├── C++ LauncherModel
  ├── Java LauncherBridge ── Android PackageManager / app launch
  └── Qt ShaderEffect ── Hyprland-derived rounded surfaces

Omarchy plugin
  └── Manifest-based Quickshell bar widget ── unpaired status
```

The Android host is Qt Quick, not a port of the Quickshell executable. Quickshell's inspected build depends on Linux/Wayland services and private Qt interfaces; no Quickshell code is bundled or claimed in this prototype. The plugin uses the Omarchy plugin entry point and existing shell UI types.

## Not implemented

There is no pairing transport, desktop listener, key storage, theme synchronization, file/clipboard/notification transfer, terminal, remote desktop, plugin manager, or command authorization path. See [pairing status](PAIRING.md), [compatibility notes](compatibility.md), and [the threat model](threat-model.md).
