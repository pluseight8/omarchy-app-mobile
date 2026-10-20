# Architecture

OmaApp has two installable components: one Android APK and one Omarchy plugin. Shared C++/QML files and the Python helper inside the plugin are source modules, not additional products.

```text
Android APK
  ├── Qt Quick launcher, devices, and appearance screens
  ├── C++ LauncherModel (app list, pins, folders) / PairingModel
  ├── Java LauncherBridge ── Android PackageManager / app launch
  ├── Java QR scanner ── CameraX + bundled ML Kit QR model
  ├── Java PairingBridge ── TLS certificate pin / Android Keystore token
  └── Qt ShaderEffect ── Hyprland-derived rounded surfaces and border gradient

Omarchy plugin
  ├── Quickshell bar widget ── QR, device approval, revoke, status
  └── bundled Python helper ── TLS pairing / authenticated theme + wallpaper reads
```

The plugin helper stays inside the plugin folder and follows the widget lifecycle. It opens no listener until the user asks to pair or a saved trusted phone needs reconnection. The Android client pins the computer certificate from the QR and authenticates each device with a separate random token. The first pairing is approved on both devices; an already trusted phone can scan a new QR to update its local network address.

The theme path reads Omarchy's active `colors.toml`, `shell.toml`, and background symlink, plus Hyprland options through `hyprctl -j getoption`. OmaApp polls every four seconds and caches the latest successful snapshot and wallpaper privately on the device.

The Android app is Qt Quick, not a port of the Quickshell executable. Quickshell and Omarchy shell components have Linux/Wayland integration; the plugin runs inside Quickshell and the Android app reuses compatible visual behavior. No Quickshell binary/library is bundled in the APK.

Launcher favorites and folders are stored locally through `QSettings`. Folder membership references Android launcher activities by package and activity name, so an unavailable app stays out of the folder view until it is installed again. Folder edits are bounded, validated, and independent of the Omarchy pairing connection.

## Not yet verified or implemented

The source and protocol tests do not establish that pairing works on a real Omarchy computer and Android device. This workspace has `adb` and Qt desktop tools but no CMake, JDK, Android SDK, emulator, or attached phone; GrapheneOS has not been tested. GitHub Actions provides the APK build and emulator smoke test.

There is no remote command, clipboard, file, notification, media, workspace, terminal, desktop-streaming, or plugin-management endpoint. These features need their own permission model, tests, and threat review before implementation.
