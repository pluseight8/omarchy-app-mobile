# Architecture

OmaApp has two installable components: one Android APK and one Omarchy plugin. Shared C++/QML files and the Python helper inside the plugin are source modules, not additional products.

```text
Android APK
  ├── Qt Quick launcher, dock, app search, folders, devices, appearance, settings
  ├── C++ LauncherModel (app list, pins, folders) / PairingModel
  ├── Java LauncherBridge ── Android PackageManager / app launch
  ├── Java QR scanner ── CameraX + bundled ML Kit QR model
  ├── Java PairingBridge ── TLS certificate pin / Android Keystore token
  └── Qt ShaderEffect ── Hyprland-derived rounded surfaces and border gradient

Omarchy plugin
  ├── Quickshell bar widget ── QR, device approval, revocation, independent permissions
  └── bundled Python helper ── TLS pairing, event stream, appearance and opt-in PC features
```

The plugin helper stays inside the plugin folder and follows the widget lifecycle. It opens no listener until the user asks to pair or a saved trusted phone needs reconnection. The Android client pins the computer certificate from the QR and authenticates each device with a separate random token. The first pairing is approved on both devices; an already trusted phone can scan a new QR to update its local network address.

The theme path reads Omarchy's active `colors.toml`, `shell.toml`, and background symlink, plus Hyprland options and animations through `hyprctl`. Linux inotify watches theme files, and Hyprland's socket2 event stream reports configuration and workspace changes. The Android foreground client waits on the authenticated, bounded event endpoint and keeps a 30-second fallback refresh for events that cannot be observed. Theme and wallpaper snapshots stay in private app storage for offline use.

Optional features each have a device-scoped grant, disabled by default: two-way text clipboard, receive/send files, MPRIS controls, system metrics, Hyprland workspace switching, and explicitly configured user commands. Clipboard transfer is user-initiated from the foreground app. Files are limited to 100 MiB and use Android's system document picker; the helper confines PC downloads to `~/Downloads/OmaApp`. MPRIS actions are allowlisted; workspace IDs are validated; configured commands use an executable and argument vector with a timeout and never pass through a shell.

The Android app is Qt Quick, not a port of the Quickshell executable. Quickshell and Omarchy shell components have Linux/Wayland integration; the plugin runs inside Quickshell and the Android app reuses compatible visual behavior. No Quickshell binary/library is bundled in the APK.

Launcher favorites and folders are stored locally through `QSettings`. Folder membership references Android launcher activities by package and activity name, so an unavailable app stays out of the folder view until it is installed again. Folder edits are bounded, validated, and independent of the Omarchy pairing connection.

## Verified and remaining work

[CI run 39610588557](https://github.com/pluseight8/omarchy-app-mobile/actions/runs/39610588557) passed the prior arm64 APK build and x86_64 emulator smoke against a fixture. Linux integration tests now start the real helper over TLS, exercise persistence and revocation, and change a watched theme file to verify event delivery. The plugin was also installed and restarted inside the local Omarchy 4.0.4 / Quickshell 0.3.1 session; its bar widget remained registered and the helper restarted correctly. A physical Pixel/GrapheneOS connection still needs owner-device testing.

The port includes a local app grid, app search, cached Android launcher icons, pin-to-dock, folders, grid sizing, and swipe navigation between Apps, Devices, Style, and Settings. It does not yet port Omalauncher's multi-page widget canvas, OWL interpreter/widget store, dynamic app shortcuts, or its Linux guest. There is no PRoot or Arch Linux ARM. Android apps and locally stored launcher data work without an Omarchy computer.
