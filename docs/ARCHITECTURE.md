# Architecture

OmaApp has two installable components: one Android APK and one Omarchy plugin. Shared C++/QML files and the Python helper inside the plugin are source modules, not additional products.

```text
Android APK
  ├── Qt Quick launcher, dock, app search, folders, devices, appearance, settings
  ├── C++ LauncherModel (app list, pins, folders) / PairingModel
  ├── Java LauncherBridge ── Android PackageManager / app launch
  ├── Java QR scanner ── CameraX + bundled ML Kit QR model
  ├── Java PairingBridge ── TLS certificate pin / Android Keystore token
  ├── Qt Quick scene-graph surface item ── QSB fragment shader (Lp contour, antialias, OkLabA border)
  └── Kotlin OWL bridge ── embedded compiler/runtime ── bounded offline QML Canvas renderer

Omarchy plugin
  ├── Quickshell bar widget ── QR, device approval, revocation, independent permissions
  └── bundled Python helper ── TLS pairing, event stream, appearance and opt-in PC features
```

The plugin helper stays inside the plugin folder and follows the widget lifecycle. It opens no listener until the user asks to pair or a saved trusted phone needs reconnection. The Android client pins the computer certificate from the QR and authenticates each device with a separate random token. The first pairing is approved on both devices; an already trusted phone can scan a new QR to update its local network address.

The theme path reads Omarchy's active `colors.toml`, `shell.toml`, and background symlink, plus Hyprland options and animations through `hyprctl`. Linux inotify watches theme files, and Hyprland's socket2 event stream reports configuration and workspace changes. The Android foreground client waits on the authenticated, bounded event endpoint and keeps a 30-second fallback refresh for events that cannot be observed. Theme and wallpaper snapshots stay in private app storage for offline use.

Optional features each have a device-scoped grant, disabled by default: two-way text clipboard, receive/send files, MPRIS controls, system metrics, Hyprland workspace switching, and explicitly configured user commands. Clipboard transfer is user-initiated from the foreground app. Files are limited to 100 MiB and use Android's system document picker; the helper confines PC downloads to `~/Downloads/OmaApp`. MPRIS actions are allowlisted; workspace IDs are validated; configured commands use an executable and argument vector with a timeout and never pass through a shell.

The Android app is Qt Quick, not a port of the Quickshell executable. Quickshell's UI modules depend on Linux/Wayland APIs and the plugin runs inside the existing shell process. The phone adapts compatible `Color`, `Style`, `Border` tokens and surface behavior to Qt Quick. `HyprlandSurface` is one custom scene-graph material whose QSB fragment shader evaluates the Lp superellipse per pixel, clips fill and border with one coverage function, and interpolates up to ten OkLabA border stops at any configured angle. It avoids tessellation and per-triangle seams; antialiasing scales to the device pixel ratio. Qt's RHI translates the same QSB shaders to OpenGL ES and Vulkan. No Quickshell binary/library is bundled in the APK.

Launcher favorites and folders are stored locally through `QSettings`. Folder membership references Android launcher activities by package and activity name, so an unavailable app stays out of the folder view until it is installed again. Folder edits are bounded, validated, and independent of the Omarchy pairing connection.

## Verified and remaining work

The active [PR #14](https://github.com/pluseight8/omarchy-app-mobile/pull/14) is the release gate; check its latest workflow run for the current source revision. CI builds the desktop and arm64 APK and runs launcher, paired-PC feature, and renderer checks in Android x86_64 emulators on OpenGL and Vulkan. The local Python service suite passes 48 tests. Linux integration tests start the real helper over TLS, exercise persistence and revocation, and change a watched theme file to verify event delivery. The plugin was installed and restarted inside the local Omarchy 4.0.4 / Quickshell 0.3.1 session. Physical Pixel/GrapheneOS validation remains outstanding.

The port includes up to five Home pages, an editable dock, cached Android launcher icons, app search, persistent folders, dynamic Android shortcuts, adjustable grid, four independent gesture preferences, app drag-and-drop, local clock and optional computer/media cards. A Kotlin bridge includes the original OWL compiler/runtime, local widget store/editor, Canvas renderer, widget reordering and a combined layout/widget backup. The offline interpreter supports common layout/text nodes and bounded Canvas operations; it rejects guest/network/command/asset/interactive facilities that cannot run locally. Android apps, widget sources and saved layout remain available offline. There is no PRoot or Arch Linux ARM.
